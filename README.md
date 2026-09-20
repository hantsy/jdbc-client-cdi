# 🚀 Fluent Jakarta EE 11 JDBC Client

A lightweight, framework-agnostic database access library designed explicitly for **Jakarta EE 11** and **CDI**. This library mirrors the fluid developer experience of Spring’s modern `JdbcClient` and `JdbcTemplate` while remaining **100% free of Spring dependencies**.

It integrates perfectly alongside your standard **Jakarta Data Specification** beans to offer custom, rapid programmatic raw SQL processing when declarative repositories fall short.

---

## 🏛️ Architecture Overview

The project processes database operations synchronously across three integrated phases:

```
[Service Layer] ──> [JdbcClient] ──> [Universal Reflection Engine] ──> (Java Records / POJOs)
                                │
                                └──> [ConverterRegistry] (Reflective Generic Scanning)
                                └──> [User Custom Converter<S, T>]
```

1. **`JdbcClient`**: The primary user-facing programmatic entry point. It features a fluent builder for both named parameters (`:param`) and positional parameters (`?`), unified transaction boundaries via standard `@jakarta.transaction.Transactional`, and execution performance logging.
2. **`ConverterRegistry`**: A central CDI component that automatically discovers and registers every type-safe, `@ApplicationScoped` implementation of the `Converter<S, T>` interface within the application classpath at boot-time.
3. **Universal Reflection Engine**: Eliminates heavy ORM configurations. It maps database result rows directly into immutable **Java Records** or standard mutable **POJOs** based on matching field/component names (ignoring casing and underscores).

---

## ⚙️ Performance Log Toggles

The telemetry logging metrics engine can be enabled or disabled dynamically at runtime without rebuilding your application package. The configuration engine evaluates three layers in order of structural priority:

| Prioritized Level | Mechanics | Code Example / Property Value |
| :--- | :--- | :--- |
| **1. Request Override** | Programmatic chain statement config. | `jdbcClient.sql(sql).enableMetrics(false)` |
| **2. Environment Variables** | Global container engine environment arguments. | `export JDBCCLIENT_METRICS_ENABLED=false` |
| **3. MicroProfile Config** | Decoupled platform runtime configuration properties. | `jdbcclient.metrics.enabled=true` |

---

## 💾 DataSource Provisioning & Production Infrastructure

By utilizing pure **CDI Constructor Injection**, the user retains complete authority over how connection pools are instantiated and configured.

### Production Setup (Container-Driven JNDI)
To register the client inside an operational production server context (like GlassFish v8), add a standard resource file alongside a producer definition to link your server's pool directly into the CDI framework:

#### 1. Define resources inside `src/main/webapp/WEB-INF/glassfish-resources.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE resources PUBLIC "-//GlassFish.org//DTD GlassFish Application Server 3.1 Resource Definitions//EN" 
  "http://glassfish.org">
<resources>
    <jdbc-connection-pool name="PostgresPool" 
                          datasource-classname="org.postgresql.ds.PGSimpleDataSource" 
                          res-type="javax.sql.DataSource">
        <property name="User" value="prod_admin"/>
        <property name="Password" value="secure_password"/>
        <property name="Url" value="jdbc:postgresql://localhost:5432/enterprise_db"/>
    </jdbc-connection-pool>

    <jdbc-resource jndi-name="jdbc/ProductionDS" pool-name="PostgresPool"/>
</resources>
```

#### 2. Create the Production CDI Producer

```java
package com.example.config;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import javax.sql.DataSource;

@ApplicationScoped
public class ProductionDataSourceProducer {

    @Resource(lookup = "jdbc/ProductionDS")
    private DataSource glassfishPool;

    @Produces
    @ApplicationScoped
    public DataSource exposeDataSource() {
        return this.glassfishPool;
    }
}
```

---

## 🛠️ Unified Record and POJO Projections

```java
package com.example.service;

import com.example.db.JdbcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

public record TeamMemberRecord(Long id, String fullName, int ranking) {}

@ApplicationScoped
public class EngineeringService {

    @Inject
    private JdbcClient jdbcClient;

    @Transactional
    public List<TeamMemberRecord> findTopEngineers(int minRank) {
        String sql = "SELECT id, full_name, ranking FROM engineers WHERE ranking >= :minRank";
        
        return jdbcClient.sql(sql)
                .param("minRank", minRank)
                .query(TeamMemberRecord.class) // Auto-maps 'full_name' column to 'fullName' component
                .list();
    }
}
```

---

## 🧪 Running the Arquillian Integration Test Suite

The test suite mirrors your target environment by downloading and extracting a standalone GlassFish v8 zip instance dynamically. At runtime, it mounts an embedded H2 datastore database instance, bundles the test web archive (WAR), and executes assertions directly inside the CDI container context.

### Execute Container Tests
Open a system terminal at your root project folder destination and execute standard integration verify hooks:
```bash
mvn clean verify
```

The system output window will log active telemetries confirming the execution timeline metrics:

```text
INFO: SQL Metrics | QUERY | 14ms | SELECT id, dev_name FROM engineers WHERE id = ?
```
