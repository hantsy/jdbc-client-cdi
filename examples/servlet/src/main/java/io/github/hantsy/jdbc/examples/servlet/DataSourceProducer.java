package io.github.hantsy.jdbc.examples.servlet;

import io.github.hantsy.jdbc.tx.PlatformTransactionManager;
import io.github.hantsy.jdbc.tx.resourcelocal.DataSourceTransactionManager;
import io.github.hantsy.jdbc.tx.resourcelocal.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Bridges the JNDI {@link DataSource} declared in {@code META-INF/context.xml} to the CDI beans:
 * exposes a transaction-aware proxy as the default {@link DataSource} and the resource-local
 * {@link PlatformTransactionManager}, both over the same raw pool.
 */
@ApplicationScoped
public class DataSourceProducer {

    @Resource(name = "jdbc/myDS")
    private DataSource rawDataSource;

    @Produces
    @ApplicationScoped
    public DataSource expose() {
        return new TransactionAwareDataSourceProxy(rawDataSource);
    }

    @Produces
    @ApplicationScoped
    public PlatformTransactionManager transactionManager() {
        return new DataSourceTransactionManager(rawDataSource);
    }
}
