package io.github.hantsy.jdbc.sqlinit.resource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassPathResourceTest {

    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    void exposesAnExistingResource() throws IOException {
        Resource resource = new ClassPathResource("db/migration/V1__create.sql", classLoader);

        assertTrue(resource.exists());
        assertEquals("V1__create.sql", resource.getFilename());
        assertTrue(resource.contentLength() > 0);
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("CREATE TABLE"));
    }

    @Test
    void reportsAMissingResource() {
        Resource resource = new ClassPathResource("db/does-not-exist.sql", classLoader);

        assertFalse(resource.exists());
        assertThrows(IOException.class, resource::getURL);
        assertThrows(IOException.class, resource::getInputStream);
    }
}
