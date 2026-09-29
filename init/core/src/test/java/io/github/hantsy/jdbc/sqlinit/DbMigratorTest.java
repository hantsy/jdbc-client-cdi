package io.github.hantsy.jdbc.sqlinit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbMigratorTest {

    @Test
    void appliesVersionedMigrationsInOrder() throws SQLException {
        DataSource dataSource = h2("order");

        new DbMigrator(dataSource).migrate();

        assertEquals(List.of("V2", "V3"), query(dataSource, "SELECT script FROM applied_scripts ORDER BY seq"));
        assertEquals(List.of("1", "2", "3"),
                query(dataSource, "SELECT version FROM db_migrations ORDER BY version"));
    }

    @Test
    void skipsAlreadyAppliedMigrations() throws SQLException {
        DataSource dataSource = h2("skip");
        DbMigrator migrator = new DbMigrator(dataSource);

        migrator.migrate();
        migrator.migrate();

        assertEquals(List.of("1", "2", "3"),
                query(dataSource, "SELECT version FROM db_migrations ORDER BY version"));
        assertEquals(List.of("V2", "V3"), query(dataSource, "SELECT script FROM applied_scripts ORDER BY seq"));
    }

    @Test
    void recordsAFailureAndRollsBackTheScript() throws SQLException {
        DataSource dataSource = h2("failed");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ok_table (id INT)");
        }
        SqlInitConfig config = SqlInitConfig.builder()
                .scriptLocations(List.of("classpath:db/failing-migration"))
                .build();

        assertThrows(SQLException.class, () -> new DbMigrator(dataSource, config).migrate());

        assertEquals(List.of("failed"), query(dataSource, "SELECT status FROM db_migrations WHERE version = 1"));
        assertEquals(List.of("0"), query(dataSource, "SELECT CAST(COUNT(*) AS VARCHAR) FROM ok_table"));
    }

    @Test
    void rerunsAFailedMigration() throws SQLException {
        DataSource dataSource = h2("retry-failed");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE db_migrations (version INT PRIMARY KEY, description VARCHAR(200), "
                    + "script VARCHAR(500), status VARCHAR(16), installed_on TIMESTAMP, error_message VARCHAR(1000))");
            statement.execute("INSERT INTO db_migrations (version, description, script, status, installed_on, error_message) "
                    + "VALUES (1, 'create', 'V1__create.sql', 'failed', CURRENT_TIMESTAMP, 'boom')");
        }

        new DbMigrator(dataSource).migrate();

        assertEquals(List.of("succeeded"), query(dataSource, "SELECT status FROM db_migrations WHERE version = 1"));
        assertEquals(List.of("V2", "V3"), query(dataSource, "SELECT script FROM applied_scripts ORDER BY seq"));
    }

    @Test
    void rerunsALeftoverRunningMigration() throws SQLException {
        DataSource dataSource = h2("retry-running");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE db_migrations (version INT PRIMARY KEY, description VARCHAR(200), "
                    + "script VARCHAR(500), status VARCHAR(16), installed_on TIMESTAMP, error_message VARCHAR(1000))");
            statement.execute("INSERT INTO db_migrations (version, description, script, status, installed_on) "
                    + "VALUES (1, 'create', 'V1__create.sql', 'running', CURRENT_TIMESTAMP)");
        }

        new DbMigrator(dataSource).migrate();

        assertEquals(List.of("succeeded"), query(dataSource, "SELECT status FROM db_migrations WHERE version = 1"));
        assertEquals(List.of("V2", "V3"), query(dataSource, "SELECT script FROM applied_scripts ORDER BY seq"));
    }

    @Test
    void failsWhenAMigrationVersionIsMissing() {
        DataSource dataSource = h2("gap");
        SqlInitConfig config = SqlInitConfig.builder()
                .scriptLocations(List.of("classpath:db/gap"))
                .build();

        SQLException e = assertThrows(SQLException.class, () -> new DbMigrator(dataSource, config).migrate());

        assertTrue(e.getMessage().contains("Expected migration"));
    }

    @Test
    void rejectsDuplicateMigrationVersions() {
        DataSource dataSource = h2("duplicate");
        SqlInitConfig config = SqlInitConfig.builder()
                .scriptLocations(List.of("classpath:db/duplicates"))
                .build();

        SQLException e = assertThrows(SQLException.class, () -> new DbMigrator(dataSource, config).migrate());

        assertTrue(e.getMessage().contains("Duplicate migration version"));
    }

    private static DataSource h2(String database) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static List<String> query(DataSource dataSource, String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows.add(resultSet.getString(1));
            }
        }
        return rows;
    }
}
