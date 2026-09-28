# SQL Initialization

SQL initialization applies schema and data scripts to a database when an application starts. It ships as three
artifacts that mirror the `jdbc-client` modules, so an application can take only the parts it needs.

| Artifact                     | Contents                                                                     |
|------------------------------|------------------------------------------------------------------------------|
| `jdbc-client-sql-init-core`  | `SqlScriptPopulator`, `SqlScriptParser`, `SqlInitConfig`. JDK only.           |
| `jdbc-client-sql-init-cdi`   | The `@SqlInit` qualifier and a portable CDI extension that runs at startup.   |
| `jdbc-client-sql-init-config`| `SqlInitConfig` produced from `jdbcclient.init.*` MicroProfile Config values. |

```xml

<dependency>
    <groupId>io.github.hantsy.jdbc</groupId>
    <artifactId>jdbc-client-sql-init-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

The `cdi` and `config` artifacts pull in the core one, so a Jakarta EE application usually declares only those two.

## Running from Java SE

`SqlScriptPopulator` needs nothing but a `DataSource`:

```java
new SqlScriptPopulator(dataSource).populate();
```

That applies `/schema.sql` and then `/data.sql` from the classpath, splitting statements on `;`. Pass an explicit
`SqlInitConfig` to change any of it:

```java
SqlInitConfig config = new SqlInitConfig("/", List.of("/db/schema.sql"), List.of("classpath*:/db/data/**/*.sql"));
new SqlScriptPopulator(dataSource, config).populate();
```

`populate()` declares `throws SQLException`, so a Java SE caller decides whether a failed initialization is fatal.

## Running at CDI startup

The `cdi` artifact registers `SqlInitExtension` through
`META-INF/services/jakarta.enterprise.inject.spi.Extension`. Adding the jar to the deployment is enough: the extension
observes the CDI `Startup` event and applies the scripts, with no bean-discovery configuration and no annotation
required in application code.

When an application declares several data sources, qualify the one that should be initialized:

```java
@Produces
@ApplicationScoped
@SqlInit
public DataSource auditDataSource() {
    return ...;
}
```

Resolution prefers a `@SqlInit` qualified `DataSource` and otherwise falls back to the default unqualified
`DataSource` bean. If neither exists, initialization is skipped with a warning, because the extension is active as soon
as the jar is on the classpath and an application may not use it at all. Any script failure is rethrown as an
`IllegalStateException`, which aborts startup.

The extension runs on every application start, so scripts should be safe to re-apply — `CREATE TABLE IF NOT EXISTS`,
`MERGE`, or a `DELETE` ahead of the `INSERT`s. Depending on the artifact without using it is harmless: with no
`DataSource`, or with no `/schema.sql` or `/data.sql` on the classpath, nothing is applied.

## Configuration

The optional `config` artifact produces the `SqlInitConfig` bean from MicroProfile Config properties. Without it, the
CDI extension uses `SqlInitConfig.DEFAULT`.

| Property                          |       Default | Description                                            |
|-----------------------------------|--------------:|--------------------------------------------------------|
| `jdbcclient.init.separator`       |           `;` | Character sequence that terminates a statement.        |
| `jdbcclient.init.schema-locations`|  `/schema.sql` | Comma-separated classpath locations, applied first.    |
| `jdbcclient.init.data-locations`  |   `/data.sql` | Comma-separated classpath locations, applied second.   |

```properties
jdbcclient.init.separator=;
jdbcclient.init.schema-locations=classpath*:/db/schema/**/*.sql
jdbcclient.init.data-locations=/db/seed.sql,/db/fixtures.sql
```

Schema locations are resolved and applied before data locations. Within one location, scripts run in alphabetical order
of their resource path, so `V1__create.sql` runs before `V2__seed.sql` and before `nested/V3__audit.sql`.

## Location syntax

Every location is a classpath location:

| Form                            | Meaning                                                              |
|---------------------------------|----------------------------------------------------------------------|
| `/schema.sql`                   | A literal resource, taken from the first classpath root that has it.  |
| `classpath:/db/schema.sql`      | The same, with the prefix spelled out.                                |
| `classpath*:/db/migration/**/*.sql` | Every match across every classpath root.                          |

Wildcard locations use Ant-style patterns: `**` matches any number of path segments, `*` matches any run of characters
inside one segment, and `?` matches a single character.

```properties
# every .sql file under /db/migration, at any depth, in any jar or classes directory
jdbcclient.init.schema-locations=classpath*:/db/migration/**/*.sql
```

Scripts are read as UTF-8. Scanning inside an archive relies on that archive carrying directory entries for the scanned
path, which is what the `jar` tool and the Maven and Gradle jar plugins produce.

## Script format

The parser tracks quoting and comments, so a separator inside any of them does not end a statement:

```sql
-- a line comment
# also a line comment
/* a block comment, /* which may nest */ and keeps going */
INSERT INTO engineers (name) VALUES ('a;b');   -- the ; inside the literal is not a separator
INSERT INTO engineers (name) VALUES ('it''s');
```

A trailing statement without a separator is still applied, and a script that contains only blanks and comments applies
nothing. An unbalanced quote or an unterminated block comment fails the initialization rather than silently running a
truncated statement.

`DELIMITER` is honoured, which is what MySQL and MariaDB scripts use to wrap a stored-procedure body:

```sql
DELIMITER //
CREATE PROCEDURE reset_counters()
BEGIN
    UPDATE counters SET value = 0;
    UPDATE audit_log SET reset_at = CURRENT_TIMESTAMP;
END//
DELIMITER ;
```

## Transactions and failures

All statements from all resolved scripts run inside a single transaction. The first failure rolls back everything
already applied and is rethrown, so a half-initialized database is not left behind.

The rollback covers whatever the engine lets a transaction cover. Engines that commit implicitly on DDL, MySQL and
MariaDB among them, cannot roll back a schema script that has already been applied, so a failure part-way through
initialization can leave the schema in place on those databases. Treat schema scripts as forward-only there, and keep
data that must be atomic in the data scripts.

A literal location that resolves to nothing fails the initialization, which catches a typo in a configured path. The
built-in `/schema.sql` and `/data.sql` defaults are the exception: they are skipped quietly when absent, so adding the
dependency without any scripts is harmless. A wildcard location that matches nothing logs a warning and is skipped.

For query execution once the database is initialized, continue with [querying and result mapping](querying.md).
