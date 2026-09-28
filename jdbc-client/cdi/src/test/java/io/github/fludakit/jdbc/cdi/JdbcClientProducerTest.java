package io.github.fludakit.jdbc.cdi;

import io.github.fludakit.jdbc.JdbcClient;
import io.github.fludakit.jdbc.converter.ConverterRegistry;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnableAutoWeld
@AddBeanClasses({JdbcClientProducer.class, ConverterRegistryProducer.class, TestDataSourceProducer.class})
class JdbcClientProducerTest {

    @Inject
    JdbcClient client;

    @Inject
    ConverterRegistry registry;

    @Inject
    DataSource dataSource;

    @Test
    void beansExist() {
        assertNotNull(client);
        assertNotNull(registry);
        assertNotNull(dataSource);
    }

    @Test
    void connectionIsUsable() {
        assertEquals(1, client.sql("SELECT 1").singleValue(Integer.class));
    }
}
