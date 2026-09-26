package io.github.hantsy.jdbc.tx.it.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.hantsy.jdbc.tx.PlatformTransactionManager;
import io.github.hantsy.jdbc.tx.resourcelocal.DataSourceTransactionManager;
import io.github.hantsy.jdbc.tx.resourcelocal.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Named;

/**
 * Single-DataSource CDI/DB support: raw pool, transaction-aware proxy (default {@code DataSource}),
 * and the {@link PlatformTransactionManager} built over the raw pool.
 */
@ApplicationScoped
public class ApplicationDatabaseConfig {

    private final HikariDataSource underlyingPool;

    public ApplicationDatabaseConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(10);
        this.underlyingPool = new HikariDataSource(config);
    }

    @Produces
    @Named("raw")
    @Typed(HikariDataSource.class)
    @ApplicationScoped
    public HikariDataSource getUnderlyingPool() {
        return underlyingPool;
    }

    @Produces
    @ApplicationScoped
    public DataSource produceApplicationDataSource() {
        return new TransactionAwareDataSourceProxy(underlyingPool);
    }

    @Produces
    @ApplicationScoped
    public PlatformTransactionManager transactionManager() {
        return new DataSourceTransactionManager(underlyingPool);
    }
}
