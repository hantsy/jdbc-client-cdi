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
