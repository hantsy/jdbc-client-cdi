package io.github.hantsy.jdbc.sqlinit;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabasePlatformTest {

    @Test
    void resolvesAliases() {
        assertEquals(DatabasePlatform.H2, DatabasePlatform.fromName("h2"));
        assertEquals(DatabasePlatform.POSTGRESQL, DatabasePlatform.fromName("pg"));
        assertEquals(DatabasePlatform.POSTGRESQL, DatabasePlatform.fromName("postgres"));
        assertEquals(DatabasePlatform.POSTGRESQL, DatabasePlatform.fromName("POSTGRESQL"));
        assertEquals(DatabasePlatform.MYSQL, DatabasePlatform.fromName("mysql"));
        assertEquals(DatabasePlatform.MYSQL, DatabasePlatform.fromName("mariadb"));
        assertEquals(DatabasePlatform.MSSQL, DatabasePlatform.fromName("mssql"));
        assertEquals(DatabasePlatform.MSSQL, DatabasePlatform.fromName("sqlserver"));
        assertEquals(DatabasePlatform.ORACLE, DatabasePlatform.fromName("oracle"));
    }

    @Test
    void rejectsAnUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> DatabasePlatform.fromName("nope"));
    }

    @Test
    void detectsByProductName() throws SQLException {
        assertEquals(DatabasePlatform.H2, DatabasePlatform.detect("H2", null));
        assertEquals(DatabasePlatform.POSTGRESQL, DatabasePlatform.detect("PostgreSQL 15", null));
        assertEquals(DatabasePlatform.MYSQL, DatabasePlatform.detect("MariaDB", null));
        assertEquals(DatabasePlatform.MSSQL, DatabasePlatform.detect("Microsoft SQL Server", null));
        assertEquals(DatabasePlatform.ORACLE, DatabasePlatform.detect("Oracle Database", null));
    }

    @Test
    void detectsByJdbcUrl() throws SQLException {
        assertEquals(DatabasePlatform.H2, DatabasePlatform.detect("Something", "jdbc:h2:mem:test"));
        assertEquals(DatabasePlatform.POSTGRESQL, DatabasePlatform.detect("Something", "jdbc:postgresql://localhost/db"));
        assertEquals(DatabasePlatform.MYSQL, DatabasePlatform.detect("Something", "jdbc:mariadb://localhost/db"));
        assertEquals(DatabasePlatform.MSSQL, DatabasePlatform.detect("Something", "jdbc:sqlserver://localhost"));
        assertEquals(DatabasePlatform.ORACLE, DatabasePlatform.detect("Something", "jdbc:oracle:thin:@localhost"));
    }

    @Test
    void failsWhenPlatformIsUnknown() {
        assertThrows(SQLException.class, () -> DatabasePlatform.detect("SomeDB", "jdbc:unknowndb:x"));
    }

    @Test
    void emitsDialectSpecificDdl() {
        assertTrue(DatabasePlatform.ORACLE.createHistoryTable("mig").contains("VARCHAR2"));
        assertTrue(DatabasePlatform.MYSQL.createHistoryTable("mig").contains("DATETIME"));
        assertTrue(DatabasePlatform.MSSQL.createHistoryTable("mig").contains("NVARCHAR"));
        assertTrue(DatabasePlatform.H2.createHistoryTable("mig").contains("version INT PRIMARY KEY"));
        assertTrue(DatabasePlatform.H2.createHistoryTable("my_table").contains("my_table"));
    }
}
