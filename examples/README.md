# Examples

Runnable examples for the fluent JDBC client. They are excluded from the main
reactor and build separately, driven by the `examples.yml` GitHub Actions
workflow (one job per example).

## Overview

| Example                  | Packaging | Demonstrates                                                                                                             |
|--------------------------|-----------|--------------------------------------------------------------------------------------------------------------------------|
| [`vanilla`](vanilla)     | jar       | The core `JdbcClient` with no CDI, backed by an in-memory H2 `DataSource`.                                               |
| [`javase`](javase)       | jar       | The core + CDI modules, bootstrapped with Weld SE from a `main()` method.                                                |
| [`servlet`](servlet)     | war       | The core module in a Servlet container (Tomcat 11), with the `DataSource` provided via JNDI from `META-INF/context.xml`. |
| [`jakartaee`](jakartaee) | war       | The core + CDI modules behind a JAX-RS resource on GlassFish / WildFly, backed by PostgreSQL.                            |

Each example keeps the same small CRUD story: get all, get by id, insert,
update, and delete an `Engineer`.

## Prerequisites

- JDK 21+
- Maven 3.9+ (or the included Maven wrapper `./mvnw`)
- PostgreSQL running on `localhost:5432` (only for the `jakartaee` example)

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

# servlet — runs the Arquillian integration tests on embedded Tomcat 11
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
# servlet on embedded Tomcat 11 (servlet mapped at /engineers)
./mvnw -f examples/pom.xml -pl servlet -Ptomcat-embedded clean package cargo:run

# jakartaee on GlassFish 8 (JAX-RS resource at /api/engineers)
./mvnw -f examples/pom.xml -pl jakartaee -Pglassfish clean package cargo:run

# jakartaee on WildFly 41 (JAX-RS resource at /api/engineers)
./mvnw -f examples/pom.xml -pl jakartaee -Pwildfly clean package wildfly:run
```

`vanilla` (`VanillaExample`) and `javase` (`Main`) are plain Java entry points —
run them from your IDE or with an exec plugin.
