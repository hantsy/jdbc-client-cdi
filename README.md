# FluDa: Fluent Data Toolkit for Jakarta EE/CDI

FluDa(**Flu**ent **Da**ta Toolkit for Jakarta EE/CDI) is a lightweight, modular data-access suite designed specifically for standard Jakarta EE and CDI environments. It provides a developer-friendly, fluent programming model that mirrors modern data APIs without requiring full-blown Spring frameworks or introducing bulky, legacy dependencies. The toolkit is organized into three standalone, high-level core feature pillars:

* **JDBC Client**(`fluda-jdbc-client`): A lightweight, type-safe, fluent query engine built directly over JDBC.
* **Resource Local Transaction Support** (`fluda-tx`): Container-agnostic declarative and programmatic transaction boundaries backed directly by a standard DataSource or JPA. (*coming soon*)
* **SQL Initialization** (`fluda-sql-init`): Automates database schema management and data population on application startup. (*coming soon*)

The `jdbc-client` module is organized into three submodules:

- `fluda-jdbc-client-core`: the core `JdbcClient` API and supporting types.
- `fluda-jdbc-client-config`: optional MicroProfile Config integration.
- `fluda-jdbc-client-cdi`: CDI producers for `JdbcClient` and converters.

The [`examples`](examples/README.md) directory holds runnable examples.

## Usage

### Creating a `JdbcClient`

```java
import io.github.fludakit.jdbc.JdbcClient;

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
