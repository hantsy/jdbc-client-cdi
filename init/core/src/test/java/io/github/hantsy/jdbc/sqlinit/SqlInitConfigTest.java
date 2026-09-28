package io.github.hantsy.jdbc.sqlinit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SqlInitConfigTest {

    @Test
    void appliesDefaults() {
        SqlInitConfig config = SqlInitConfig.defaults();

        assertEquals(List.of("classpath:db/migration"), config.scriptLocations());
        assertEquals(";", config.separator());
        assertNull(config.platform());
        assertEquals("sqlinit_migration", config.historyTable());
    }

    @Test
    void overridesEveryValue() {
        SqlInitConfig config = SqlInitConfig.builder()
                .scriptLocations(List.of("classpath:db/migration", "filesystem:/opt/sql"))
                .separator("/")
                .platform("postgresql")
                .historyTable("mig_history")
                .build();

        assertEquals(List.of("classpath:db/migration", "filesystem:/opt/sql"), config.scriptLocations());
        assertEquals("/", config.separator());
        assertEquals("postgresql", config.platform());
        assertEquals("mig_history", config.historyTable());
    }
}
