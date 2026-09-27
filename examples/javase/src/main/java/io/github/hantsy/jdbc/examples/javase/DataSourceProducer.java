package io.github.hantsy.jdbc.examples.javase;

import io.github.hantsy.jdbc.tx.PlatformTransactionManager;
import io.github.hantsy.jdbc.tx.resourcelocal.DataSourceTransactionManager;
import io.github.hantsy.jdbc.tx.resourcelocal.TransactionAwareDataSourceProxy;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Produces the transaction-aware {@link DataSource} consumed by the {@code cdi} module's
 * {@code JdbcClientProducer} and the resource-local {@link PlatformTransactionManager}. Both are
 * built over the same raw in-memory H2 pool, so the transaction's bound connection is shared.
 */
@ApplicationScoped
public class DataSourceProducer {

    private final JdbcDataSource pool;

    public DataSourceProducer() {
        this.pool = new JdbcDataSource();
        this.pool.setURL("jdbc:h2:mem:javase;DB_CLOSE_DELAY=-1");
        this.pool.setUser("sa");
        this.pool.setPassword("");
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
