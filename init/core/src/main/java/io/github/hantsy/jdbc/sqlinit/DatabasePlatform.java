package io.github.hantsy.jdbc.sqlinit;

import java.sql.SQLException;
import java.util.Locale;

/**
 * The database the migration history table is created for.
 *
 * <p>Each member carries the dialect-specific {@code CREATE TABLE} statement for the history table;
 * the only differences across platforms are the type spellings ({@code VARCHAR2} on Oracle,
 * {@code NVARCHAR}/{@code DATETIME2} on SQL Server, {@code DATETIME} on MySQL/MariaDB).</p>
 */
public enum DatabasePlatform {

    H2("CREATE TABLE %s (version INT PRIMARY KEY, description VARCHAR(200) NOT NULL, "
            + "script VARCHAR(500) NOT NULL, status VARCHAR(16) NOT NULL, "
            + "installed_on TIMESTAMP NOT NULL, error_message VARCHAR(1000))"),

    POSTGRESQL("CREATE TABLE %s (version INT PRIMARY KEY, description VARCHAR(200) NOT NULL, "
            + "script VARCHAR(500) NOT NULL, status VARCHAR(16) NOT NULL, "
            + "installed_on TIMESTAMP NOT NULL, error_message VARCHAR(1000))"),

    MYSQL("CREATE TABLE %s (version INT PRIMARY KEY, description VARCHAR(200) NOT NULL, "
            + "script VARCHAR(500) NOT NULL, status VARCHAR(16) NOT NULL, "
            + "installed_on DATETIME NOT NULL, error_message VARCHAR(1000))"),

    MSSQL("CREATE TABLE %s (version INT PRIMARY KEY, description NVARCHAR(200) NOT NULL, "
            + "script NVARCHAR(500) NOT NULL, status NVARCHAR(16) NOT NULL, "
            + "installed_on DATETIME2 NOT NULL, error_message NVARCHAR(1000))"),

    ORACLE("CREATE TABLE %s (version NUMBER(10) PRIMARY KEY, description VARCHAR2(200) NOT NULL, "
            + "script VARCHAR2(500) NOT NULL, status VARCHAR2(16) NOT NULL, "
            + "installed_on TIMESTAMP NOT NULL, error_message VARCHAR2(1000))");

    private final String createTable;

    DatabasePlatform(String createTable) {
        this.createTable = createTable;
    }

    /**
     * Returns the {@code CREATE TABLE} statement for the history table of the given name.
     */
    public String createHistoryTable(String tableName) {
        return String.format(createTable, tableName);
    }

    /**
     * Resolves a configured platform name, accepting common aliases (case-insensitive).
     *
     * @throws IllegalArgumentException if the name is not a known platform
     */
    public static DatabasePlatform fromName(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "h2":
                return H2;
            case "pg":
            case "postgres":
            case "postgresql":
                return POSTGRESQL;
            case "mysql":
            case "mariadb":
                return MYSQL;
            case "mssql":
            case "sqlserver":
            case "sql_server":
                return MSSQL;
            case "oracle":
                return ORACLE;
            default:
                throw new IllegalArgumentException("Unknown database platform: " + name);
        }
    }

    /**
     * Detects the platform from the JDBC product name and, failing that, the JDBC URL.
     *
     * @throws SQLException if the platform cannot be determined
     */
    public static DatabasePlatform detect(String productName, String jdbcUrl) throws SQLException {
        DatabasePlatform detected = detectByProductName(productName);
        if (detected == null && jdbcUrl != null) {
            detected = detectByJdbcUrl(jdbcUrl);
        }
        if (detected == null) {
            throw new SQLException("Cannot detect the database platform from product name '"
                    + productName + "' and JDBC URL '" + jdbcUrl
                    + "'; set jdbcclient.init.platform explicitly");
        }
        return detected;
    }

    private static DatabasePlatform detectByProductName(String productName) {
        if (productName == null) {
            return null;
        }
        String name = productName.toLowerCase(Locale.ROOT);
        if (name.contains("h2")) {
            return H2;
        }
        if (name.contains("postgres")) {
            return POSTGRESQL;
        }
        if (name.contains("mysql") || name.contains("mariadb")) {
            return MYSQL;
        }
        if (name.contains("microsoft") || name.contains("sql server")) {
            return MSSQL;
        }
        if (name.contains("oracle")) {
            return ORACLE;
        }
        return null;
    }

    private static DatabasePlatform detectByJdbcUrl(String jdbcUrl) {
        String url = jdbcUrl.toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:h2:")) {
            return H2;
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return POSTGRESQL;
        }
        if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
            return MYSQL;
        }
        if (url.startsWith("jdbc:sqlserver:")) {
            return MSSQL;
        }
        if (url.startsWith("jdbc:oracle:")) {
            return ORACLE;
        }
        return null;
    }
}
