package io.github.hantsy.jdbc.sqlinit;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.sql.DataSource;

/**
 * Applies SQL scripts to a {@link DataSource}.
 *
 * <p>Schema locations are resolved and executed before data locations. All statements of all
 * resolved scripts run inside a single transaction: the connection is switched off auto-commit, and
 * the first failure rolls back everything already applied before it is rethrown.</p>
 *
 * <p>Every location is a classpath location, optionally prefixed with {@code classpath:} or
 * {@code classpath*:}. A location without wildcards resolves to the first matching resource on the
 * classpath; a location with Ant-style wildcards ({@code **}, {@code *}, {@code ?}) is scanned
 * across the matching classpath roots, and the scripts it resolves to are executed in alphabetical
 * order of their resource path.</p>
 *
 * <p>Scripts are read as UTF-8 and split by {@link SqlScriptParser}. Some engines, MySQL and
 * MariaDB among them, commit implicitly on DDL, so a rollback cannot undo a schema script that has
 * already been applied on those databases.</p>
 *
 * <p>Scanning inside an archive requires that archive to carry directory entries for the scanned
 * path, which is what the {@code jar} tool and the Maven and Gradle jar plugins produce.</p>
 */
public class SqlScriptPopulator {

    private static final Logger LOGGER = Logger.getLogger(SqlScriptPopulator.class.getName());

    private static final String CLASSPATH_ALL_PREFIX = "classpath*:";
    private static final String CLASSPATH_PREFIX = "classpath:";

    private final DataSource dataSource;
    private final SqlInitConfig config;

    /**
     * Creates a populator that runs the default {@code /schema.sql} and {@code /data.sql} scripts
     * with a {@code ;} separator.
     *
     * @param dataSource the database to initialize
     */
    public SqlScriptPopulator(DataSource dataSource) {
        this(dataSource, SqlInitConfig.DEFAULT);
    }

    /**
     * Creates a populator for an explicit configuration.
     *
     * @param dataSource the database to initialize
     * @param config     the separator and script locations to apply
     */
    public SqlScriptPopulator(DataSource dataSource, SqlInitConfig config) {
        this.dataSource = dataSource;
        this.config = config;
    }

    /**
     * Resolves the configured locations and executes the scripts they contain.
     *
     * @throws SQLException if a literal location cannot be found, a script cannot be read or parsed,
     *                      or a statement fails; the transaction is rolled back before this is thrown
     */
    public void populate() throws SQLException {
        ClassLoader classLoader = classLoader();
        List<URL> scripts = new ArrayList<>(
                resolve(classLoader, config.schemaLocations(), SqlInitConfig.DEFAULT.schemaLocations()));
        scripts.addAll(resolve(classLoader, config.dataLocations(), SqlInitConfig.DEFAULT.dataLocations()));

        if (scripts.isEmpty()) {
            LOGGER.info("No SQL scripts resolved, skipping database initialization");
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                execute(connection, scripts);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
            LOGGER.info(() -> "Database initialization completed, applied " + scripts.size() + " SQL script(s)");
        }
    }

    private void execute(Connection connection, List<URL> scripts) throws SQLException {
        for (URL script : scripts) {
            LOGGER.info(() -> "Executing SQL script: " + script);
            List<String> statements = parse(script);
            if (statements.isEmpty()) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.addBatch(sql);
                }
                statement.executeBatch();
            } catch (SQLException e) {
                throw new SQLException("Failed to execute SQL script: " + script, e.getSQLState(), e.getErrorCode(), e);
            }
        }
    }

    private List<String> parse(URL script) throws SQLException {
        try {
            URLConnection connection = script.openConnection();
            if (connection instanceof JarURLConnection jarConnection) {
                // The JDK jar cache would keep the archive open for the lifetime of the JVM.
                jarConnection.setUseCaches(false);
            }
            try (InputStream in = connection.getInputStream();
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return SqlScriptParser.parse(reader, config.separator());
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read SQL script: " + script, e);
        }
    }

    private List<URL> resolve(ClassLoader classLoader, List<String> locations, List<String> defaults)
            throws SQLException {
        List<URL> resolved = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        boolean optional = locations.equals(defaults);

        for (String location : locations) {
            List<Candidate> candidates = resolveLocation(classLoader, location);
            if (candidates.isEmpty()) {
                if (optional) {
                    LOGGER.fine(() -> "No SQL script found for the default location: " + location);
                } else if (AntPathMatcher.isPattern(stripPrefix(location))) {
                    LOGGER.warning(() -> "No SQL script found for the location pattern: " + location);
                } else {
                    throw new SQLException("SQL script not found on the classpath: " + location);
                }
                continue;
            }
            Collections.sort(candidates);
            for (Candidate candidate : candidates) {
                if (seen.add(candidate.url().toExternalForm())) {
                    resolved.add(candidate.url());
                }
            }
        }
        return resolved;
    }

    private List<Candidate> resolveLocation(ClassLoader classLoader, String location) throws SQLException {
        String pattern = stripLeadingSlash(stripPrefix(location));
        if (pattern.isEmpty()) {
            throw new SQLException("Empty SQL script location: " + location);
        }

        List<Candidate> candidates = new ArrayList<>();
        try {
            if (!AntPathMatcher.isPattern(pattern)) {
                Enumeration<URL> resources = classLoader.getResources(pattern);
                if (resources.hasMoreElements()) {
                    candidates.add(new Candidate(pattern, resources.nextElement()));
                }
                return candidates;
            }

            int rootEnd = wildcardRoot(pattern);
            String root = pattern.substring(0, rootEnd);
            String entryPrefix = root.isEmpty() ? "" : root + "/";
            String relativePattern = pattern.substring(entryPrefix.length());

            Enumeration<URL> roots = classLoader.getResources(root);
            while (roots.hasMoreElements()) {
                scan(roots.nextElement(), entryPrefix, relativePattern, candidates);
            }
            return candidates;
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            throw new SQLException("Failed to scan the classpath for SQL scripts at: " + location, e);
        }
    }

    private void scan(URL rootUrl, String entryPrefix, String relativePattern, List<Candidate> candidates)
            throws IOException, URISyntaxException {
        switch (rootUrl.getProtocol()) {
            case "file" -> scanDirectory(rootUrl, relativePattern, candidates);
            case "jar" -> scanJar(rootUrl, entryPrefix, relativePattern, candidates);
            default -> LOGGER.warning(() -> "Unsupported classpath root, skipping: " + rootUrl);
        }
    }

    private void scanDirectory(URL rootUrl, String relativePattern, List<Candidate> candidates)
            throws IOException, URISyntaxException {
        Path root = Paths.get(rootUrl.toURI());
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile).toList();
        }
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (AntPathMatcher.match(relativePattern, relative)) {
                candidates.add(new Candidate(relative, file.toUri().toURL()));
            }
        }
    }

    private void scanJar(URL rootUrl, String entryPrefix, String relativePattern, List<Candidate> candidates)
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
                    candidates.add(new Candidate(relative, new URI("jar", jarUri.toASCIIString() + "!/" + name, null)
                            .toURL()));
                }
            }
        }
    }

    private void restoreAutoCommit(Connection connection, boolean originalAutoCommit) {
        try {
            if (connection.getAutoCommit() != originalAutoCommit) {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            LOGGER.warning(() -> "Failed to restore the connection auto-commit flag: " + e.getMessage());
        }
    }

    private ClassLoader classLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : SqlScriptPopulator.class.getClassLoader();
    }

    private static String stripPrefix(String location) {
        if (location.startsWith(CLASSPATH_ALL_PREFIX)) {
            return location.substring(CLASSPATH_ALL_PREFIX.length());
        }
        if (location.startsWith(CLASSPATH_PREFIX)) {
            return location.substring(CLASSPATH_PREFIX.length());
        }
        return location;
    }

    private static String stripLeadingSlash(String location) {
        String result = location;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    /**
     * Returns the length of the leading wildcard-free part of a pattern, excluding the separator
     * that precedes the first wildcard segment.
     */
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

    /**
     * A resolved script, ordered by its classpath resource path and then by URL so that scanning
     * several classpath roots stays deterministic.
     */
    private record Candidate(String resourcePath, URL url) implements Comparable<Candidate> {

        @Override
        public int compareTo(Candidate other) {
            int result = this.resourcePath.compareTo(other.resourcePath);
            return result != 0 ? result : this.url.toExternalForm().compareTo(other.url.toExternalForm());
        }
    }
}
