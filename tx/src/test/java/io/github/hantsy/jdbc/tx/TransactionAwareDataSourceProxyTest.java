package io.github.hantsy.jdbc.tx;

import io.github.hantsy.jdbc.tx.resourcelocal.TransactionAwareDataSourceProxy;
import io.github.hantsy.jdbc.tx.support.TransactionContext;
import io.github.hantsy.jdbc.tx.support.TransactionContextHolder;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronizationManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionAwareDataSourceProxyTest {

    @Test
    void delegatesOutsideTransaction() throws Exception {
        DataSource dataSource = newDataSource("proxy1");
        DataSource proxy = new TransactionAwareDataSourceProxy(dataSource);

        Connection connection = proxy.getConnection();
        assertFalse(connection.isClosed());
        connection.close();
        assertTrue(connection.isClosed(), "outside a transaction close must be real");
    }

    @Test
    void closeIsSuppressedInsideTransaction() throws Exception {
        DataSource dataSource = newDataSource("proxy2");
        DataSource proxy = new TransactionAwareDataSourceProxy(dataSource);
        Connection txConnection = dataSource.getConnection();

        TransactionContextHolder.call(new TransactionContext(true), () -> {
            TransactionSynchronizationManager.bindResource(dataSource, txConnection);
            Connection got = proxy.getConnection();
            got.close();
            assertFalse(txConnection.isClosed(), "close must be suppressed inside a transaction");
            return null;
        });

        txConnection.close();
    }

    @Test
    void joinsTransactionWhenNotYetBound() throws Exception {
        DataSource dataSource = newDataSource("proxy3");
        DataSource proxy = new TransactionAwareDataSourceProxy(dataSource);

        TransactionContextHolder.call(new TransactionContext(true), () -> {
            proxy.getConnection();

            Connection bound = (Connection) TransactionSynchronizationManager.getResource(dataSource);
            assertNotNull(bound, "a connection must be bound on join");
            assertFalse(bound.getAutoCommit());
            bound.close();
            return null;
        });
    }

    private DataSource newDataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
