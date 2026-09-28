package io.github.hantsy.jdbc.sqlinit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbTypeTest {

    @Test
    void resolvesAliases() {
        assertEquals(DbType.H2, DbType.fromName("h2"));
        assertEquals(DbType.POSTGRESQL, DbType.fromName("pg"));
        assertEquals(DbType.POSTGRESQL, DbType.fromName("postgres"));
        assertEquals(DbType.POSTGRESQL, DbType.fromName("POSTGRESQL"));
        assertEquals(DbType.MYSQL, DbType.fromName("mysql"));
        assertEquals(DbType.MYSQL, DbType.fromName("mariadb"));
        assertEquals(DbType.MSSQL, DbType.fromName("mssql"));
        assertEquals(DbType.MSSQL, DbType.fromName("sqlserver"));
        assertEquals(DbType.ORACLE, DbType.fromName("oracle"));
    }

    @Test
    void rejectsAnUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> DbType.fromName("nope"));
    }

    @Test
    void detectsByProductName() throws SQLException {
        assertEquals(DbType.H2, DbType.detect("H2", null));
        assertEquals(DbType.POSTGRESQL, DbType.detect("PostgreSQL 15", null));
        assertEquals(DbType.MYSQL, DbType.detect("MariaDB", null));
        assertEquals(DbType.MSSQL, DbType.detect("Microsoft SQL Server", null));
        assertEquals(DbType.ORACLE, DbType.detect("Oracle Database", null));
    }

    @Test
    void detectsByJdbcUrl() throws SQLException {
        assertEquals(DbType.H2, DbType.detect("Something", "jdbc:h2:mem:test"));
        assertEquals(DbType.POSTGRESQL, DbType.detect("Something", "jdbc:postgresql://localhost/db"));
        assertEquals(DbType.MYSQL, DbType.detect("Something", "jdbc:mariadb://localhost/db"));
        assertEquals(DbType.MSSQL, DbType.detect("Something", "jdbc:sqlserver://localhost"));
        assertEquals(DbType.ORACLE, DbType.detect("Something", "jdbc:oracle:thin:@localhost"));
    }

    @Test
    void failsWhenTypeIsUnknown() {
        assertThrows(SQLException.class, () -> DbType.detect("SomeDB", "jdbc:unknowndb:x"));
    }

    @Test
    void everyTypeHasALoadableInitSqlResource() throws IOException {
        for (DbType type : DbType.values()) {
            try (InputStream in = DbType.class.getClassLoader().getResourceAsStream(type.initSqlResource())) {
                assertNotNull(in, type.name());
                String ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(ddl.contains("CREATE TABLE db_migrations"), type.name());
            }
        }
    }
}
