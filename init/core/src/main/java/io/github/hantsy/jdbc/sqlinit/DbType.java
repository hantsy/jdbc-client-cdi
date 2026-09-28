package io.github.hantsy.jdbc.sqlinit;

import java.sql.SQLException;
import java.util.Locale;

/**
 * The database the {@code db_migrations} history table is created for.
 *
 * <p>Each member points at the classpath resource holding its dialect-specific
 * {@code CREATE TABLE db_migrations} statement.</p>
 */
public enum DbType {

    H2("sqlinit/init-h2.sql"),
    POSTGRESQL("sqlinit/init-postgresql.sql"),
    MYSQL("sqlinit/init-mysql.sql"),
    MSSQL("sqlinit/init-mssql.sql"),
    ORACLE("sqlinit/init-oracle.sql");

    private final String initSqlResource;

    DbType(String initSqlResource) {
        this.initSqlResource = initSqlResource;
    }

    /** The classpath resource holding this database's history-table initialization statement. */
    public String initSqlResource() {
        return initSqlResource;
    }

    /**
     * Resolves a configured database type name, accepting common aliases (case-insensitive).
     *
     * @throws IllegalArgumentException if the name is not a known database type
     */
    public static DbType fromName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "h2" -> H2;
            case "pg", "postgres", "postgresql" -> POSTGRESQL;
            case "mysql", "mariadb" -> MYSQL;
            case "mssql", "sqlserver", "sql_server" -> MSSQL;
            case "oracle" -> ORACLE;
            default -> throw new IllegalArgumentException("Unknown database type: " + name);
        };
    }

    /**
     * Detects the database type from the JDBC product name and, failing that, the JDBC URL.
     *
     * @throws SQLException if the type cannot be determined
     */
    public static DbType detect(String productName, String jdbcUrl) throws SQLException {
        DbType detected = detectByProductName(productName);
        if (detected == null && jdbcUrl != null) {
            detected = detectByJdbcUrl(jdbcUrl);
        }
        if (detected == null) {
            throw new SQLException("Cannot detect the database type from product name '"
                    + productName + "' and JDBC URL '" + jdbcUrl
                    + "'; set jdbcclient.init.db-type explicitly");
        }
        return detected;
    }

    private static DbType detectByProductName(String productName) {
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

    private static DbType detectByJdbcUrl(String jdbcUrl) {
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
