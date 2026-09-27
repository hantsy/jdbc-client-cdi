# Fluent JDBC Client for Jakarta EE/CDI

A lightweight, framework-agnostic fluent JDBC client for Jakarta EE and CDI
applications. It mirrors the developer experience of Spring's `JdbcClient`
without requiring Spring.

The project is organized into three modules:

- `jdbc-client-core`: the core `JdbcClient` API and supporting types.
- `jdbc-client-config`: optional MicroProfile Config integration.
- `jdbc-client-cdi`: CDI producers for `JdbcClient` and converters.

The [`examples`](examples/README.md) directory holds runnable examples.

## Usage

### Creating a `JdbcClient`

```java
import io.github.hantsy.jdbc.JdbcClient;

import javax.sql.DataSource;

DataSource dataSource = ...; // obtain a DataSource from your application or runtime

JdbcClient client = new JdbcClient(dataSource);
```

### Performing a query

```java
List<Engineer> engineers = client.sql("SELECT id, name FROM engineers WHERE id = :id")
        .param("id", 1L)
        .query(Engineer.class)
        .list();
```

### Updating existing data

```java
int rows = client.sql("UPDATE engineers SET name = :name WHERE id = :id")
        .param("name", "Ada Lovelace")
        .param("id", 1L)
        .update();
```

For more details — installation options, the full query and update API, and
configuration — see the documentation, starting with
[installation](docs/install.md), the [quickstart](docs/quickstart.md), and
[querying and result mapping](docs/querying.md). The same content is published
at the [reference documentation site](https://hantsy.github.io/jdbc-client-cdi/).

## Contributing

Contributions are welcome — issues, pull requests, and feature suggestions are
all encouraged. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to build the
project, the module layout, and how to submit changes.
