package io.github.hantsy.jdbc.sqlinit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptPopulatorTest {

    @Test
    void appliesTheDefaultSchemaAndDataScripts() throws SQLException {
        DataSource dataSource = dataSource("defaults");

        new SqlScriptPopulator(dataSource).populate();

        assertEquals(List.of("Ada", "Grace"), query(dataSource, "SELECT name FROM engineers ORDER BY id"));
    }

    @Test
    void scansAndOrdersAWildcardLocationAlphabetically() throws SQLException {
        DataSource dataSource = dataSource("wildcard");
        SqlInitConfig config = new SqlInitConfig(";", List.of("classpath*:/db/migration/**/*.sql"), List.of());

        new SqlScriptPopulator(dataSource, config).populate();

        assertEquals(List.of("V2", "V3"), query(dataSource, "SELECT script FROM applied_scripts ORDER BY seq"));
    }

    @Test
    void scansScriptsInsideAJar(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("scripts.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            // Directory entries are what the jar tool and the Maven/Gradle jar plugins emit, and
            // scanning a location inside an archive relies on them.
            writeDirectory(out, "db/");
            writeDirectory(out, "db/jar/");
            writeEntry(out, "db/jar/V2__second.sql", "INSERT INTO jar_scripts (script) VALUES ('V2');");
            writeEntry(out, "db/jar/V1__create.sql", "CREATE TABLE jar_scripts (script VARCHAR(50));");
            writeEntry(out, "db/jar/notes.txt", "ignored");
        }

        DataSource dataSource = dataSource("jar");
        SqlInitConfig config = new SqlInitConfig(";", List.of("classpath*:/db/jar/**/*.sql"), List.of());
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, parent)) {
            Thread.currentThread().setContextClassLoader(loader);
            try {
                new SqlScriptPopulator(dataSource, config).populate();
            } finally {
                Thread.currentThread().setContextClassLoader(parent);
            }
        }

        assertEquals(List.of("V2"), query(dataSource, "SELECT script FROM jar_scripts"));
    }

    @Test
    void appliesACustomSeparator() throws SQLException {
        DataSource dataSource = dataSource("separator");
        SqlInitConfig config = new SqlInitConfig("/", List.of("classpath:/db/slash/scripts.sql"), List.of());

        new SqlScriptPopulator(dataSource, config).populate();

        assertEquals(List.of("a", "b"), query(dataSource, "SELECT name FROM slash_scripts ORDER BY name"));
    }

    @Test
    void rollsBackEveryScriptWhenOneStatementFails() throws Exception {
        DataSource dataSource = dataSource("rollback");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE applied (name VARCHAR(50))");
        }
        SqlInitConfig config = new SqlInitConfig(";", List.of("/db/applied-first.sql"),
                List.of("classpath:/db/failing.sql"));

        SQLException e = assertThrows(SQLException.class, () -> new SqlScriptPopulator(dataSource, config).populate());

        assertTrue(e.getMessage().contains("Failed to execute SQL script"));
        assertEquals(List.of("0"), query(dataSource, "SELECT CAST(COUNT(*) AS VARCHAR) FROM applied"));
    }

    @Test
    void failsWhenALiteralLocationIsMissing() {
        DataSource dataSource = dataSource("missing");
        SqlInitConfig config = new SqlInitConfig(";", List.of("/does-not-exist.sql"), List.of());

        SQLException e = assertThrows(SQLException.class, () -> new SqlScriptPopulator(dataSource, config).populate());

        assertTrue(e.getMessage().contains("not found"));
    }

    @Test
    void skipsAPatternThatMatchesNothing() throws SQLException {
        DataSource dataSource = dataSource("empty-pattern");
        SqlInitConfig config = new SqlInitConfig(";", List.of("/schema.sql"), List.of("classpath*:/nothing/**/*.sql"));

        new SqlScriptPopulator(dataSource, config).populate();

        assertEquals(List.of("0"), query(dataSource, "SELECT CAST(COUNT(*) AS VARCHAR) FROM engineers"));
    }

    @Test
    void skipsQuietlyWhenTheDefaultScriptsAreAbsent() throws Exception {
        DataSource dataSource = dataSource("no-defaults");
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[0], null)) {
            Thread.currentThread().setContextClassLoader(loader);
            try {
                new SqlScriptPopulator(dataSource).populate();
            } finally {
                Thread.currentThread().setContextClassLoader(parent);
            }
        }

        assertEquals(List.of("0"), query(dataSource,
                "SELECT CAST(COUNT(*) AS VARCHAR) FROM information_schema.tables WHERE table_name = 'ENGINEERS'"));
    }

    private static DataSource dataSource(String database) {
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

    private static void writeDirectory(JarOutputStream out, String name) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.closeEntry();
    }

    private static void writeEntry(JarOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
