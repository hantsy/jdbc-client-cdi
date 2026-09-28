package io.github.hantsy.jdbc.sqlinit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptLocatorTest {

    @Test
    void resolvesAClasspathDirectoryRecursively() throws SQLException {
        List<Resource> resources = new ScriptLocator().resolve(List.of("classpath:db/migration"));
        assertEquals(List.of("V1__create.sql", "V2__second.sql", "V3__third.sql"),
                resources.stream().map(Resource::fileName).toList());
    }

    @Test
    void resolvesAClasspathPattern() throws SQLException {
        List<Resource> resources = new ScriptLocator().resolve(List.of("classpath:db/migration/**/*.sql"));
        assertEquals(List.of("V1__create.sql", "V2__second.sql", "V3__third.sql"),
                resources.stream().map(Resource::fileName).toList());
    }

    @Test
    void resolvesALiteralClasspathFile() throws SQLException {
        List<Resource> resources = new ScriptLocator().resolve(List.of("classpath:db/migration/V1__create.sql"));
        assertEquals(List.of("V1__create.sql"), resources.stream().map(Resource::fileName).toList());
    }

    @Test
    void failsWhenALiteralClasspathFileIsMissing() {
        assertThrows(SQLException.class, () -> new ScriptLocator().resolve(List.of("classpath:db/nope.sql")));
    }

    @Test
    void resolvesAFilesystemDirectoryRecursively(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("V1__a.sql"), "SELECT 1");
        Files.writeString(tempDir.resolve("V2__b.sql"), "SELECT 2");
        Files.writeString(tempDir.resolve("notes.txt"), "ignored");
        Files.createDirectory(tempDir.resolve("nested"));
        Files.writeString(tempDir.resolve("nested/V3__c.sql"), "SELECT 3");

        List<Resource> resources = new ScriptLocator().resolve(List.of("filesystem:" + tempDir));

        assertEquals(List.of("V1__a.sql", "V2__b.sql", "V3__c.sql"),
                resources.stream().map(Resource::fileName).toList());
    }

    @Test
    void scansScriptsInsideAJar(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("scripts.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            writeDirectory(out, "db/");
            writeDirectory(out, "db/migration/");
            writeEntry(out, "db/migration/V1__create.sql", "CREATE TABLE t (id INT);");
            writeEntry(out, "db/migration/V2__second.sql", "INSERT INTO t VALUES (1);");
            writeEntry(out, "db/migration/readme.txt", "ignored");
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(loader);
            try {
                List<Resource> resources = new ScriptLocator().resolve(List.of("classpath:db/migration"));
                assertEquals(List.of("V1__create.sql", "V2__second.sql"),
                        resources.stream().map(Resource::fileName).toList());
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
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
