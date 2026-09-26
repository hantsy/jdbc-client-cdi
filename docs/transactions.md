# Transactions

`jdbc-client` can participate in database transactions in two ways, depending on the runtime:

- **Java SE or a Servlet container** — use the `jdbc-client-tx` module for *resource-local* transactions.
  It provides a CDI interceptor that honors `jakarta.transaction.Transactional` over a plain JDBC
  `DataSource`, with no JTA.
- **A full Jakarta EE runtime** (WildFly, GlassFish, Payara, Open Liberty, ...) — the container ships a
  JTA implementation, so `jakarta.transaction.Transactional` works out of the box and the `tx` module is
  unnecessary.

This page focuses on the resource-local case and ends with a short note on the Jakarta EE case.

## Resource-local transactions (Java SE / Servlet)

### Add the dependency

```xml
<dependency>
    <groupId>io.github.hantsy.jdbc</groupId>
    <artifactId>jdbc-client-tx</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

The module declares the CDI API and `jakarta.transaction-api` as `provided`: supply a CDI container (Weld SE, or a
Servlet container with Weld) plus `jakarta.transaction-api` for the `@Transactional`
annotation.

### Wire the DataSource and the transaction manager

Expose two `DataSource` beans and a `PlatformTransactionManager`:

- the **raw pool** (the actual connection pool),
- a `TransactionAwareDataSourceProxy` wrapping it — this is the `DataSource` your code injects,
- a `DataSourceTransactionManager` built from the same raw pool.

```java
import com.zaxxer.hikari.HikariDataSource;
import io.github.hantsy.jdbc.tx.PlatformTransactionManager;
import io.github.hantsy.jdbc.tx.resourcelocal.DataSourceTransactionManager;
import io.github.hantsy.jdbc.tx.resourcelocal.TransactionAwareDataSourceProxy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import javax.sql.DataSource;

@ApplicationScoped
public class DatabaseConfig {

    private final HikariDataSource pool = /* your connection pool */;

    @Produces
    @Named("raw")
    @ApplicationScoped
    public DataSource rawPool() {
        return pool;
    }

    @Produces
    @ApplicationScoped
    public DataSource dataSource() {
        return new TransactionAwareDataSourceProxy(pool);
    }

    @Produces
    @ApplicationScoped
    public PlatformTransactionManager transactionManager() {
        return new DataSourceTransactionManager(pool);
    }
}
```

The manager and the proxy must share the **same raw pool** — the pool instance is the key that binds the
transaction's `Connection` to the thread.

### Declarative transactions

Annotate a CDI bean method with `jakarta.transaction.Transactional`. Inject the transaction-aware
`DataSource` and use it as usual:

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

@ApplicationScoped
public class OrderService {

    @Inject
    private DataSource dataSource; // the TransactionAwareDataSourceProxy

    @Transactional
    public void createOrder(int id, String info) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO orders (id, info) VALUES (?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, info);
            ps.executeUpdate();
        }
    }
}
```

The `tx` module auto-registers a CDI interceptor that drives the transaction around the method: it opens a
`Connection`, disables auto-commit, and commits on success or rolls back on failure. Inside the method the
proxy returns that bound `Connection` and suppresses `close()`, so the try-with-resources block does not end
the transaction.

Propagation is controlled with `Transactional.TxType` (`REQUIRED` by default, plus `REQUIRES_NEW`,
`MANDATORY`, `SUPPORTS`, `NOT_SUPPORTED`, `NEVER`):

```java
@Transactional(Transactional.TxType.REQUIRES_NEW)
public void doWork() {
}
```

Exceptions are classified with `rollbackOn` and `dontRollbackOn`. By default the transaction rolls back on
a `RuntimeException` or an `Error`, and commits on a checked exception.

```java
@Transactional(rollbackOn = CheckedBusinessException.class)
public void doWork() throws CheckedBusinessException {
}

@Transactional(dontRollbackOn = NoRollbackException.class)
public void doWork() {
}
```

### Transaction-phase callbacks

Register a `TransactionSynchronization` with `TransactionSynchronizationManager` to run code at a
transaction phase. Registration must happen inside an active transaction.

```java
import io.github.hantsy.jdbc.tx.support.TransactionSynchronization;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronizationManager;

@Transactional
public void doWork() {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            // commit-only follow-up
        }

        @Override
        public void afterCompletion(CompletionStatus status) {
            // COMMITTED or ROLLED_BACK
        }
    });
}
```

`TransactionSynchronizationManager.setRollbackOnly()` marks the current transaction for rollback without
throwing.

### CDI transaction-phase events

Observers can react to the transaction outcome with `@Observes(during = TransactionPhase.X)`. An event
fired inside an active transaction is deferred and delivered at the matching phase.

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

@ApplicationScoped
public class OrderNotifier {

    public void onCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrderCreated event) {
        // runs only if the transaction commits
    }
}
```

Supported phases are `BEFORE_COMPLETION`, `AFTER_SUCCESS`, `AFTER_FAILURE`, and `AFTER_COMPLETION`.
`IN_PROGRESS` observers fire immediately.

### Multiple DataSources

A single transaction can touch more than one `DataSource`. The primary manager drives the transaction;
additional `TransactionAwareDataSourceProxy`s join best-effort. This is **not XA**: each `Connection`
commits independently, so work across `DataSource`s is not atomic.

## Transactions in a full Jakarta EE runtime

On a full Jakarta EE server JTA is built in, so `jakarta.transaction.Transactional` is handled by the
container — no `jdbc-client-tx` dependency is needed. Expose the `DataSource` through JNDI (`@DataSourceDefinition` or
the server's admin console) and let the container manage the transaction:

```java
import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

@Stateless
public class OrderService {

    @Resource(lookup = "java:comp/DefaultDataSource")
    private DataSource dataSource;

    @Transactional
    public void createOrder(int id, String info) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO orders (id, info) VALUES (?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, info);
            ps.executeUpdate();
        }
    }
}
```

For programmatic control inject `jakarta.transaction.UserTransaction`; for per-transaction state use
`@TransactionScoped`. See your server's documentation for JTA details.
