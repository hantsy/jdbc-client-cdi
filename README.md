# Fluent JDBC Client for Jakarta EE/CDI

A lightweight, framework-agnostic fluent JDBC client for Jakarta EE and CDI
applications. It mirrors the developer experience of Spring's `JdbcClient`
without requiring Spring.

The project is organized into three modules:

- `jdbc-client-core`: the core `JdbcClient` API and supporting types.
- `jdbc-client-config`: optional MicroProfile Config integration.
- `jdbc-client-cdi`: CDI producers for `JdbcClient` and converters.

The [`examples`](examples/README.md) directory holds runnable examples (excluded
from the main build):

- `examples/vanilla`: plain (no CDI) usage of the core module.
- `examples/javase`: core + CDI, bootstrapped with Weld SE.
- `examples/servlet`: core in a Servlet container (Tomcat 11) with a JNDI DataSource.
- `examples/jakartaee`: core + CDI + JAX-RS on GlassFish / WildFly.

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

### Building the examples

The examples are excluded from the main reactor and build separately. Install the
main modules first, then build each example:

```bash
./mvnw install -DskipTests

# vanilla (core only)
./mvnw -f examples/pom.xml -pl vanilla verify

# javase (core + CDI via Weld SE)
./mvnw -f examples/pom.xml -pl javase verify

# servlet (Arquillian integration tests on embedded Tomcat 11)
./mvnw -f examples/pom.xml -pl servlet -Parq-tomcat-embedded verify

# jakartaee (Arquillian integration tests; requires PostgreSQL on localhost:5432)
./mvnw -f examples/pom.xml -pl jakartaee -Parq-glassfish-managed verify
./mvnw -f examples/pom.xml -pl jakartaee -Parq-wildfly-managed verify
```

Run the applications locally:

```bash
# jakartaee on GlassFish 8
./mvnw -f examples/pom.xml -pl jakartaee -Pglassfish clean package cargo:run

# jakartaee on WildFly 41
./mvnw -f examples/pom.xml -pl jakartaee -Pwildfly clean package wildfly:run

# servlet on embedded Tomcat 11
./mvnw -f examples/pom.xml -pl servlet -Pcargo-run clean package cargo:run
```

The `vanilla` and `javase` examples expose a `main` method that can be run from your IDE.

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
