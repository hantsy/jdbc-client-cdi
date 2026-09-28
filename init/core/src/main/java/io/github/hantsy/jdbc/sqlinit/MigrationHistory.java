package io.github.hantsy.jdbc.sqlinit;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The migration history table: its dialect-specific DDL and the bookkeeping statements that record
 * a migration's lifecycle ({@code running} -> {@code succeeded}/{@code failed}).
 *
 * <p>Callers are expected to run these statements on a connection in auto-commit mode, so that the
 * bookkeeping commits independently of the migration's own transaction.</p>
 */
final class MigrationHistory {

    static final String STATUS_RUNNING = "running";
    static final String STATUS_SUCCEEDED = "succeeded";
    static final String STATUS_FAILED = "failed";

    private final Connection connection;
    private final DatabasePlatform platform;
    private final String tableName;

    MigrationHistory(Connection connection, DatabasePlatform platform, String tableName) {
        this.connection = connection;
        this.platform = platform;
        this.tableName = tableName;
    }

    void ensureTable() throws SQLException {
        if (!tableExists()) {
            try (var statement = connection.createStatement()) {
                statement.execute(platform.createHistoryTable(tableName));
            }
        }
    }

    Map<Integer, String> applied() throws SQLException {
        Map<Integer, String> applied = new LinkedHashMap<>();
        try (var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT version, status FROM " + tableName + " ORDER BY version")) {
            while (rows.next()) {
                applied.put(rows.getInt(1), rows.getString(2));
            }
        }
        return applied;
    }

    void insertRunning(Migration migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + tableName
                        + " (version, description, script, status, installed_on) VALUES (?, ?, ?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.setString(3, migration.script());
            statement.setString(4, STATUS_RUNNING);
            statement.setTimestamp(5, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    void markSucceeded(int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + tableName + " SET status = ? WHERE version = ?")) {
            statement.setString(1, STATUS_SUCCEEDED);
            statement.setInt(2, version);
            statement.executeUpdate();
        }
    }

    void markFailed(int version, String errorMessage) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + tableName + " SET status = ?, error_message = ? WHERE version = ?")) {
            statement.setString(1, STATUS_FAILED);
            statement.setString(2, errorMessage);
            statement.setInt(3, version);
            statement.executeUpdate();
        }
    }

    private boolean tableExists() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
