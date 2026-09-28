package io.github.hantsy.jdbc.sqlinit.config;

import io.github.hantsy.jdbc.sqlinit.SqlInitConfig;
import io.smallrye.config.inject.ConfigExtension;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import java.util.List;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnableAutoWeld
@AddExtensions(ConfigExtension.class)
@AddBeanClasses(SqlInitConfigProducer.class)
class SqlInitConfigProducerTest {

    @Inject
    SqlInitConfig config;

    @Test
    void mapsConfigProperties() {
        assertNotNull(config);
        assertEquals("/", config.separator());
        assertEquals(List.of("classpath:db/migration", "filesystem:/opt/sql"), config.scriptLocations());
        assertEquals("h2", config.platform());
        assertEquals("custom_migrations", config.historyTable());
    }
}
