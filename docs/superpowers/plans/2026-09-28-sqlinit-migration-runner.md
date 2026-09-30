# SQL Init: Lightweight Versioned Migration Runner — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single-transaction schema/data populator with a Flyway-like versioned migration runner that tracks applied migrations in a history table.

**Architecture:** A `SqlMigrator` resolves versioned scripts (`V<version>__<description>.sql`) from `classpath:`/`filesystem:` locations, then per migration atomically claims a `running` row, executes the script in its own transaction, and records `succeeded`/`failed`. A `DatabasePlatform` enum supplies dialect-specific history-table DDL.

**Tech Stack:** Java 21, JUnit 5 (Jupiter), H2 (test), MicroProfile Config, CDI (`jakarta.enterprise`).

## Global Constraints

- Java 21 (`maven.compiler.release=21`); use records, `switch` expressions, text blocks, `var`, `List.of`.
- Core package: `io.github.hantsy.jdbc.sqlinit` (JDK-only, no new runtime deps). CDI package `…sqlinit.cdi`; config package `…sqlinit.config`.
- Core `module-info` exports/opens `io.github.hantsy.jdbc.sqlinit` — **no change needed** (public types stay in the exported package).
- History status values: `running`, `succeeded`, `failed`.
- Migration filename pattern: `V<version>__<description>.sql` (integer version).
- Config property prefix: `jdbcclient.init.*`.
- History table default: `sqlinit_migration`. Default script location: `classpath:db/migration`. Default separator `;`.
- Use `java.util.logging.Logger` (match existing style).
- Every commit message ends with `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
- Do not touch the untracked `.qoder/` directory.
- Build/test from repo root with the Maven wrapper: `./mvnw -pl init/core test` etc.

---

### Task 1: `DatabasePlatform` enum

**Files:**
- Create: `init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/DatabasePlatform.java`
- Test: `init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/DatabasePlatformTest.java`

**Interfaces:**
- Produces: `DatabasePlatform` enum with members `H2, POSTGRESQL, MYSQL, MSSQL, ORACLE`; methods `String createHistoryTable(String tableName)`, `static DatabasePlatform fromName(String name)`, `static DatabasePlatform detect(String productName, String jdbcUrl) throws SQLException`.

- [ ] **Step 1: Write the failing test**

`init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/DatabasePlatformTest.java`:

```java
package io.github.hantsy.jdbc.sqlinit;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabasePlatformTest {

    @Test
    void resolvesAliases() {
        assertEquals(DatabasePlatform.H2, DatabasePlatform.fromName("h2"));
        assertEquals(DatabasePlatform.POSTGRESQL, DatabasePlatform.fromName("pg"));
        assertEquals(DatabasePlatform.POSTGRESQL, DatabasePlatform.fromName("postgres"));
        assertEquals(DatabasePlatform.POSTGRESQL, DatabasePlatform.fromName("POSTGRESQL"));
        assertEquals(DatabasePlatform.MYSQL, DatabasePlatform.fromName("mysql"));
        assertEquals(DatabasePlatform.MYSQL, DatabasePlatform.fromName("mariadb"));
        assertEquals(DatabasePlatform.MSSQL, DatabasePlatform.fromName("mssql"));
        assertEquals(DatabasePlatform.MSSQL, DatabasePlatform.fromName("sqlserver"));
        assertEquals(DatabasePlatform.ORACLE, DatabasePlatform.fromName("oracle"));
    }

    @Test
    void rejectsAnUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> DatabasePlatform.fromName("nope"));
    }

    @Test
    void detectsByProductName() throws SQLException {
        assertEquals(DatabasePlatform.H2, DatabasePlatform.detect("H2", null));
        assertEquals(DatabasePlatform.POSTGRESQL, DatabasePlatform.detect("PostgreSQL 15", null));
        assertEquals(DatabasePlatform.MYSQL, DatabasePlatform.detect("MariaDB", null));
        assertEquals(DatabasePlatform.MSSQL, DatabasePlatform.detect("Microsoft SQL Server", null));
        assertEquals(DatabasePlatform.ORACLE, DatabasePlatform.detect("Oracle Database", null));
    }

    @Test
    void detectsByJdbcUrl() throws SQLException {
        assertEquals(DatabasePlatform.H2, DatabasePlatform.detect("Something", "jdbc:h2:mem:test"));
        assertEquals(DatabasePlatform.POSTGRESQL, DatabasePlatform.detect("Something", "jdbc:postgresql://localhost/db"));
        assertEquals(DatabasePlatform.MYSQL, DatabasePlatform.detect("Something", "jdbc:mariadb://localhost/db"));
        assertEquals(DatabasePlatform.MSSQL, DatabasePlatform.detect("Something", "jdbc:sqlserver://localhost"));
        assertEquals(DatabasePlatform.ORACLE, DatabasePlatform.detect("Something", "jdbc:oracle:thin:@localhost"));
    }

    @Test
    void failsWhenPlatformIsUnknown() {
        assertThrows(SQLException.class, () -> DatabasePlatform.detect("SomeDB", "jdbc:unknowndb:x"));
    }

    @Test
    void emitsDialectSpecificDdl() {
        assertTrue(DatabasePlatform.ORACLE.createHistoryTable("mig").contains("VARCHAR2"));
        assertTrue(DatabasePlatform.MYSQL.createHistoryTable("mig").contains("DATETIME"));
        assertTrue(DatabasePlatform.MSSQL.createHistoryTable("mig").contains("NVARCHAR"));
        assertTrue(DatabasePlatform.H2.createHistoryTable("mig").contains("version INT PRIMARY KEY"));
        assertTrue(DatabasePlatform.H2.createHistoryTable("my_table").contains("my_table"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl init/core test -Dtest=DatabasePlatformTest`
Expected: BUILD FAILURE (compilation error — `DatabasePlatform` is undefined).

- [ ] **Step 3: Write the implementation**

`init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/DatabasePlatform.java`:

```java
package io.github.hantsy.jdbc.sqlinit;

import java.sql.SQLException;
import java.util.Locale;

/**
 * The database the migration history table is created for.
 *
 * <p>Each member carries the dialect-specific {@code CREATE TABLE} statement for the history table;
 * the only differences across platforms are the type spellings ({@code VARCHAR2} on Oracle,
 * {@code NVARCHAR}/{@code DATETIME2} on SQL Server, {@code DATETIME} on MySQL/MariaDB).</p>
 */
public enum DatabasePlatform {

    H2("CREATE TABLE %s (version INT PRIMARY KEY, description VARCHAR(200) NOT NULL, "
            + "script VARCHAR(500) NOT NULL, status VARCHAR(16) NOT NULL, "
            + "installed_on TIMESTAMP NOT NULL, error_message VARCHAR(1000))"),

    POSTGRESQL("CREATE TABLE %s (version INT PRIMARY KEY, description VARCHAR(200) NOT NULL, "
            + "script VARCHAR(500) NOT NULL, status VARCHAR(16) NOT NULL, "
            + "installed_on TIMESTAMP NOT NULL, error_message VARCHAR(1000))"),

    MYSQL("CREATE TABLE %s (version INT PRIMARY KEY, description VARCHAR(200) NOT NULL, "
            + "script VARCHAR(500) NOT NULL, status VARCHAR(16) NOT NULL, "
            + "installed_on DATETIME NOT NULL, error_message VARCHAR(1000))"),

    MSSQL("CREATE TABLE %s (version INT PRIMARY KEY, description NVARCHAR(200) NOT NULL, "
            + "script NVARCHAR(500) NOT NULL, status NVARCHAR(16) NOT NULL, "
            + "installed_on DATETIME2 NOT NULL, error_message NVARCHAR(1000))"),

    ORACLE("CREATE TABLE %s (version NUMBER(10) PRIMARY KEY, description VARCHAR2(200) NOT NULL, "
            + "script VARCHAR2(500) NOT NULL, status VARCHAR2(16) NOT NULL, "
            + "installed_on TIMESTAMP NOT NULL, error_message VARCHAR2(1000))");

    private final String createTable;

    DatabasePlatform(String createTable) {
        this.createTable = createTable;
    }

    /**
     * Returns the {@code CREATE TABLE} statement for the history table of the given name.
     */
    public String createHistoryTable(String tableName) {
        return String.format(createTable, tableName);
    }

    /**
     * Resolves a configured platform name, accepting common aliases (case-insensitive).
     *
     * @throws IllegalArgumentException if the name is not a known platform
     */
    public static DatabasePlatform fromName(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "h2":
                return H2;
            case "pg":
            case "postgres":
            case "postgresql":
                return POSTGRESQL;
            case "mysql":
            case "mariadb":
                return MYSQL;
            case "mssql":
            case "sqlserver":
            case "sql_server":
                return MSSQL;
            case "oracle":
                return ORACLE;
            default:
                throw new IllegalArgumentException("Unknown database platform: " + name);
        }
    }

    /**
     * Detects the platform from the JDBC product name and, failing that, the JDBC URL.
     *
     * @throws SQLException if the platform cannot be determined
     */
    public static DatabasePlatform detect(String productName, String jdbcUrl) throws SQLException {
        DatabasePlatform detected = detectByProductName(productName);
        if (detected == null && jdbcUrl != null) {
            detected = detectByJdbcUrl(jdbcUrl);
        }
        if (detected == null) {
            throw new SQLException("Cannot detect the database platform from product name '"
                    + productName + "' and JDBC URL '" + jdbcUrl
                    + "'; set jdbcclient.init.platform explicitly");
        }
        return detected;
    }

    private static DatabasePlatform detectByProductName(String productName) {
        if (productName == null) {
            return null;
        }
        String name = productName.toLowerCase(Locale.ROOT);
        if (name.contains("h2")) {
            return H2;
        }
        if (name.contains("postgres")) {
            return POSTGRESQL;
        }
        if (name.contains("mysql") || name.contains("mariadb")) {
            return MYSQL;
        }
        if (name.contains("microsoft") || name.contains("sql server")) {
            return MSSQL;
        }
        if (name.contains("oracle")) {
            return ORACLE;
        }
        return null;
    }

    private static DatabasePlatform detectByJdbcUrl(String jdbcUrl) {
        String url = jdbcUrl.toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:h2:")) {
            return H2;
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return POSTGRESQL;
        }
        if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
            return MYSQL;
        }
        if (url.startsWith("jdbc:sqlserver:")) {
            return MSSQL;
        }
        if (url.startsWith("jdbc:oracle:")) {
            return ORACLE;
        }
        return null;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -pl init/core test -Dtest=DatabasePlatformTest`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/DatabasePlatform.java \
        init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/DatabasePlatformTest.java
git commit -m "feat(sqlinit): add DatabasePlatform with per-platform history DDL

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: `SqlInitConfig` value object

**Files:**
- Modify: `init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/SqlInitConfig.java` (replace whole file)
- Test: `init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/SqlInitConfigTest.java`

**Interfaces:**
- Produces: `SqlInitConfig` with static `defaults()` and `builder()`; accessors `List<String> scriptLocations()`, `String separator()`, `String platform()` (nullable = auto-detect), `String historyTable()`; nested `Builder` with `scriptLocations/separator/platform/historyTable` setters.

- [ ] **Step 1: Write the failing test**

`init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/SqlInitConfigTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl init/core test -Dtest=SqlInitConfigTest`
Expected: BUILD FAILURE (compilation error — `defaults()`, `builder()`, `scriptLocations()`, `platform()`, `historyTable()` do not exist yet).

- [ ] **Step 3: Replace the implementation**

`init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/SqlInitConfig.java` (replace entire file):

```java
package io.github.hantsy.jdbc.sqlinit;

import java.util.List;

/**
 * Immutable configuration for SQL migration initialization.
 */
public final class SqlInitConfig {

    public static final String DEFAULT_SEPARATOR = ";";
    public static final String DEFAULT_HISTORY_TABLE = "sqlinit_migration";
    public static final String DEFAULT_SCRIPT_LOCATION = "classpath:db/migration";

    private final List<String> scriptLocations;
    private final String separator;
    private final String platform;
    private final String historyTable;

    private SqlInitConfig(Builder builder) {
        this.scriptLocations = List.copyOf(builder.scriptLocations);
        this.separator = builder.separator;
        this.platform = builder.platform;
        this.historyTable = builder.historyTable;
    }

    public static SqlInitConfig defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The script locations to scan for versioned migrations (default {@code classpath:db/migration}). */
    public List<String> scriptLocations() {
        return scriptLocations;
    }

    /** The statement separator passed to the script parser (default {@code ;}). */
    public String separator() {
        return separator;
    }

    /** The configured database platform, or {@code null} to auto-detect it from the connection. */
    public String platform() {
        return platform;
    }

    /** The name of the migration history table (default {@code sqlinit_migration}). */
    public String historyTable() {
        return historyTable;
    }

    public static final class Builder {

        private List<String> scriptLocations = List.of(DEFAULT_SCRIPT_LOCATION);
        private String separator = DEFAULT_SEPARATOR;
        private String platform;
        private String historyTable = DEFAULT_HISTORY_TABLE;

        public Builder scriptLocations(List<String> scriptLocations) {
            this.scriptLocations = scriptLocations;
            return this;
        }

        public Builder separator(String separator) {
            this.separator = separator;
            return this;
        }

        public Builder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder historyTable(String historyTable) {
            this.historyTable = historyTable;
            return this;
        }

        public SqlInitConfig build() {
            return new SqlInitConfig(this);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -pl init/core test -Dtest=SqlInitConfigTest`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/SqlInitConfig.java \
        init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/SqlInitConfigTest.java
git commit -m "feat(sqlinit): replace SqlInitConfig with builder-based value object

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: `ScriptLocator` + `Resource`

**Files:**
- Create: `init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/Resource.java`
- Create: `init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/ScriptLocator.java`
- Test: `init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/ScriptLocatorTest.java`

**Interfaces:**
- Consumes: `AntPathMatcher` (existing).
- Produces: `record Resource(String name, URL url)` with `InputStream open()`, `String fileName()`, `Comparable`; `final class ScriptLocator` with `List<Resource> resolve(List<String> locations) throws SQLException`.

- [ ] **Step 1: Write the failing test**

`init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/ScriptLocatorTest.java`:

```java
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

        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, parent)) {
            Thread.currentThread().setContextClassLoader(loader);
            try {
                List<Resource> resources = new ScriptLocator().resolve(List.of("classpath:db/migration"));
                assertEquals(List.of("V1__create.sql", "V2__second.sql"),
                        resources.stream().map(Resource::fileName).toList());
            } finally {
                Thread.currentThread().setContextClassLoader(parent);
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl init/core test -Dtest=ScriptLocatorTest`
Expected: BUILD FAILURE (compilation error — `Resource`, `ScriptLocator` are undefined).

- [ ] **Step 3: Write `Resource`**

`init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/Resource.java`:

```java
package io.github.hantsy.jdbc.sqlinit;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * A resolved SQL script: a display name and an openable location.
 */
record Resource(String name, URL url) implements Comparable<Resource> {

    InputStream open() throws IOException {
        URLConnection connection = url.openConnection();
        if (connection instanceof JarURLConnection jarConnection) {
            // The JDK jar cache would keep the archive open for the lifetime of the JVM.
            jarConnection.setUseCaches(false);
        }
        return connection.getInputStream();
    }

    String fileName() {
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    @Override
    public int compareTo(Resource other) {
        int result = name.compareTo(other.name);
        return result != 0 ? result : url.toExternalForm().compareTo(other.url.toExternalForm());
    }
}
```

- [ ] **Step 4: Write `ScriptLocator`**

`init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/ScriptLocator.java`:

```java
package io.github.hantsy.jdbc.sqlinit;

import java.io.IOException;
import java.net.JarURLConnection;
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
                    resources.add(new Resource(relative, file.toUri().toURL()));
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
            return List.of(new Resource(normalized, file.toUri().toURL()));
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
        String rootPath = wildcardRoot(pattern);
        if (rootPath.isEmpty()) {
            return List.of();
        }
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
                    resources.add(new Resource(relative, file.toUri().toURL()));
                }
            });
        } catch (IOException e) {
            throw new SQLException("Failed to scan the filesystem for SQL scripts at: " + root, e);
        }
        return resources;
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
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -pl init/core test -Dtest=ScriptLocatorTest`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/Resource.java \
        init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/ScriptLocator.java \
        init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/ScriptLocatorTest.java
git commit -m "feat(sqlinit): add ScriptLocator for classpath and filesystem resolution

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: `Migration` + `MigrationHistory`

**Files:**
- Create: `init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/Migration.java`
- Create: `init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/MigrationHistory.java`
- Test: `init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/MigrationHistoryTest.java`

**Interfaces:**
- Consumes: `Resource` (Task 3), `DatabasePlatform` (Task 1).
- Produces: `record Migration(int version, String description, Resource resource)` with `String script()`; `final class MigrationHistory` with constructor `(Connection, DatabasePlatform, String tableName)`, constants `STATUS_RUNNING/STATUS_SUCCEEDED/STATUS_FAILED`, and methods `ensureTable()`, `Map<Integer,String> applied()`, `insertRunning(Migration)`, `markSucceeded(int)`, `markFailed(int, String)` — all `throws SQLException`.

- [ ] **Step 1: Write the failing test**

`init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/MigrationHistoryTest.java`:

```java
package io.github.hantsy.jdbc.sqlinit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationHistoryTest {

    @Test
    void createsTheTableAndTracksTheLifecycle() throws SQLException {
        DataSource dataSource = h2("history");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            MigrationHistory history = new MigrationHistory(connection, DatabasePlatform.H2, "sqlinit_migration");

            history.ensureTable();
            assertTrue(history.applied().isEmpty());

            history.insertRunning(new Migration(1, "create", resource("V1__create.sql")));
            assertEquals(Map.of(1, "running"), history.applied());

            history.markSucceeded(1);
            assertEquals(Map.of(1, "succeeded"), history.applied());

            history.insertRunning(new Migration(2, "seed", resource("V2__seed.sql")));
            history.markFailed(2, "boom");
            assertEquals(Map.of(1, "succeeded", 2, "failed"), history.applied());
        }
    }

    private static Resource resource(String name) throws SQLException {
        try {
            URL url = Path.of("unused-" + name).toUri().toURL();
            return new Resource(name, url);
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    private static DataSource h2(String database) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl init/core test -Dtest=MigrationHistoryTest`
Expected: BUILD FAILURE (compilation error — `Migration`, `MigrationHistory` are undefined).

- [ ] **Step 3: Write `Migration`**

`init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/Migration.java`:

```java
package io.github.hantsy.jdbc.sqlinit;

/**
 * A single versioned migration parsed from a resolved script resource.
 */
record Migration(int version, String description, Resource resource) {

    String script() {
        return resource.fileName();
    }
}
```

- [ ] **Step 4: Write `MigrationHistory`**

`init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/MigrationHistory.java`:

```java
package io.github.hantsy.jdbc.sqlinit;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The migration history table: its dialect-specific DDL and the bookkeeping statements that record
 * a migration's lifecycle ({@code running} -> {@code succeeded}/{@code failed}).
 *
 * <p>Callers are expected to run these statements on a connection in auto-commit mode, so that the
 * bookkeeping commits independently of the migration's own transaction.</p>
 */
final class MigrationHistory {

    static final String STATUS_RUNNING = "running";
    static final String STATUS_SUCCEEDED = "succeeded";
    static final String STATUS_FAILED = "failed";

    private final Connection connection;
    private final DatabasePlatform platform;
    private final String tableName;

    MigrationHistory(Connection connection, DatabasePlatform platform, String tableName) {
        this.connection = connection;
        this.platform = platform;
        this.tableName = tableName;
    }

    void ensureTable() throws SQLException {
        if (!tableExists()) {
            try (var statement = connection.createStatement()) {
                statement.execute(platform.createHistoryTable(tableName));
            }
        }
    }

    Map<Integer, String> applied() throws SQLException {
        Map<Integer, String> applied = new LinkedHashMap<>();
        try (var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT version, status FROM " + tableName + " ORDER BY version")) {
            while (rows.next()) {
                applied.put(rows.getInt(1), rows.getString(2));
            }
        }
        return applied;
    }

    void insertRunning(Migration migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + tableName
                        + " (version, description, script, status, installed_on) VALUES (?, ?, ?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.setString(3, migration.script());
            statement.setString(4, STATUS_RUNNING);
            statement.setTimestamp(5, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    void markSucceeded(int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + tableName + " SET status = ? WHERE version = ?")) {
            statement.setString(1, STATUS_SUCCEEDED);
            statement.setInt(2, version);
            statement.executeUpdate();
        }
    }

    void markFailed(int version, String errorMessage) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + tableName + " SET status = ?, error_message = ? WHERE version = ?")) {
            statement.setString(1, STATUS_FAILED);
            statement.setString(2, errorMessage);
            statement.setInt(3, version);
            statement.executeUpdate();
        }
    }

    private boolean tableExists() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -pl init/core test -Dtest=MigrationHistoryTest`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/Migration.java \
        init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/MigrationHistory.java \
        init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/MigrationHistoryTest.java
git commit -m "feat(sqlinit): add MigrationHistory for version bookkeeping

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: `SqlMigrator`

**Files:**
- Create: `init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/SqlMigrator.java`
- Test: `init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/SqlMigratorTest.java`
- Create: `init/core/src/test/resources/db/failing-migration/V1__fail.sql`
- Create: `init/core/src/test/resources/db/duplicates/V1__first.sql`
- Create: `init/core/src/test/resources/db/duplicates/V1__second.sql`

**Interfaces:**
- Consumes: `SqlInitConfig` (Task 2), `ScriptLocator`/`Resource` (Task 3), `Migration`/`MigrationHistory` (Task 4), `SqlScriptParser`/`DatabasePlatform`.
- Produces: `public final class SqlMigrator` with constructors `(DataSource)` and `(DataSource, SqlInitConfig)`; `public void migrate() throws SQLException`.

- [ ] **Step 1: Create the failing-migration test resources**

`init/core/src/test/resources/db/failing-migration/V1__fail.sql`:

```sql
CREATE TABLE ok_table (id INT);
INSERT INTO table_that_does_not_exist (id) VALUES (1);
```

`init/core/src/test/resources/db/duplicates/V1__first.sql`:

```sql
CREATE TABLE a (id INT);
```

`init/core/src/test/resources/db/duplicates/V1__second.sql`:

```sql
CREATE TABLE b (id INT);
```

- [ ] **Step 2: Write the failing test**

`init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/SqlMigratorTest.java`:

```java
package io.github.hantsy.jdbc.sqlinit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlMigratorTest {

    @Test
    void appliesVersionedMigrationsInOrder() throws SQLException {
        DataSource dataSource = h2("order");

        new SqlMigrator(dataSource).migrate();

        assertEquals(List.of("V2", "V3"), query(dataSource, "SELECT script FROM applied_scripts ORDER BY seq"));
        assertEquals(List.of("1", "2", "3"),
                query(dataSource, "SELECT version FROM sqlinit_migration ORDER BY version"));
    }

    @Test
    void skipsAlreadyAppliedMigrations() throws SQLException {
        DataSource dataSource = h2("skip");
        SqlMigrator migrator = new SqlMigrator(dataSource);

        migrator.migrate();
        migrator.migrate();

        assertEquals(List.of("1", "2", "3"),
                query(dataSource, "SELECT version FROM sqlinit_migration ORDER BY version"));
        assertEquals(List.of("V2", "V3"), query(dataSource, "SELECT script FROM applied_scripts ORDER BY seq"));
    }

    @Test
    void recordsAFailureAndRollsBackTheScript() throws SQLException {
        DataSource dataSource = h2("failed");
        SqlInitConfig config = SqlInitConfig.builder()
                .scriptLocations(List.of("classpath:db/failing-migration"))
                .build();

        assertThrows(SQLException.class, () -> new SqlMigrator(dataSource, config).migrate());

        assertEquals(List.of("failed"), query(dataSource, "SELECT status FROM sqlinit_migration WHERE version = 1"));
        assertEquals(List.of("0"), query(dataSource,
                "SELECT CAST(COUNT(*) AS VARCHAR) FROM information_schema.tables WHERE table_name = 'OK_TABLE'"));
    }

    @Test
    void failsWhenALeftoverRunningRowExists() throws SQLException {
        DataSource dataSource = h2("running");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sqlinit_migration (version INT PRIMARY KEY, description VARCHAR(200), "
                    + "script VARCHAR(500), status VARCHAR(16), installed_on TIMESTAMP, error_message VARCHAR(1000))");
            statement.execute("INSERT INTO sqlinit_migration (version, description, script, status, installed_on) "
                    + "VALUES (1, 'x', 'V1__x.sql', 'running', CURRENT_TIMESTAMP)");
        }

        SQLException e = assertThrows(SQLException.class, () -> new SqlMigrator(dataSource).migrate());

        assertTrue(e.getMessage().contains("running"));
    }

    @Test
    void rejectsDuplicateMigrationVersions() {
        DataSource dataSource = h2("duplicate");
        SqlInitConfig config = SqlInitConfig.builder()
                .scriptLocations(List.of("classpath:db/duplicates"))
                .build();

        SQLException e = assertThrows(SQLException.class, () -> new SqlMigrator(dataSource, config).migrate());

        assertTrue(e.getMessage().contains("Duplicate migration version"));
    }

    private static DataSource h2(String database) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static List<String> query(DataSource dataSource, String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows.add(resultSet.getString(1));
            }
        }
        return rows;
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw -pl init/core test -Dtest=SqlMigratorTest`
Expected: BUILD FAILURE (compilation error — `SqlMigrator` is undefined).

- [ ] **Step 4: Write `SqlMigrator`**

`init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/SqlMigrator.java`:

```java
package io.github.hantsy.jdbc.sqlinit;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * Applies versioned SQL migrations to a {@link DataSource}, tracking them in a history table.
 *
 * <p>Scripts are resolved from the configured {@link SqlInitConfig#scriptLocations()} and must be
 * named {@code V<version>__<description>.sql}. Each pending migration is claimed by inserting a
 * {@code running} row (atomic via the {@code version} primary key), executed in its own
 * transaction, then marked {@code succeeded} or {@code failed}.</p>
 */
public final class SqlMigrator {

    private static final Logger LOGGER = Logger.getLogger(SqlMigrator.class.getName());
    private static final Pattern MIGRATION_NAME = Pattern.compile("V(\\d+)__(.+)\\.sql");

    private final DataSource dataSource;
    private final SqlInitConfig config;

    public SqlMigrator(DataSource dataSource) {
        this(dataSource, SqlInitConfig.defaults());
    }

    public SqlMigrator(DataSource dataSource, SqlInitConfig config) {
        this.dataSource = dataSource;
        this.config = config;
    }

    /**
     * Resolves the configured scripts and applies the migrations that have not run yet.
     *
     * @throws SQLException if a script cannot be located, read, parsed, or executed, or if the
     *                      history table holds a {@code failed} or leftover {@code running} row
     */
    public void migrate() throws SQLException {
        List<Migration> migrations = resolve();
        if (migrations.isEmpty()) {
            LOGGER.info("No SQL migrations resolved, skipping database migration");
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            MigrationHistory history = new MigrationHistory(connection, platform(connection), config.historyTable());
            history.ensureTable();

            Map<Integer, String> applied = history.applied();
            assertNoBrokenHistory(applied);

            int appliedCount = 0;
            for (Migration migration : migrations) {
                if (applied.containsKey(migration.version())) {
                    LOGGER.fine(() -> "Skipping already applied migration: " + migration.script());
                    continue;
                }
                runMigration(connection, history, migration);
                appliedCount++;
            }
            LOGGER.info(() -> "Database migration completed, applied " + appliedCount + " migration(s)");
        }
    }

    private List<Migration> resolve() throws SQLException {
        List<Resource> resources = new ScriptLocator().resolve(config.scriptLocations());
        List<Migration> migrations = new ArrayList<>();
        for (Resource resource : resources) {
            Matcher matcher = MIGRATION_NAME.matcher(resource.fileName());
            if (!matcher.matches()) {
                LOGGER.warning(() -> "Ignoring SQL script that does not match V<version>__<description>.sql: "
                        + resource.fileName());
                continue;
            }
            int version = Integer.parseInt(matcher.group(1));
            String description = matcher.group(2);
            migrations.add(new Migration(version, description, resource));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        for (int i = 1; i < migrations.size(); i++) {
            if (migrations.get(i).version() == migrations.get(i - 1).version()) {
                throw new SQLException("Duplicate migration version " + migrations.get(i).version()
                        + ": " + migrations.get(i - 1).script() + " and " + migrations.get(i).script());
            }
        }
        return migrations;
    }

    private DatabasePlatform platform(Connection connection) throws SQLException {
        if (config.platform() != null) {
            try {
                return DatabasePlatform.fromName(config.platform());
            } catch (IllegalArgumentException e) {
                throw new SQLException(e.getMessage(), e);
            }
        }
        return DatabasePlatform.detect(connection.getMetaData().getDatabaseProductName(),
                connection.getMetaData().getURL());
    }

    private void assertNoBrokenHistory(Map<Integer, String> applied) throws SQLException {
        for (Map.Entry<Integer, String> entry : applied.entrySet()) {
            if (!MigrationHistory.STATUS_SUCCEEDED.equals(entry.getValue())) {
                throw new SQLException("Migration V" + entry.getKey() + " is in state '" + entry.getValue()
                        + "'; fix or remove the " + config.historyTable() + " row before restarting");
            }
        }
    }

    private void runMigration(Connection connection, MigrationHistory history, Migration migration)
            throws SQLException {
        LOGGER.info(() -> "Applying migration: " + migration.script());

        connection.setAutoCommit(true);
        history.insertRunning(connection, migration);

        connection.setAutoCommit(false);
        SQLException failure = null;
        try {
            executeScript(connection, migration);
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly(connection, e);
            failure = e;
        }

        connection.setAutoCommit(true);

        if (failure != null) {
            try {
                history.markFailed(connection, migration.version(), truncate(failure.getMessage()));
            } catch (SQLException markFailure) {
                failure.addSuppressed(markFailure);
            }
            throw failure;
        }
        history.markSucceeded(connection, migration.version());
    }

    private void executeScript(Connection connection, Migration migration) throws SQLException {
        List<String> statements;
        try (InputStream in = migration.resource().open();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            statements = SqlScriptParser.parse(reader, config.separator());
        } catch (IOException e) {
            throw new SQLException("Failed to read SQL script: " + migration.script(), e);
        }
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new SQLException("Failed to execute SQL script: " + migration.script(),
                    e.getSQLState(), e.getErrorCode(), e);
        }
    }

    private void rollbackQuietly(Connection connection, SQLException failure) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            failure.addSuppressed(e);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -pl init/core test -Dtest=SqlMigratorTest`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/SqlMigrator.java \
        init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/SqlMigratorTest.java \
        init/core/src/test/resources/db/failing-migration/V1__fail.sql \
        init/core/src/test/resources/db/duplicates/V1__first.sql \
        init/core/src/test/resources/db/duplicates/V1__second.sql
git commit -m "feat(sqlinit): add SqlMigrator versioned migration runner

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: Rework `config` producer

**Files:**
- Modify: `init/config/src/main/java/io/github/hantsy/jdbc/sqlinit/config/SqlInitConfigProducer.java` (replace whole file)
- Modify: `init/config/src/test/java/io/github/hantsy/jdbc/sqlinit/config/SqlInitConfigProducerTest.java` (replace whole file)
- Modify: `init/config/src/test/resources/META-INF/microprofile-config.properties` (replace whole file)

**Interfaces:**
- Consumes: `SqlInitConfig` builder (Task 2).
- Produces: unchanged bean type `SqlInitConfig`; new property names.

- [ ] **Step 1: Update the test resources**

`init/config/src/test/resources/META-INF/microprofile-config.properties`:

```properties
jdbcclient.init.separator=/
jdbcclient.init.script-locations=classpath:db/migration,filesystem:/opt/sql
jdbcclient.init.platform=h2
jdbcclient.init.history-table=custom_migrations
```

- [ ] **Step 2: Update the test**

`init/config/src/test/java/io/github/hantsy/jdbc/sqlinit/config/SqlInitConfigProducerTest.java`:

```java
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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw -pl init/config -am test -Dtest=SqlInitConfigProducerTest`
Expected: BUILD FAILURE (the producer still references removed `schemaLocations`/`dataLocations`, or the assertions fail).

- [ ] **Step 4: Replace the producer**

`init/config/src/main/java/io/github/hantsy/jdbc/sqlinit/config/SqlInitConfigProducer.java`:

```java
package io.github.hantsy.jdbc.sqlinit.config;

import io.github.hantsy.jdbc.sqlinit.SqlInitConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Produces the {@code @ApplicationScoped} {@link SqlInitConfig} bean from the
 * {@code jdbcclient.init.*} MicroProfile Config properties.
 */
@ApplicationScoped
public class SqlInitConfigProducer {

    @Inject
    @ConfigProperty(name = "jdbcclient.init.script-locations", defaultValue = "classpath:db/migration")
    private List<String> scriptLocations;

    @Inject
    @ConfigProperty(name = "jdbcclient.init.separator", defaultValue = ";")
    private String separator;

    @Inject
    @ConfigProperty(name = "jdbcclient.init.platform", defaultValue = "")
    private String platform;

    @Inject
    @ConfigProperty(name = "jdbcclient.init.history-table", defaultValue = "sqlinit_migration")
    private String historyTable;

    @Produces
    @ApplicationScoped
    public SqlInitConfig produce() {
        SqlInitConfig.Builder builder = SqlInitConfig.builder()
                .scriptLocations(scriptLocations)
                .separator(separator)
                .historyTable(historyTable);
        if (platform != null && !platform.isBlank()) {
            builder.platform(platform);
        }
        return builder.build();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -pl init/config -am test -Dtest=SqlInitConfigProducerTest`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add init/config/src/main/java/io/github/hantsy/jdbc/sqlinit/config/SqlInitConfigProducer.java \
        init/config/src/test/java/io/github/hantsy/jdbc/sqlinit/config/SqlInitConfigProducerTest.java \
        init/config/src/test/resources/META-INF/microprofile-config.properties
git commit -m "feat(sqlinit): map script-locations and platform config properties

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: Rework `cdi` extension + tests

**Files:**
- Modify: `init/cdi/src/main/java/io/github/hantsy/jdbc/sqlinit/cdi/SqlInitExtension.java`
- Delete: `init/cdi/src/test/resources/schema.sql`
- Delete: `init/cdi/src/test/resources/data.sql`
- Create: `init/cdi/src/test/resources/db/migration/V1__create_engineers.sql`
- Create: `init/cdi/src/test/resources/db/migration/V2__seed_engineers.sql`
- Test (no assertion change): `SqlInitExtensionTest`, `SqlInitQualifiedDataSourceTest`, `SqlInitStartupTest` remain valid — verify they pass.

**Interfaces:**
- Consumes: `SqlMigrator`, `SqlInitConfig.defaults()` (Task 2/5).

- [ ] **Step 1: Add migration test resources**

`init/cdi/src/test/resources/db/migration/V1__create_engineers.sql`:

```sql
CREATE TABLE engineers (
    id   INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);
```

`init/cdi/src/test/resources/db/migration/V2__seed_engineers.sql`:

```sql
INSERT INTO engineers (id, name) VALUES (1, 'Ada');
INSERT INTO engineers (id, name) VALUES (2, 'Grace');
```

- [ ] **Step 2: Delete the old schema/data resources**

Run:

```bash
git rm init/cdi/src/test/resources/schema.sql init/cdi/src/test/resources/data.sql
```

- [ ] **Step 3: Update `SqlInitExtension`**

`init/cdi/src/main/java/io/github/hantsy/jdbc/sqlinit/cdi/SqlInitExtension.java` — change the import and the `run()` body:

Change:
```java
import io.github.hantsy.jdbc.sqlinit.SqlScriptPopulator;
```
to:
```java
import io.github.hantsy.jdbc.sqlinit.SqlMigrator;
```

Change the `run()` method to:
```java
    public void run() throws SQLException {
        CDI<Object> cdi = CDI.current();
        DataSource dataSource = dataSource(cdi);
        if (dataSource == null) {
            LOGGER.warning("No DataSource bean found, skipping SQL script initialization");
            return;
        }
        Instance<SqlInitConfig> configs = cdi.select(SqlInitConfig.class);
        SqlInitConfig config = configs.isResolvable() ? configs.get() : SqlInitConfig.defaults();
        new SqlMigrator(dataSource, config).migrate();
    }
```

Also update the class javadoc's reference to "SQL scripts" if it names `SqlScriptPopulator` — it does not, so no other change is needed.

- [ ] **Step 4: Run the CDI tests**

Run: `./mvnw -pl init/cdi -am test`
Expected: BUILD SUCCESS (all three CDI test classes pass; assertions `["Ada", "Grace"]` are unchanged).

- [ ] **Step 5: Commit**

```bash
git add init/cdi/src/main/java/io/github/hantsy/jdbc/sqlinit/cdi/SqlInitExtension.java \
        init/cdi/src/test/resources/db/migration/V1__create_engineers.sql \
        init/cdi/src/test/resources/db/migration/V2__seed_engineers.sql
git commit -m "refactor(sqlinit): migrate cdi extension to SqlMigrator

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: Remove the populator and update docs

**Files:**
- Delete: `init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/SqlScriptPopulator.java`
- Delete: `init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/SqlScriptPopulatorTest.java`
- Delete: `init/core/src/test/resources/schema.sql`
- Delete: `init/core/src/test/resources/data.sql`
- Delete: `init/core/src/test/resources/db/applied-first.sql`
- Delete: `init/core/src/test/resources/db/failing.sql`
- Delete: `init/core/src/test/resources/db/slash/scripts.sql`
- Modify: `docs/sql-init.md` (replace whole file)

- [ ] **Step 1: Remove the obsolete source and test resources**

Run:

```bash
git rm init/core/src/main/java/io/github/hantsy/jdbc/sqlinit/SqlScriptPopulator.java \
       init/core/src/test/java/io/github/hantsy/jdbc/sqlinit/SqlScriptPopulatorTest.java \
       init/core/src/test/resources/schema.sql \
       init/core/src/test/resources/data.sql \
       init/core/src/test/resources/db/applied-first.sql \
       init/core/src/test/resources/db/failing.sql \
       init/core/src/test/resources/db/slash/scripts.sql
```

- [ ] **Step 2: Run the whole `init` build**

Run: `./mvnw -pl init -am test`
Expected: BUILD SUCCESS (core + config + cdi tests all green; `module-info` needs no change because the public types remain in the exported package).

- [ ] **Step 3: Rewrite the documentation**

Replace `docs/sql-init.md` with:

```markdown
# SQL Initialization

SQL initialization applies versioned migrations to a database when an application starts. It ships as three
artifacts that mirror the `jdbc-client` modules, so an application can take only the parts it needs.

| Artifact                     | Contents                                                                  |
|------------------------------|---------------------------------------------------------------------------|
| `jdbc-client-sql-init-core`  | `SqlMigrator`, `SqlInitConfig`, `DatabasePlatform`, `SqlScriptParser`. JDK only. |
| `jdbc-client-sql-init-cdi`   | The `@SqlInit` qualifier and a portable CDI extension that runs at startup. |
| `jdbc-client-sql-init-config`| `SqlInitConfig` produced from `jdbcclient.init.*` MicroProfile Config values. |

```xml
<dependency>
    <groupId>io.github.hantsy.jdbc</groupId>
    <artifactId>jdbc-client-sql-init-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

The `cdi` and `config` artifacts pull in the core one.

## Running from Java SE

`SqlMigrator` needs nothing but a `DataSource`:

```java
new SqlMigrator(dataSource).migrate();
```

That scans the default location `classpath:db/migration` for scripts named `V<version>__<description>.sql`
and applies the ones that have not run yet. Pass an explicit `SqlInitConfig` to change any of it:

```java
SqlInitConfig config = SqlInitConfig.builder()
        .scriptLocations(List.of("classpath:db/migration", "filesystem:/opt/sql/migration"))
        .platform("postgresql")
        .build();
new SqlMigrator(dataSource, config).migrate();
```

`migrate()` declares `throws SQLException`, so a Java SE caller decides whether a failed migration is fatal.

## Running at CDI startup

The `cdi` artifact registers `SqlInitExtension` through
`META-INF/services/jakarta.enterprise.inject.spi.Extension`. Adding the jar to the deployment is enough: the
extension observes the CDI `Startup` event and runs the migrations. When an application declares several data
sources, qualify the one that should be initialized with `@SqlInit`; otherwise the default unqualified
`DataSource` bean is used. If neither exists, initialization is skipped with a warning. Any migration failure is
rethrown as an `IllegalStateException`, which aborts startup.

## Migration files

A migration is a SQL script whose file name is `V<version>__<description>.sql`, for example
`V1__create_users.sql`, `V2__add_index.sql`. `version` is an integer and determines the order in which scripts
run; a duplicate version across scripts fails the run. Files in a scanned directory that do not match this
pattern are ignored. Each script runs exactly once, in its own transaction, tracked in the history table.

## Configuration

| Property                          |               Default | Description |
|-----------------------------------|----------------------:|-------------|
| `jdbcclient.init.script-locations`| `classpath:db/migration` | Comma-separated locations to scan. |
| `jdbcclient.init.separator`       |                   `;` | Character sequence that terminates a statement. |
| `jdbcclient.init.platform`        | *(auto-detected)*     | `h2`, `postgresql` (or `pg`), `mysql` (or `mariadb`), `mssql`, `oracle`. |
| `jdbcclient.init.history-table`   | `sqlinit_migration`   | Name of the migration history table. |

## Location syntax

A location is `classpath:` (resolved through the context classloader only), `filesystem:`, or, without a prefix,
classpath. Each accepts a literal file, a directory (scanned recursively for `*.sql`), or an Ant-style pattern
(`**` matches any number of path segments, `*` one segment, `?` one character):

```properties
jdbcclient.init.script-locations=classpath:db/migration,filesystem:/opt/sql/migration
```

Scripts are read as UTF-8. Scanning inside an archive relies on that archive carrying directory entries for the
scanned path, which is what the `jar` tool and the Maven and Gradle jar plugins produce.

## Script format

The parser tracks quoting and comments, so a separator inside any of them does not end a statement, and honours
`DELIMITER`, which is what MySQL and MariaDB scripts use to wrap a stored-procedure body:

```sql
DELIMITER //
CREATE PROCEDURE reset_counters()
BEGIN
    UPDATE counters SET value = 0;
END//
DELIMITER ;
```

## History table and failures

Each applied migration is recorded in the history table with a `status` of `running`, `succeeded`, or `failed`.
A migration is claimed by inserting a `running` row, executed in its own transaction, then marked `succeeded`.
If a script fails, its transaction is rolled back and the row is marked `failed` with the error message; a
`failed` or leftover `running` row stops the next startup, because re-running against partial state is unsafe.
Engines that commit implicitly on DDL (MySQL and MariaDB among them) cannot roll back a schema script, so treat
such scripts as forward-only there.

For query execution once the database is initialized, continue with [querying and result mapping](querying.md).
```

- [ ] **Step 4: Run the full build to confirm nothing is broken**

Run: `./mvnw -pl init -am test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add docs/sql-init.md
git commit -m "docs(sqlinit): document the versioned migration runner

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Self-Review Notes

- **Spec coverage:** `script-locations` (Task 2/6), history table with atomic `running`→`succeeded`/`failed`
  (Tasks 4/5), `classpath:`/`filesystem:` resolution (Task 3), `platform` DDL (Task 1), versioned-only scope
  (Task 5), docs (Task 8). All four improvement requests are covered.
- **Type consistency:** `Resource.fileName()`, `Migration.script()`, `MigrationHistory.insertRunning(Migration)`,
  `SqlInitConfig.defaults()/builder()`, `DatabasePlatform.fromName()/detect()/createHistoryTable()` are used
  identically across tasks.
- **`readme.txt`** under `db/migration` is intentionally left in place: directory scanning filters on `*.sql`, so
  it is excluded, matching the original intent of that fixture.
