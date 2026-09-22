# Configuration

Configuration defines the default behavior for SQL placeholder rewriting, query timeouts, and fetch-size hints. These defaults keep database access consistent throughout an application while still allowing individual statements to override behavior when needed.

## Programmatic configuration

The `core` module provides a `JdbcConfig` class for setting the default SQL placeholder, query timeout, and fetch size.

A `JdbcConfig` instance can be created programmatically:

```java
JdbcConfig config = new JdbcConfig(...);
```

Then apply it when constructing a `JdbcClient`:

```java
JdbcClient client = new JdbcClient(dataSource, converters, config);
```

The builder API flattens these settings into fine-grained methods:

```java
JdbcClient client = JdbcClient.builder(dataSource)
        .placeholder("$")
        .queryTimeout(30)
        .fetchSize(100)
        .build();
```

## Declarative configuration in Jakarta EE/CDI

In a Jakarta EE/CDI environment, the optional `jdbc-client-config` module can populate this configuration from MicroProfile Config properties.

| Property | Default | Description |
| --- | ---: | --- |
| `jdbcclient.placeholder` | `?` | Placeholder used when rewriting named parameters such as `:name`. |
| `jdbcclient.query-timeout` | `0` | Default query timeout in seconds. `0` means no timeout. |
| `jdbcclient.fetch-size` | `0` | Default fetch-size hint. `0` uses the driver default. |

Named parameters are rewritten according to the configured placeholder:

| Placeholder | Rewritten SQL | Typical database |
| --- | --- | --- |
| `?` | `?` | MySQL and standard JDBC |
| `$` | `$1`, `$2`, ... | H2/PostgreSQL |
| `:` | `:1`, `:2`, ... | Oracle |
| `@` | `@name` | SQL Server |

When `jdbcclient.placeholder=$`, the following SQL:

```sql
SELECT id FROM engineers WHERE id = :id AND active = :active
```

is rewritten before it is sent to the driver as:

```sql
SELECT id FROM engineers WHERE id = $1 AND active = $2
```

## Overriding configuration for a query

The `queryTimeout(...)` and `fetchSize(...)` methods on an individual SQL specification override the client defaults for that statement.

```java
List<DevSummary> devs = client
        .sql("SELECT id, dev_name FROM engineers")
        .fetchSize(100)
        .queryTimeout(30)
        .query(DevSummary.class)
        .list();
```

In practice, these defaults are useful for keeping database behavior consistent across an application while still allowing particular queries to opt into stricter or more permissive settings when needed. The same pattern applies whether the client is configured programmatically or through MicroProfile Config in a Jakarta EE environment.

For query execution and mapping behavior, continue with [querying and result mapping](querying.md).