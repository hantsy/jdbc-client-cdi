package io.github.hantsy.jdbc.sqlinit.resource;

import java.io.FileNotFoundException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Resolves location patterns by combining a {@link PathMatcher} with the classpath and file-system
 * loaders, scanning directories and jar/zip archives.
 */
public class PathMatchingResourcePatternResolver implements ResourcePatternResolver {

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILE_PREFIX = "file:";
    private static final String FILESYSTEM_PREFIX = "filesystem:";
    private static final String SQL_GLOB = "**/*.sql";

    private final ClassLoader classLoader;
    private final PathMatcher pathMatcher;
    private final ClassPathResourceLoader classPathResourceLoader;
    private final FileSystemResourceLoader fileSystemResourceLoader;

    public PathMatchingResourcePatternResolver() {
        this(ResourceUtils.defaultClassLoader());
    }

    public PathMatchingResourcePatternResolver(ClassLoader classLoader) {
        this(classLoader, new AntPathMatcher());
    }

    public PathMatchingResourcePatternResolver(ClassLoader classLoader, PathMatcher pathMatcher) {
        this.classLoader = classLoader;
        this.pathMatcher = pathMatcher;
        this.classPathResourceLoader = new ClassPathResourceLoader(classLoader);
        this.fileSystemResourceLoader = new FileSystemResourceLoader();
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public Resource getResource(String location) {
        if (location.startsWith(FILESYSTEM_PREFIX)) {
            return fileSystemResourceLoader.getResource(location.substring(FILESYSTEM_PREFIX.length()));
        }
        if (location.startsWith(FILE_PREFIX)) {
            return fileSystemResourceLoader.getResource(location.substring(FILE_PREFIX.length()));
        }
        String classpath = location.startsWith(CLASSPATH_PREFIX)
                ? location.substring(CLASSPATH_PREFIX.length())
                : location;
        return classPathResourceLoader.getResource(classpath);
    }

    @Override
    public List<Resource> getResources(String locationPattern) throws IOException {
        if (locationPattern.startsWith(FILESYSTEM_PREFIX)) {
            return resolveFilesystem(locationPattern.substring(FILESYSTEM_PREFIX.length()));
        }
        if (locationPattern.startsWith(FILE_PREFIX)) {
            return resolveFilesystem(locationPattern.substring(FILE_PREFIX.length()));
        }
        String classpath = locationPattern.startsWith(CLASSPATH_PREFIX)
                ? locationPattern.substring(CLASSPATH_PREFIX.length())
                : locationPattern;
        return resolveClasspath(ResourceUtils.stripLeadingSlash(classpath));
    }

    private List<Resource> resolveClasspath(String path) throws IOException {
        if (path.isEmpty()) {
            throw new IOException("Empty classpath resource location");
        }
        if (pathMatcher.isPattern(path)) {
            return scanClasspath(path);
        }
        if (path.endsWith(".sql")) {
            URL url = classLoader.getResource(path);
            if (url == null) {
                throw new FileNotFoundException("Classpath resource not found: " + path);
            }
            return List.of(new ClassPathResource(path, classLoader));
        }
        return scanClasspath(path + "/" + SQL_GLOB);
    }

    private List<Resource> scanClasspath(String pattern) throws IOException {
        int rootEnd = wildcardRoot(pattern);
        String root = pattern.substring(0, rootEnd);
        String entryPrefix = root.isEmpty() ? "" : root + "/";
        String relativePattern = pattern.substring(entryPrefix.length());

        List<Resource> resources = new ArrayList<>();
        try {
            Enumeration<URL> roots = classLoader.getResources(root);
            while (roots.hasMoreElements()) {
                scanRoot(roots.nextElement(), entryPrefix, relativePattern, resources);
            }
        } catch (URISyntaxException e) {
            throw new IOException("Failed to scan the classpath for resources at: " + pattern, e);
        }
        resources.sort(Comparator.comparing(Resource::getFilename));
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
                if (pathMatcher.match(relativePattern, relative)) {
                    resources.add(new UrlResource(toUrl(file)));
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
                if (pathMatcher.match(relativePattern, relative)) {
                    resources.add(new UrlResource(
                            new URI("jar", jarUri.toASCIIString() + "!/" + name, null).toURL()));
                }
            }
        }
    }

    private List<Resource> resolveFilesystem(String path) throws IOException {
        String normalized = path.replace('\\', '/');
        if (normalized.isEmpty()) {
            throw new IOException("Empty filesystem resource location");
        }
        Path file = Paths.get(normalized);
        if (pathMatcher.isPattern(normalized)) {
            return scanFilesystemPattern(normalized);
        }
        if (Files.isRegularFile(file)) {
            return List.of(new FileSystemResource(file));
        }
        if (Files.isDirectory(file)) {
            return scanFilesystem(file, SQL_GLOB);
        }
        if (normalized.endsWith(".sql")) {
            throw new FileNotFoundException("Filesystem resource not found: " + path);
        }
        return List.of();
    }

    private List<Resource> scanFilesystemPattern(String pattern) throws IOException {
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

    private List<Resource> scanFilesystem(Path root, String relativePattern) throws IOException {
        List<Resource> resources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (pathMatcher.match(relativePattern, relative)) {
                    resources.add(new FileSystemResource(file));
                }
            });
        }
        resources.sort(Comparator.comparing(Resource::getFilename));
        return resources;
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the length of the leading wildcard-free part of a pattern, excluding its separator. */
    private int wildcardRoot(String pattern) {
        int start = 0;
        while (start < pattern.length()) {
            int slash = pattern.indexOf('/', start);
            String segment = slash < 0 ? pattern.substring(start) : pattern.substring(start, slash);
            if (pathMatcher.isPattern(segment)) {
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
