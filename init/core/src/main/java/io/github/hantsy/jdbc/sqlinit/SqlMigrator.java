package io.github.hantsy.jdbc.sqlinit;

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
public final class SqlMigrator {

    private static final Logger LOGGER = Logger.getLogger(SqlMigrator.class.getName());
    private static final Pattern MIGRATION_NAME = Pattern.compile("V(\\d+)__(.+)\\.sql");

    private final DataSource dataSource;
    private final SqlInitConfig config;

    public SqlMigrator(DataSource dataSource) {
        this(dataSource, SqlInitConfig.defaults());
    }

    public SqlMigrator(DataSource dataSource, SqlInitConfig config) {
        this.dataSource = dataSource;
        this.config = config;
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
            MigrationHistory history = new MigrationHistory(connection, platform(connection), config.historyTable());
            history.ensureTable();

            Map<Integer, String> applied = history.applied();
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
        List<Resource> resources = new ScriptLocator().resolve(config.scriptLocations());
        List<Migration> migrations = new ArrayList<>();
        for (Resource resource : resources) {
            Matcher matcher = MIGRATION_NAME.matcher(resource.fileName());
            if (!matcher.matches()) {
                LOGGER.warning(() -> "Ignoring SQL script that does not match V<version>__<description>.sql: "
                        + resource.fileName());
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

    private DatabasePlatform platform(Connection connection) throws SQLException {
        if (config.platform() != null) {
            try {
                return DatabasePlatform.fromName(config.platform());
            } catch (IllegalArgumentException e) {
                throw new SQLException(e.getMessage(), e);
            }
        }
        return DatabasePlatform.detect(connection.getMetaData().getDatabaseProductName(),
                connection.getMetaData().getURL());
    }

    private void assertNoBrokenHistory(Map<Integer, String> applied) throws SQLException {
        for (Map.Entry<Integer, String> entry : applied.entrySet()) {
            if (!MigrationHistory.STATUS_SUCCEEDED.equals(entry.getValue())) {
                throw new SQLException("Migration V" + entry.getKey() + " is in state '" + entry.getValue()
                        + "'; fix or remove the " + config.historyTable() + " row before restarting");
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
        try (InputStream in = migration.resource().open();
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
