package io.github.hantsy.jdbc.sqlinit;

import io.github.hantsy.jdbc.sqlinit.resource.Resource;
import io.github.hantsy.jdbc.sqlinit.resource.ResourceResolver;
import io.github.hantsy.jdbc.sqlinit.resource.ResourceResolverRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * Applies versioned SQL migrations to a {@link DataSource}, tracking them in a history table.
 *
 * <p>Scripts are resolved from the configured {@link SqlInitConfig#scriptLocations()} and must be
 * named {@code V<version>__<description>.sql}. Each pending migration is claimed by inserting a
 * {@code running} row (atomic via the {@code version} primary key), executed in its own
 * transaction, then marked {@code succeeded} or {@code failed}.</p>
 */
public final class DbMigrator {

    private static final Logger LOGGER = Logger.getLogger(DbMigrator.class.getName());
    private static final Pattern MIGRATION_NAME = Pattern.compile("V(\\d+)__(.+)\\.sql");

    private final DataSource dataSource;
    private final SqlInitConfig config;
    private final ResourceResolver resourceResolver;

    public DbMigrator(DataSource dataSource) {
        this(dataSource, SqlInitConfig.defaults(), new ResourceResolverRegistry());
    }

    public DbMigrator(DataSource dataSource, SqlInitConfig config) {
        this(dataSource, config, new ResourceResolverRegistry());
    }

    public DbMigrator(DataSource dataSource, SqlInitConfig config, ResourceResolver resourceResolver) {
        this.dataSource = dataSource;
        this.config = config;
        this.resourceResolver = resourceResolver;
    }

    /**
     * Resolves the configured scripts and applies the migrations that have not run yet.
     *
     * @throws SQLException if a script cannot be located, read, parsed, or executed, or if the
     *                      history table holds a {@code failed} or leftover {@code running} row
     */
    public void migrate() throws SQLException {
        List<Migration> migrations = resolve();
        if (migrations.isEmpty()) {
            LOGGER.info("No SQL migrations resolved, skipping database migration");
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            MigrationHistory history = new MigrationHistory(connection, dbType(connection));
            history.ensureTable();

            Map<Integer, MigrationHistory.Status> applied = history.applied();
            assertNoBrokenHistory(applied);

            int appliedCount = 0;
            for (Migration migration : migrations) {
                if (applied.containsKey(migration.version())) {
                    LOGGER.fine(() -> "Skipping already applied migration: " + migration.script());
                    continue;
                }
                runMigration(connection, history, migration);
                appliedCount++;
            }
            int count = appliedCount;
            LOGGER.info(() -> "Database migration completed, applied " + count + " migration(s)");
        }
    }

    private List<Migration> resolve() throws SQLException {
        ResourceResolver resolver = resourceResolver;
        Map<String, Resource> byFilename = new LinkedHashMap<>();
        for (String location : config.scriptLocations()) {
            List<Resource> found;
            try {
                found = resolver.getResources(location);
            } catch (IOException e) {
                throw new SQLException("Failed to resolve SQL script location: " + location, e);
            }
            for (Resource resource : found) {
                byFilename.putIfAbsent(resource.getFilename(), resource);
            }
        }
        List<Resource> resources = new ArrayList<>(byFilename.values());
        resources.sort(Comparator.comparing(Resource::getFilename));

        List<Migration> migrations = new ArrayList<>();
        for (Resource resource : resources) {
            Matcher matcher = MIGRATION_NAME.matcher(resource.getFilename());
            if (!matcher.matches()) {
                LOGGER.warning(() -> "Ignoring resource that does not match V<version>__<description>.sql: "
                        + resource.getFilename());
                continue;
            }
            int version = Integer.parseInt(matcher.group(1));
            String description = matcher.group(2);
            migrations.add(new Migration(version, description, resource));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        for (int i = 1; i < migrations.size(); i++) {
            if (migrations.get(i).version() == migrations.get(i - 1).version()) {
                throw new SQLException("Duplicate migration version " + migrations.get(i).version()
                        + ": " + migrations.get(i - 1).script() + " and " + migrations.get(i).script());
            }
        }
        return migrations;
    }

    private DbType dbType(Connection connection) throws SQLException {
        if (config.dbType() != null) {
            try {
                return DbType.fromName(config.dbType());
            } catch (IllegalArgumentException e) {
                throw new SQLException(e.getMessage(), e);
            }
        }
        return DbType.detect(connection.getMetaData().getDatabaseProductName(),
                connection.getMetaData().getURL());
    }

    private void assertNoBrokenHistory(Map<Integer, MigrationHistory.Status> applied) throws SQLException {
        for (Map.Entry<Integer, MigrationHistory.Status> entry : applied.entrySet()) {
            if (MigrationHistory.Status.SUCCEEDED != entry.getValue()) {
                throw new SQLException("Migration V" + entry.getKey() + " is in state '" + entry.getValue().value()
                        + "'; fix or remove the " + MigrationHistory.TABLE_NAME + " row before restarting");
            }
        }
    }

    private void runMigration(Connection connection, MigrationHistory history, Migration migration)
            throws SQLException {
        LOGGER.info(() -> "Applying migration: " + migration.script());

        connection.setAutoCommit(true);
        history.insertRunning(migration);

        connection.setAutoCommit(false);
        SQLException failure = null;
        try {
            executeScript(connection, migration);
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly(connection, e);
            failure = e;
        }

        connection.setAutoCommit(true);

        if (failure != null) {
            try {
                history.markFailed(migration.version(), truncate(failure.getMessage()));
            } catch (SQLException markFailure) {
                failure.addSuppressed(markFailure);
            }
            throw failure;
        }
        history.markSucceeded(migration.version());
    }

    private void executeScript(Connection connection, Migration migration) throws SQLException {
        List<String> statements;
        try (InputStream in = migration.resource().getInputStream();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            statements = SqlScriptParser.parse(reader, config.separator());
        } catch (IOException e) {
            throw new SQLException("Failed to read SQL script: " + migration.script(), e);
        }
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new SQLException("Failed to execute SQL script: " + migration.script(),
                    e.getSQLState(), e.getErrorCode(), e);
        }
    }

    private void rollbackQuietly(Connection connection, SQLException failure) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            failure.addSuppressed(e);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
