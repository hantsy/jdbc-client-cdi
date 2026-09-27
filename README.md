# Fluent JDBC Client for Jakarta EE/CDI

A lightweight, framework-agnostic fluent JDBC client for Jakarta EE and CDI
applications. It mirrors the developer experience of Spring's `JdbcClient`
without requiring Spring.

The project is organized into four modules:

- `jdbc-client-core`: the core `JdbcClient` API and supporting types.
- `jdbc-client-config`: optional MicroProfile Config integration.
- `jdbc-client-cdi`: CDI producers for `JdbcClient` and converters.
- `jdbc-client-tx`: resource-local transactions for Java SE and Servlet runtimes.

The [`examples`](examples/README.md) directory holds runnable examples.

Start with the [reference documentation](https://hantsy.github.io/jdbc-client-cdi/) for installation, usage patterns,
and configuration details.

## Building

### Prerequisites

- JDK 21+
- Maven 3.9+ (or use the included Maven wrapper `./mvnw`)

### Build

Clone the repository:

```bash
git clone https://github.com/hantsy/jdbc-client-cdi.git
```

Build all modules and run the unit tests:

```bash
cd jdbc-client-cdi
./mvnw clean install
```

### Building the documentation

For a local documentation site:

```bash
python -m pip install -r docs/requirements.txt
mkdocs serve
```

To build the documentation site:

```bash
mkdocs build --strict
```

## Contributing

Contributions are welcome. Issues, pull requests, and feature suggestions are all encouraged.
