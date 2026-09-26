# Fluent JDBC Client for Jakarta EE/CDI

Fluent JDBC Client is a lightweight, framework-agnostic JDBC client for Jakarta EE and CDI applications. It provides the
fluent developer experience of Spring's `JdbcClient` without introducing a Spring dependency.

The project is organized into three core modules:

| Module   | Artifact             | Description                                                                                                |
|----------|----------------------|------------------------------------------------------------------------------------------------------------|
| `core`   | `jdbc-client-core`   | Contains the plain `JdbcClient` API and helper types. It depends only on the JDK's `javax.sql.DataSource`. |
| `config` | `jdbc-client-config` | Integrates with MicroProfile Config and exposes a configurable `JdbcConfig` bean.                          |
| `cdi`    | `jdbc-client-cdi`    | Provides CDI producers for `JdbcClient` and the CDI-discovered `ConverterRegistry`.                        |

The repository also includes Arquillian integration tests for GlassFish and WildFly. These examples provide a practical
reference for validating Jakarta EE/CDI applications in real runtimes.

Follow this reading order:

- Start with [installation](install.md) to select the appropriate dependency.
- Continue with [quickstart](quickstart.md) for the fastest path to using the client.
- Explore [querying and result mapping](querying.md) and [updates](updates.md) for detailed behavior.
- Review [configuration](configuration.md) for placeholders, timeouts, and defaults.
