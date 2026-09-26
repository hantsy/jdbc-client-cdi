package io.github.hantsy.jdbc.tx.it.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.hantsy.jdbc.tx.PlatformTransactionManager;
import io.github.hantsy.jdbc.tx.resourcelocal.DataSourceTransactionManager;
import io.github.hantsy.jdbc.tx.resourcelocal.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

/**
 * Two-DataSource CDI/DB support: an "order" and a "customer" pool, each exposed both raw and
 * transaction-aware. The default {@link PlatformTransactionManager} manages the order pool; the
 * customer pool joins best-effort.
 */
@ApplicationScoped
public class MultiDatabaseConfig {

    private final HikariDataSource orderRawPool;
    private final HikariDataSource customerRawPool;

    public MultiDatabaseConfig() {
        HikariConfig orderCfg = new HikariConfig();
        orderCfg.setJdbcUrl("jdbc:h2:mem:order_db;DB_CLOSE_DELAY=-1");
        orderCfg.setUsername("sa");
        orderCfg.setPassword("");
        this.orderRawPool = new HikariDataSource(orderCfg);

        HikariConfig customerCfg = new HikariConfig();
        customerCfg.setJdbcUrl("jdbc:h2:mem:customer_db;DB_CLOSE_DELAY=-1");
        customerCfg.setUsername("sa");
        customerCfg.setPassword("");
        this.customerRawPool = new HikariDataSource(customerCfg);
    }

    @Produces
    @Named("orderRaw")
    @ApplicationScoped
    public DataSource getOrderRawPool() {
        return orderRawPool;
    }

    @Produces
    @Named("orderDataSource")
    @ApplicationScoped
    public DataSource orderDataSource() {
        return new TransactionAwareDataSourceProxy(orderRawPool);
    }

    @Produces
    @ApplicationScoped
    public PlatformTransactionManager defaultTxManager() {
        return new DataSourceTransactionManager(orderRawPool);
    }

    @Produces
    @Named("customerRaw")
    @ApplicationScoped
    public DataSource getCustomerRawPool() {
        return customerRawPool;
    }

    @Produces
    @Named("customerDataSource")
    @ApplicationScoped
    public DataSource customerDataSource() {
        return new TransactionAwareDataSourceProxy(customerRawPool);
    }
}
