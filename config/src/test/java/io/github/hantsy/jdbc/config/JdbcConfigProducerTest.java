package io.github.hantsy.jdbc.config;

import io.github.hantsy.jdbc.JdbcConfig;
import io.smallrye.config.inject.ConfigExtension;

import jakarta.inject.Inject;

import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnableAutoWeld
@AddExtensions(ConfigExtension.class)
@AddBeanClasses(JdbcConfigProducer.class)
class JdbcConfigProducerTest {

    @Inject
    JdbcConfig config;

    @Test
    void mapsConfigProperties() {
        assertNotNull(config);
        assertEquals("$", config.placeholder());
        assertEquals(30, config.queryTimeout());
        assertEquals(100, config.fetchSize());
    }
}
