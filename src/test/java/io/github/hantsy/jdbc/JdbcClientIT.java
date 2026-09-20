package io.github.hantsy.jdbc;

import jakarta.inject.Inject;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ExtendWith(ArquillianExtension.class)
public class JdbcClientIT {

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "test-jdbc-pipeline.war")
                .addPackages(true, "io.github.hantsy.jdbc")
                .addAsResource("microprofile-config.properties")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    private JdbcClient jdbcClient;

    @Inject
    private DataSource dataSource;

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

    @Test
    public void verifyNamedAndPositionalQueryProjections() {
        List<DevSummary> namedResult = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :targetId")
                .param("targetId", 1L)
                .query(DevSummary.class)
                .list();
        Assertions.assertEquals(1, namedResult.size());
        Assertions.assertEquals("Duke Jakarta", namedResult.get(0).devName());

        List<DevSummary> positionalResult = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = ?")
                .param(2L)
                .query(DevSummary.class)
                .list();
        Assertions.assertEquals(1, positionalResult.size());
        Assertions.assertEquals("Arquillian Glassfish", positionalResult.get(0).devName());
    }

    @Test
    public void singleReturnsExactlyOneRow() {
        DevSummary result = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .param("id", 1L)
                .query(DevSummary.class)
                .single();
        Assertions.assertEquals("Duke Jakarta", result.devName());
    }

    @Test
    public void singleThrowsWhenEmpty() {
        Assertions.assertThrows(IncorrectResultSizeException.class, () ->
                jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                        .param("id", 999L)
                        .query(DevSummary.class)
                        .single());
    }

    @Test
    public void singleThrowsWhenMultipleRows() {
        Assertions.assertThrows(IncorrectResultSizeException.class, () ->
                jdbcClient.sql("SELECT id, dev_name FROM engineers")
                        .query(DevSummary.class)
                        .single());
    }

    @Test
    public void optionalReturnsValueWhenFound() {
        Optional<DevSummary> result = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .param("id", 1L)
                .query(DevSummary.class)
                .optional();
        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals("Duke Jakarta", result.get().devName());
    }

    @Test
    public void optionalIsEmptyWhenNotFound() {
        Optional<DevSummary> result = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .param("id", 999L)
                .query(DevSummary.class)
                .optional();
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    public void optionalThrowsWhenMultipleRows() {
        Assertions.assertThrows(IncorrectResultSizeException.class, () ->
                jdbcClient.sql("SELECT id, dev_name FROM engineers")
                        .query(DevSummary.class)
                        .optional());
    }

    @Test
    public void singleValueReadsScalar() {
        Optional<Long> count = jdbcClient.sql("SELECT COUNT(*) FROM engineers")
                .singleValue(Long.class);
        Assertions.assertEquals(Optional.of(2L), count);
    }

    @Test
    public void queryWithCustomRowMapper() {
        List<String> names = jdbcClient.sql("SELECT dev_name FROM engineers ORDER BY id")
                .query((rs, rowNum) -> rs.getString("dev_name"))
                .list();
        Assertions.assertEquals(List.of("Duke Jakarta", "Arquillian Glassfish"), names);
    }

    @Test
    public void streamYieldsAllRows() {
        List<DevSummary> all = jdbcClient.sql("SELECT id, dev_name FROM engineers ORDER BY id")
                .query(DevSummary.class)
                .stream()
                .toList();
        Assertions.assertEquals(2, all.size());
        Assertions.assertEquals("Duke Jakarta", all.get(0).devName());
    }

    @Test
    public void paramsWithMap() {
        List<DevSummary> result = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .params(Map.of("id", 1L))
                .query(DevSummary.class)
                .list();
        Assertions.assertEquals("Duke Jakarta", result.get(0).devName());
    }

    @Test
    public void paramsWithVarargs() {
        List<DevSummary> result = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = ?")
                .params(2L)
                .query(DevSummary.class)
                .list();
        Assertions.assertEquals("Arquillian Glassfish", result.get(0).devName());
    }

    @Test
    public void updateWithKeyHolderPopulatesHolder() {
        KeyHolder holder = new GeneratedKeyHolder();
        int rows = jdbcClient.sql("INSERT INTO engineers_gen (dev_name) VALUES (:name)")
                .param("name", "Another Dev")
                .update(holder);
        Assertions.assertEquals(1, rows);
        Assertions.assertNotNull(holder.getKey());
    }

    @Test
    public void batchUpdateWithNamedMaps() {
        List<Map<String, Object>> batch = List.of(
                Map.of("name", "Dev C"),
                Map.of("name", "Dev D"));
        int[] counts = jdbcClient.sql("INSERT INTO engineers_gen (dev_name) VALUES (:name)")
                .batchUpdate(batch);
        Assertions.assertEquals(2, counts.length);
    }

    @Test
    public void batchUpdateWithPositionalArrays() {
        Object[][] batch = {{"Dev E"}, {"Dev F"}};
        int[] counts = jdbcClient.sql("INSERT INTO engineers_gen (dev_name) VALUES (?)")
                .batchUpdate(batch);
        Assertions.assertEquals(2, counts.length);
    }

    @Test
    public void maxRowsLimitsResultSet() {
        List<DevSummary> limited = jdbcClient.sql("SELECT id, dev_name FROM engineers ORDER BY id")
                .maxRows(1)
                .query(DevSummary.class)
                .list();
        Assertions.assertEquals(1, limited.size());
    }

    @Test
    public void fetchSizeIsAccepted() {
        List<DevSummary> result = jdbcClient.sql("SELECT id, dev_name FROM engineers ORDER BY id")
                .fetchSize(10)
                .query(DevSummary.class)
                .list();
        Assertions.assertEquals(2, result.size());
    }

    @Test
    public void mixingNamedAndPositionalThrows() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                        .param("id", 1L)
                        .param(1L));
    }
}
