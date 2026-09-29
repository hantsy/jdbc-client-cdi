package io.github.hantsy.jdbc.sqlinit.resource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves locations against the operating system's file system.
 */
public class FileSystemResourceResolver implements ResourceResolver {

    private static final String SQL_GLOB = "**/*.sql";

    private final PathMatcher pathMatcher;

    public FileSystemResourceResolver() {
        this(new AntPathMatcher());
    }

    public FileSystemResourceResolver(PathMatcher pathMatcher) {
        this.pathMatcher = pathMatcher;
    }

    @Override
    public Resource getResource(String location) {
        return new FileSystemResource(Paths.get(location));
    }

    @Override
    public List<Resource> getResources(String pattern) throws IOException {
        return resolveFilesystem(pattern);
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
