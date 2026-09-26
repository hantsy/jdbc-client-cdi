# Quickstart

The fastest path to using the library is to construct a `JdbcClient` from a `DataSource` and start composing fluent SQL
with strongly typed results.

## Creating `JdbcClient`

After `jdbc-client-core` has been added to the project dependencies, a `JdbcClient` can be created directly from a
`DataSource`:

```java
import io.github.hantsy.jdbc.JdbcClient;

import javax.sql.DataSource;

DataSource dataSource = ...; // obtain a DataSource from the application or runtime

JdbcClient client = new JdbcClient(dataSource);
```

The builder API also supports explicit configuration:

```java
JdbcClient client = JdbcClient.builder(dataSource)
        .placeholder("?")
        .queryTimeout(30)
        .fetchSize(100)
        .converters(registry)
        .build();
```

See [configuration](configuration.md) for the full list of available defaults and options.

In a Jakarta EE/CDI environment, CDI can create and inject the client. Add `jdbc-client-cdi` and `jdbc-client-config`,
then expose a `DataSource` as a bean:

```java

@ApplicationScoped
public class TestDataSourceProducer {

    @Resource(lookup = "java:comp/MyDS")
    private DataSource dataSource;

    @Produces
    @ApplicationScoped
    public DataSource expose() {
        return dataSource;
    }
}
```

The `JdbcClient` is then available for injection:

```java

@Inject
JdbcClient client;
```

## Executing queries

Once the client is available, the fluent SQL API can be used for both reading and writing data. The library supports
both named and positional parameters in a single SQL statement.

Use named parameters with `param(...)`:

```java
List<DevSummary> devs = client
        .sql("SELECT id, dev_name FROM engineers WHERE id = :id")
        .param("id", 1L)
        .query(DevSummary.class)
        .list();
```

The client also accepts positional parameters:

```java
List<DevSummary> devs = client
        .sql("SELECT id, dev_name FROM engineers WHERE id = ?")
        .param(1L)
        .query(DevSummary.class)
        .list();
```

A single SQL specification cannot combine named and positional parameters.

See [querying and mapping](querying.md) for more examples of result mapping, row mappers, and streaming.
