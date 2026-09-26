# Querying and result mapping

`JdbcClient.sql(...)` returns a SQL specification. It supports named parameters such as `:name`, positional parameters
such as `?`, statement hints, typed mapping, and update statements.

## Retrieving simple values

Use `singleValue(...)` or `optionalValue(...)` to read a single scalar result from the database.

```java
Long count = client.sql("SELECT COUNT(*) FROM engineers")
        .singleValue(Long.class);

Optional<Long> maybeCount = client.sql("SELECT COUNT(*) FROM engineers")
        .optionalValue(Long.class);
```

## Mapping results to records and POJOs

The built-in typed mapper matches result-column labels to record components or fields. Underscores are ignored when
matching a column name to a record component.

```java
public record DevSummary(Long id, String devName) {
}

List<DevSummary> devs = client
        .sql("SELECT id, dev_name FROM engineers ORDER BY id")
        .query(DevSummary.class)
        .list();
```

The mapper also works with a no-argument POJO whose fields match the result columns.

The following result-access patterns are available:

- `single()` requires exactly one row.
- `optional()` allows zero or one row.
- `list()` reads all rows before returning.
- `stream()` returns a lazy stream, and the stream should be closed when work is complete so JDBC resources are
  released.

```java
DevSummary one = client.sql("SELECT ... WHERE id = :id")
        .param("id", 1L)
        .query(DevSummary.class)
        .single();

Optional<DevSummary> maybeOne = client.sql("SELECT ... WHERE id = :id")
        .param("id", 1L)
        .query(DevSummary.class)
        .optional();

Stream<DevSummary> stream = client.sql("SELECT ...")
        .query(DevSummary.class)
        .stream();
```

## Customizing row mapping

Mapping can be defined inline with a lambda or reused through a dedicated `RowMapper`.

```java
List<String> names = client
        .sql("SELECT dev_name FROM engineers ORDER BY id")
        .query((rs, rowNum) -> rs.getString("dev_name"))
        .list();

RowMapper<DevSummary> mapper = (rs, rowNum) ->
        new DevSummary(rs.getLong("id"), rs.getString("dev_name"));

List<DevSummary> devs = client
        .sql("SELECT id, dev_name FROM engineers")
        .query(mapper)
        .list();
```

## Configuring converters

Converters translate a database value into the Java type requested by a typed mapper or scalar query. The built-in
mapper supports common conversions for numeric types, strings, booleans, SQL date/time types, and Java time types.

Use a `ConverterRegistry` with a standalone client:

```java
ConverterRegistry registry = new ConverterRegistry()
        .register(UUID.class, String.class, UUID::toString)
        .register(String.class, UUID.class, UUID::fromString);

JdbcClient client = JdbcClient.builder(dataSource)
        .converters(registry)
        .build();
```

A converter implements `Converter<S, T>`:

```java
Converter<UUID, String> converter = UUID::toString;
registry.register(UUID.class, String.class, converter);
```

In a CDI application, register the converter as an application-scoped bean. The CDI integration automatically discovers
the generic source and target types.

```java
@ApplicationScoped
public class UuidToStringConverter implements Converter<UUID, String> {

    @Override
    public String convert(UUID source) {
        return source.toString();
    }
}
```

Converters are consulted when the JDBC value is not already assignable to the requested target type.

For executing inserts, updates, and batch statements, see [updates](updates.md). For configuration options,
see [configuration](configuration.md).
