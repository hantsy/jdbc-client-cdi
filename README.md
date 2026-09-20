# Fluent JDBC Client for Jakarta EE/CDI

A lightweight, framework-agnostic fluent JDBC client for Jakarta EE / CDI applications. It mirrors the
developer experience of Spring's `JdbcClient` while remaining 100% free of Spring dependencies.

## Requirements

- JDK 21 (the library targets Java 21).
- A Jakarta EE 11 / CDI compatible application server for the `cdi` and `config` modules, such as GlassFish 8 or WildFly 41.
- The `core` module alone has no container requirement — it only needs a `javax.sql.DataSource` (JDK).

The project is split into three library modules plus an integration-test module:

| Module              | Artifact                        | Description                                                                                                                |
|---------------------|---------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `core`              | `jdbc-client-core`              | The plain `JdbcClient` and supporting types. Depends only on `javax.sql.DataSource` (JDK). No CDI, no MicroProfile Config. |
| `config`            | `jdbc-client-config`            | A MicroProfile Config integration that produces a `JdbcConfig` bean.                                                       |
| `cdi`               | `jdbc-client-cdi`               | The CDI beans that produce the `JdbcClient` and the CDI-discovered `ConverterRegistry`.                                    |
| `integration-tests` | `jdbc-client-integration-tests` | Arquillian integration tests on GlassFish and WildFly.                                                                     |

## Core — plain `JdbcClient`

Add the `jdbc-client-core` dependency:

```xml
<dependency>
    <groupId>io.github.hantsy.jdbc</groupId>
    <artifactId>jdbc-client-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

The `core` module has zero runtime dependencies (JDK only). Create a client directly from a
`DataSource`:

```java
import io.github.hantsy.jdbc.JdbcClient;

JdbcClient client = new JdbcClient(dataSource);

List<DevSummary> devs = client.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
        .param("id", 1L)
        .query(DevSummary.class)
        .list();

Long count = client.sql("SELECT COUNT(*) FROM engineers").singleValue(Long.class);
```

Configuration and converters are optional. Use the builder to override them:

```java
JdbcClient client = JdbcClient.builder(dataSource)
        .placeholder("?")            // :name -> ? (default) or $1, $2, ...
        .queryTimeout(30)            // seconds
        .fetchSize(100)
        .converters(registry)
        .build();
```

The fluent API supports named (`:name`) and positional (`?`) parameters, records/POJO reflection
mapping, custom `RowMapper`, `single()`/`optional()`/`stream()`, scalar `singleValue()`, generated keys
via `KeyHolder`, and batch updates.

### RowMapper

Map rows manually with a `RowMapper` lambda or a custom `RowMapper` implementation:

```java
// Lambda row mapper
List<String> names = client.sql("SELECT dev_name FROM engineers ORDER BY id")
        .query((rs, rowNum) -> rs.getString("dev_name"))
        .list();

// Custom RowMapper
RowMapper<DevSummary> mapper = (rs, rowNum) ->
        new DevSummary(rs.getLong("id"), rs.getString("dev_name"));
List<DevSummary> devs = client.sql("SELECT id, dev_name FROM engineers")
        .query(mapper)
        .list();
```

### Converters

Converters translate a JDBC/database value into a Java type when mapping result-set columns to
records, POJOs, and scalar values (e.g. `java.sql.Timestamp` → `LocalDateTime`, `BigDecimal` → `Long`).

Register type converters in a plain `ConverterRegistry`:

```java
ConverterRegistry registry = new ConverterRegistry()
        .register(UUID.class, String.class, UUID::toString)
        .register(String.class, UUID.class, UUID::fromString);
```

## CDI — injecting `JdbcClient`

Add the `jdbc-client-cdi` dependency (and, optionally, `jdbc-client-config`):

```xml
<dependency>
    <groupId>io.github.hantsy.jdbc</groupId>
    <artifactId>jdbc-client-cdi</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Supply a `DataSource` producer. The CDI module produces the `@ApplicationScoped JdbcClient`:

```java
@Inject
private JdbcClient jdbcClient;
```

`DataSource` is not produced by the library — you provide it, e.g. via JNDI:

```java
@ApplicationScoped
public class DataSourceProducer {
    @Resource(lookup = "jdbc/MyDS")
    private DataSource dataSource;

    @Produces
    @ApplicationScoped
    public DataSource expose() {
        return dataSource;
    }
}
```

`Converter` beans (`@ApplicationScoped Converter<S, T>`) are discovered automatically and registered:

```java
import io.github.hantsy.jdbc.converter.Converter;

@ApplicationScoped
public class UuidToStringConverter implements Converter<UUID, String> {

    @Override
    public String convert(UUID source) {
        return source.toString();
    }
}
```

## Configuration

Add the `jdbc-client-config` dependency:

```xml
<dependency>
    <groupId>io.github.hantsy.jdbc</groupId>
    <artifactId>jdbc-client-config</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

`JdbcConfig` is a plain POJO in `core`. The optional `config` module produces a `JdbcConfig` bean from
MicroProfile Config properties:

| Property                   | Default | Description                                                                                                                               |
|----------------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `jdbcclient.placeholder`   | `?`     | JDBC placeholder symbol used when rewriting `:name` parameters: `?` (MySQL, default), `$1` (H2/PostgreSQL), `:1` (Oracle), `@name` (SQL Server). |
| `jdbcclient.query-timeout` | `0`     | Default query timeout in seconds (`0` = no timeout).                                                                                      |
| `jdbcclient.fetch-size`    | `0`     | Default fetch size hint (`0` = driver default).                                                                                           |

When the `config` module is **not** present, the CDI producer falls back to `JdbcConfig.DEFAULT`.

## Building

### Prerequisites

- JDK 21+
- Maven 3.9+ (or use the included Maven wrapper `./mvnw`)

### Build

```bash
# Clone the repository
git clone https://github.com/hantsy/jdbc-client-cdi.git
cd jdbc-client-cdi

# Build all modules and run unit tests (integration tests are skipped by default)
./mvnw clean install

# Run the Arquillian integration tests on GlassFish (downloads and boots GlassFish 8)
./mvnw -pl integration-tests -Parq-glassfish-managed verify

# Run the Arquillian integration tests on WildFly (downloads and boots WildFly 41)
./mvnw -pl integration-tests -Parq-wildfly-managed verify
```

## Contributing

Contributions are welcome! Feel free to file issues, submit pull requests, or suggest new features.
