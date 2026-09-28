package io.github.hantsy.jdbc.sqlinit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Resolves script locations into the {@link Resource}s they hold.
 *
 * <p>A location is either {@code classpath:} (resolved through the context classloader only),
 * {@code filesystem:}, or, without a prefix, classpath. Each accepts a literal file, a directory
 * (scanned recursively for {@code *.sql}), or an Ant-style pattern ({@code **}, {@code *}, {@code ?}).</p>
 */
final class ScriptLocator {

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILESYSTEM_PREFIX = "filesystem:";
    private static final String SQL_GLOB = "**/*.sql";

    List<Resource> resolve(List<String> locations) throws SQLException {
        Set<String> seen = new LinkedHashSet<>();
        List<Resource> resources = new ArrayList<>();
        for (String location : locations) {
            for (Resource resource : resolveLocation(location)) {
                if (seen.add(resource.url().toExternalForm())) {
                    resources.add(resource);
                }
            }
        }
        Collections.sort(resources);
        return resources;
    }

    private List<Resource> resolveLocation(String location) throws SQLException {
        if (location.startsWith(FILESYSTEM_PREFIX)) {
            return resolveFilesystem(location.substring(FILESYSTEM_PREFIX.length()));
        }
        String classpath = location.startsWith(CLASSPATH_PREFIX)
                ? location.substring(CLASSPATH_PREFIX.length())
                : location;
        return resolveClasspath(stripLeadingSlash(classpath));
    }

    private List<Resource> resolveClasspath(String path) throws SQLException {
        if (path.isEmpty()) {
            throw new SQLException("Empty classpath script location");
        }
        if (AntPathMatcher.isPattern(path)) {
            return scanClasspath(path);
        }
        if (path.endsWith(".sql")) {
            URL url = classLoader().getResource(path);
            return url == null ? failLiteral(path) : List.of(new Resource(path, url));
        }
        return scanClasspath(path + "/" + SQL_GLOB);
    }

    private List<Resource> scanClasspath(String pattern) throws SQLException {
        int rootEnd = wildcardRoot(pattern);
        String root = pattern.substring(0, rootEnd);
        String entryPrefix = root.isEmpty() ? "" : root + "/";
        String relativePattern = pattern.substring(entryPrefix.length());

        List<Resource> resources = new ArrayList<>();
        try {
            Enumeration<URL> roots = classLoader().getResources(root);
            while (roots.hasMoreElements()) {
                scanRoot(roots.nextElement(), entryPrefix, relativePattern, resources);
            }
        } catch (IOException | URISyntaxException e) {
            throw new SQLException("Failed to scan the classpath for SQL scripts at: " + pattern, e);
        }
        return resources;
    }

    private void scanRoot(URL rootUrl, String entryPrefix, String relativePattern, List<Resource> resources)
            throws IOException, URISyntaxException {
        switch (rootUrl.getProtocol()) {
            case "file" -> scanDirectory(Paths.get(rootUrl.toURI()), relativePattern, resources);
            case "jar" -> scanJar(rootUrl, entryPrefix, relativePattern, resources);
            default -> { /* unsupported classpath root, skip */ }
        }
    }

    private void scanDirectory(Path root, String relativePattern, List<Resource> resources) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (AntPathMatcher.match(relativePattern, relative)) {
                    resources.add(new Resource(relative, toUrl(file)));
                }
            });
        }
    }

    private void scanJar(URL rootUrl, String entryPrefix, String relativePattern, List<Resource> resources)
            throws IOException, URISyntaxException {
        JarURLConnection connection = (JarURLConnection) rootUrl.openConnection();
        connection.setUseCaches(false);
        URI jarUri = connection.getJarFileURL().toURI();
        try (JarFile jarFile = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(entryPrefix)) {
                    continue;
                }
                String relative = name.substring(entryPrefix.length());
                if (AntPathMatcher.match(relativePattern, relative)) {
                    resources.add(new Resource(relative,
                            new URI("jar", jarUri.toASCIIString() + "!/" + name, null).toURL()));
                }
            }
        }
    }

    private List<Resource> resolveFilesystem(String path) throws SQLException {
        String normalized = path.replace('\\', '/');
        if (normalized.isEmpty()) {
            throw new SQLException("Empty filesystem script location");
        }
        Path file = Paths.get(normalized);
        if (AntPathMatcher.isPattern(normalized)) {
            return scanFilesystemPattern(normalized);
        }
        if (Files.isRegularFile(file)) {
            return List.of(new Resource(normalized, toUrl(file)));
        }
        if (Files.isDirectory(file)) {
            return scanFilesystem(file, SQL_GLOB);
        }
        if (normalized.endsWith(".sql")) {
            return failLiteral(path);
        }
        return List.of();
    }

    private List<Resource> scanFilesystemPattern(String pattern) throws SQLException {
        int rootEnd = wildcardRoot(pattern);
        if (rootEnd == 0) {
            return List.of();
        }
        String rootPath = pattern.substring(0, rootEnd);
        Path root = Paths.get(rootPath);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        String entryPrefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
        String relativePattern = pattern.startsWith(entryPrefix) ? pattern.substring(entryPrefix.length()) : pattern;
        return scanFilesystem(root, relativePattern);
    }

    private List<Resource> scanFilesystem(Path root, String relativePattern) throws SQLException {
        List<Resource> resources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (AntPathMatcher.match(relativePattern, relative)) {
                    resources.add(new Resource(relative, toUrl(file)));
                }
            });
        } catch (IOException e) {
            throw new SQLException("Failed to scan the filesystem for SQL scripts at: " + root, e);
        }
        return resources;
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Resource> failLiteral(String path) throws SQLException {
        throw new SQLException("SQL script not found: " + path);
    }

    private ClassLoader classLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : ScriptLocator.class.getClassLoader();
    }

    private static String stripLeadingSlash(String location) {
        String result = location;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    /** Returns the length of the leading wildcard-free part of a pattern, excluding its separator. */
    private static int wildcardRoot(String pattern) {
        int start = 0;
        while (start < pattern.length()) {
            int slash = pattern.indexOf('/', start);
            String segment = slash < 0 ? pattern.substring(start) : pattern.substring(start, slash);
            if (AntPathMatcher.isPattern(segment)) {
                return start == 0 ? 0 : start - 1;
            }
            if (slash < 0) {
                return pattern.length();
            }
            start = slash + 1;
        }
        return start;
    }
}
