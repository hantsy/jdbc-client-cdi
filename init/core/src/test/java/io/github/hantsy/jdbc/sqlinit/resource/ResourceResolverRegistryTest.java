package io.github.hantsy.jdbc.sqlinit.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceResolverRegistryTest {

    private final ResourceResolverRegistry registry = new ResourceResolverRegistry();

    @Test
    void resolvesAClasspathDirectoryRecursively() throws IOException {
        List<Resource> resources = registry.getResources("classpath:db/migration");
        assertEquals(List.of("V1__create.sql", "V2__second.sql", "V3__third.sql"),
                resources.stream().map(Resource::getFilename).toList());
    }

    @Test
    void resolvesAClasspathPattern() throws IOException {
        List<Resource> resources = registry.getResources("classpath:db/migration/**/*.sql");
        assertEquals(List.of("V1__create.sql", "V2__second.sql", "V3__third.sql"),
                resources.stream().map(Resource::getFilename).toList());
    }

    @Test
    void resolvesALiteralClasspathFile() throws IOException {
        List<Resource> resources = registry.getResources("classpath:db/migration/V1__create.sql");
        assertEquals(List.of("V1__create.sql"), resources.stream().map(Resource::getFilename).toList());
    }

    @Test
    void failsWhenALiteralClasspathFileIsMissing() {
        assertThrows(IOException.class, () -> registry.getResources("classpath:db/nope.sql"));
    }

    @Test
    void resolvesAFilesystemDirectoryRecursively(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("V1__a.sql"), "SELECT 1");
        Files.writeString(tempDir.resolve("V2__b.sql"), "SELECT 2");
        Files.writeString(tempDir.resolve("notes.txt"), "ignored");
        Files.createDirectory(tempDir.resolve("nested"));
        Files.writeString(tempDir.resolve("nested/V3__c.sql"), "SELECT 3");

        List<Resource> resources = registry.getResources("filesystem:" + tempDir);

        assertEquals(List.of("V1__a.sql", "V2__b.sql", "V3__c.sql"),
                resources.stream().map(Resource::getFilename).toList());
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
                List<Resource> resources = new ResourceResolverRegistry().getResources("classpath:db/migration");
                assertEquals(List.of("V1__create.sql", "V2__second.sql"),
                        resources.stream().map(Resource::getFilename).toList());
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Test
    void registersACustomProtocol() throws IOException {
        ResourceResolverRegistry registry = new ResourceResolverRegistry();
        registry.register("s3", new InMemoryResourceResolver()
                .add("bucket/V1__create.sql", "CREATE TABLE t (id INT)")
                .add("bucket/V2__seed.sql", "INSERT INTO t VALUES (1)"));

        List<Resource> resources = registry.getResources("s3:bucket");

        assertEquals(List.of("V1__create.sql", "V2__seed.sql"),
                resources.stream().map(Resource::getFilename).toList());
        assertEquals("CREATE TABLE t (id INT)",
                new String(resources.get(0).getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsAnUnknownProtocol() {
        assertThrows(IllegalArgumentException.class, () -> registry.getResources("s3:bucket/key.sql"));
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

    private static final class InMemoryResourceResolver implements ResourceResolver {

        private final Map<String, byte[]> entries = new LinkedHashMap<>();

        InMemoryResourceResolver add(String name, String content) {
            entries.put(name, content.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        @Override
        public Resource getResource(String location) {
            byte[] content = entries.get(location);
            return new InMemoryResource(location, content == null ? new byte[0] : content, content != null);
        }

        @Override
        public List<Resource> getResources(String pattern) {
            List<Resource> resources = new ArrayList<>();
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                resources.add(new InMemoryResource(entry.getKey(), entry.getValue(), true));
            }
            resources.sort(Comparator.comparing(Resource::getFilename));
            return resources;
        }
    }

    private static final class InMemoryResource implements Resource {

        private final String name;
        private final byte[] content;
        private final boolean exists;

        InMemoryResource(String name, byte[] content, boolean exists) {
            this.name = name;
            this.content = content;
            this.exists = exists;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public boolean exists() {
            return exists;
        }

        @Override
        public long contentLength() {
            return content.length;
        }

        @Override
        public URL getURL() {
            throw new UnsupportedOperationException("In-memory resource has no URL");
        }

        @Override
        public String getFilename() {
            String normalized = name.replace('\\', '/');
            int slash = normalized.lastIndexOf('/');
            return slash < 0 ? normalized : normalized.substring(slash + 1);
        }
    }
}
