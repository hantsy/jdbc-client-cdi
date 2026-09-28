# Examples

Runnable examples for the fluent JDBC client. They are excluded from the main
reactor and build separately, driven by the `examples.yml` GitHub Actions
workflow (one job per example).

## Overview

| Example                  | Packaging | Modules + Database          | Demonstrates                                                                                                                                   |
|--------------------------|-----------|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| [`vanilla`](vanilla)     | jar       | core, H2                    | The core `JdbcClient` with no CDI, backed by an in-memory H2 `DataSource`.                                                                     |
| [`javase`](javase)       | jar       | core/cdi/tx, H2             | The core + CDI + tx modules, bootstrapped with Weld SE from a `main()` method; resource-local `@Transactional`.                                 |
| [`servlet`](servlet)     | war       | core/cdi/tx, MariaDB        | The core + CDI + tx modules in a Servlet container (Tomcat 11), with the `DataSource` provided via JNDI; resource-local `@Transactional`.       |
| [`jakartaee`](jakartaee) | war       | core/cdi/config, PostgreSQL | The core + CDI modules behind a JAX-RS resource on GlassFish / WildFly, backed by PostgreSQL; container-managed JTA `@Transactional`.           |

Each example keeps the same small CRUD story: get all, get by id, insert,
update, and delete an `Engineer`. The `javase`, `servlet`, and `jakartaee`
examples wrap those operations in an `EngineerService` annotated with
`@Transactional` — resource-local (via the `tx` module) in `javase`/`servlet`,
and container-managed JTA (XA `DataSource`) in `jakartaee`. See
[transactions](../docs/transactions.md) for details.

## Prerequisites

- JDK 21+
- Maven 3.9+ (or the included Maven wrapper `./mvnw`)
- PostgreSQL on `localhost:5432` (only for the `jakartaee` example)
- MariaDB on `localhost:3306` with a `servlet` database and `root`/`root`
  credentials (only for the `servlet` example)

Start the databases with Docker Compose (same images/credentials as CI):

```bash
docker compose -f examples/docker-compose.yml up -d
```

Install the main modules once, so the examples can resolve `jdbc-client-*`:

```bash
./mvnw install -DskipTests
```

## Build and test

All examples are built from the `examples` reactor; select one with `-pl`.

```bash
# vanilla — runs the JUnit test
./mvnw -f examples/pom.xml -pl vanilla verify

# javase — runs the weld-junit5 test
./mvnw -f examples/pom.xml -pl javase verify

# servlet — runs the Arquillian integration tests (requires MariaDB)
./mvnw -f examples/pom.xml -pl servlet -Parq-tomcat-embedded verify

# jakartaee — runs the Arquillian integration tests (requires PostgreSQL)
./mvnw -f examples/pom.xml -pl jakartaee -Parq-glassfish-managed verify
./mvnw -f examples/pom.xml -pl jakartaee -Parq-wildfly-managed verify
```

Integration tests are skipped by default, so a plain `verify` on `jakartaee` or
`servlet` only compiles and packages without booting a container.

## Run

The `servlet` and `jakartaee` examples deploy as WARs and can be started locally
with a Maven profile; the `vanilla` and `javase` examples expose a `main`
method that can be run from your IDE.

```bash
# servlet on embedded Tomcat 11 (servlet mapped at /engineers; requires MariaDB)
./mvnw -f examples/pom.xml -pl servlet -Ptomcat-embedded clean package cargo:run

# jakartaee on GlassFish 8 (JAX-RS resource at /api/engineers)
./mvnw -f examples/pom.xml -pl jakartaee -Pglassfish clean package cargo:run

# jakartaee on WildFly 41 (JAX-RS resource at /api/engineers)
./mvnw -f examples/pom.xml -pl jakartaee -Pwildfly clean package wildfly:run
```

`vanilla` (`VanillaExample`) and `javase` (`Main`) are plain Java entry points —
run them from your IDE or with an exec plugin.
