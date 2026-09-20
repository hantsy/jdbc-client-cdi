package io.github.hantsy.jdbc;

import io.github.hantsy.jdbc.converter.Converter;
import io.github.hantsy.jdbc.converter.ConverterRegistry;
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

@ExtendWith(ArquillianExtension.class)
public class JdbcClientTest {

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "test-jdbc-pipeline.war")
                .addClasses(JdbcClient.class, ConverterRegistry.class, Converter.class)
                .addClasses(TestDataSourceProducer.class, DevSummary.class)
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
        }
    }

    @Test
    public void verifyNamedAndPositionalQueryProjections() {
        // Test Named Parameter Binding
        List<DevSummary> namedResult = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = :targetId")
                .param("targetId", 1L)
                .query(DevSummary.class)
                .list();

        Assertions.assertEquals(1, namedResult.size());
        Assertions.assertEquals("Duke Jakarta", namedResult.get(0).devName());

        // Test Positional Parameter Binding
        List<DevSummary> positionalResult = jdbcClient.sql("SELECT id, dev_name FROM engineers WHERE id = ?")
                .param(2L)
                .query(DevSummary.class)
                .list();

        Assertions.assertEquals(1, positionalResult.size());
        Assertions.assertEquals("Arquillian Glassfish", positionalResult.get(0).devName());
    }
}
