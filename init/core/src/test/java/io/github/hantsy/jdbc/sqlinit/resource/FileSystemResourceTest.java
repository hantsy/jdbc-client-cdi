package io.github.hantsy.jdbc.sqlinit.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemResourceTest {

    @Test
    void exposesAnExistingFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("V1__create.sql");
        Files.writeString(file, "SELECT 1;");

        Resource resource = new FileSystemResource(file);

        assertTrue(resource.exists());
        assertEquals("V1__create.sql", resource.getFilename());
        assertEquals(Files.size(file), resource.contentLength());
        assertEquals(file.toUri().toURL(), resource.getURL());
        assertEquals("SELECT 1;", new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void reportsAMissingFile(@TempDir Path tempDir) {
        Resource resource = new FileSystemResource(tempDir.resolve("missing.sql"));

        assertFalse(resource.exists());
    }
}
