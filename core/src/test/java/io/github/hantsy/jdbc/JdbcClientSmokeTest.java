package io.github.hantsy.jdbc;

import io.github.hantsy.jdbc.support.GeneratedKeyHolder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fast, container-free smoke test that constructs {@link JdbcClient} directly against H2.
 */
public class JdbcClientSmokeTest {

    private static JdbcClient jdbcClient;
    private static DataSource dataSource;

    @BeforeAll
    public static void createClient() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:smoke;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;

        jdbcClient = new JdbcClient(dataSource);
    }

    @BeforeEach
    public void setupDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS engineers");
            stmt.execute("CREATE TABLE engineers (id BIGINT PRIMARY KEY, dev_name VARCHAR(255))");
            stmt.execute("INSERT INTO engineers VALUES (1, 'Duke Jakarta')");
            stmt.execute("INSERT INTO engineers VALUES (2, 'Arquillian Glassfish')");
            stmt.execute("DROP TABLE IF EXISTS engineers_gen");
            stmt.execute("CREATE TABLE engineers_gen (id BIGINT AUTO_INCREMENT PRIMARY KEY, dev_name VARCHAR(255))");
        }
    }

    @AfterAll
    public static void shutdown() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
        }
    }

    @Test
    public void list() {
        List<DevSummary> all = jdbcClient.sql("SELECT id, dev_name FROM engineers ORDER BY id")
                .query(DevSummary.class).list();
        Assertions.assertEquals(2, all.size());
    }

    @Test
    public void single() {
        DevSummary one = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .param("id", 1L).query(DevSummary.class).single();
        Assertions.assertEquals("Duke Jakarta", one.devName());
    }

    @Test
    public void singleThrowsWhenEmpty() {
        Assertions.assertThrows(IncorrectResultSizeException.class, () ->
                jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                        .param("id", 999L).query(DevSummary.class).single());
    }

    @Test
    public void singleThrowsWhenMany() {
        Assertions.assertThrows(IncorrectResultSizeException.class, () ->
                jdbcClient.sql("SELECT id, dev_name FROM engineers").query(DevSummary.class).single());
    }

    @Test
    public void optionalFoundAndEmpty() {
        Optional<DevSummary> found = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .param("id", 1L).query(DevSummary.class).optional();
        Assertions.assertTrue(found.isPresent());

        Optional<DevSummary> missing = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .param("id", 999L).query(DevSummary.class).optional();
        Assertions.assertTrue(missing.isEmpty());
    }

    @Test
    public void singleValue() {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM engineers").singleValue(Long.class);
        Assertions.assertEquals(2L, count);
    }

    @Test
    public void singleValueThrowsWhenEmpty() {
        Assertions.assertThrows(IncorrectResultSizeException.class, () ->
                jdbcClient.sql("SELECT id FROM engineers WHERE id = 999").singleValue(Long.class));
    }

    @Test
    public void optionalValue() {
        Optional<Long> count = jdbcClient.sql("SELECT COUNT(*) FROM engineers").optionalValue(Long.class);
        Assertions.assertEquals(Optional.of(2L), count);

        Optional<Long> empty = jdbcClient.sql("SELECT id FROM engineers WHERE id = 999")
                .optionalValue(Long.class);
        Assertions.assertTrue(empty.isEmpty());
    }

    @Test
    public void customRowMapper() {
        List<String> names = jdbcClient.sql("SELECT dev_name FROM engineers ORDER BY id")
                .query((rs, rowNum) -> rs.getString("dev_name")).list();
        Assertions.assertEquals(List.of("Duke Jakarta", "Arquillian Glassfish"), names);
    }

    @Test
    public void explicitRowMapper() {
        RowMapper<DevSummary> rowMapper =
                (rs, rowNum) -> new DevSummary(rs.getLong("id"), rs.getString("dev_name"));

        List<DevSummary> summaries = jdbcClient.sql("SELECT id, dev_name FROM engineers ORDER BY id")
                .query(rowMapper).list();

        Assertions.assertEquals(List.of(
                new DevSummary(1L, "Duke Jakarta"),
                new DevSummary(2L, "Arquillian Glassfish")), summaries);
    }

    @Test
    public void listMapsToPojo() {
        List<DevSummaryPojo> all = jdbcClient.sql("SELECT id, dev_name FROM engineers ORDER BY id")
                .query(DevSummaryPojo.class).list();

        Assertions.assertEquals(2, all.size());
        Assertions.assertEquals(1L, all.get(0).getId());
        Assertions.assertEquals("Duke Jakarta", all.get(0).getDevName());
        Assertions.assertEquals(2L, all.get(1).getId());
        Assertions.assertEquals("Arquillian Glassfish", all.get(1).getDevName());
    }

    @Test
    public void singleMapsToPojo() {
        DevSummaryPojo summary = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .param("id", 2L).query(DevSummaryPojo.class).single();

        Assertions.assertEquals(2L, summary.getId());
        Assertions.assertEquals("Arquillian Glassfish", summary.getDevName());
    }

    @Test
    public void stream() {
        List<DevSummary> all = jdbcClient.sql("SELECT id, dev_name FROM engineers ORDER BY id")
                .query(DevSummary.class).stream().toList();
        Assertions.assertEquals(2, all.size());
    }

    @Test
    public void paramsMap() {
        List<DevSummary> r = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .params(Map.of("id", 2L)).query(DevSummary.class).list();
        Assertions.assertEquals("Arquillian Glassfish", r.get(0).devName());
    }

    @Test
    public void paramsVarargs() {
        List<DevSummary> r = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = ?")
                .params(1L).query(DevSummary.class).list();
        Assertions.assertEquals("Duke Jakarta", r.get(0).devName());
    }

    @Test
    public void generatedKeys() {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        int rows = jdbcClient.sql("INSERT INTO engineers_gen (dev_name) VALUES (:name)")
                .param("name", "New Dev").update(keys);
        Assertions.assertEquals(1, rows);
        Assertions.assertNotNull(keys.getKey());
        Assertions.assertTrue(((Number) keys.getKey()).longValue() > 0);
    }

    @Test
    public void batchPositional() {
        Object[][] batch = {{"A"}, {"B"}};
        int[] counts = jdbcClient.sql("INSERT INTO engineers_gen (dev_name) VALUES (?)").batchUpdate(batch);
        Assertions.assertEquals(2, counts.length);
    }

    @Test
    public void batchNamed() {
        List<Map<String, Object>> batch = List.of(Map.of("name", "C"), Map.of("name", "D"));
        int[] counts = jdbcClient.sql("INSERT INTO engineers_gen (dev_name) VALUES (:name)").batchUpdate(batch);
        Assertions.assertEquals(2, counts.length);
    }

    @Test
    public void queryTimeout() {
        List<DevSummary> all = jdbcClient.sql("SELECT id, dev_name FROM engineers ORDER BY id")
                .queryTimeout(10).query(DevSummary.class).list();
        Assertions.assertEquals(2, all.size());
    }

    @Test
    public void mixingThrows() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                        .param("id", 1L).param(1L));
    }
}
