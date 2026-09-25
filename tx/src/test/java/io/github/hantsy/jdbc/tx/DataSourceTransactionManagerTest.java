package io.github.hantsy.jdbc.tx;

import io.github.hantsy.jdbc.tx.resourcelocal.DataSourceTransactionManager;
import io.github.hantsy.jdbc.tx.support.TransactionContext;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronization;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

class DataSourceTransactionManagerTest {

    @Test
    void getTransactionBindsConnectionWithAutoCommitDisabled() throws Exception {
        DataSource dataSource = newDataSource("dsmgr1");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);

        TransactionContext context = manager.getTransaction(TransactionDefinition.DEFAULT);
        assertTrue(context.isActualTransactionActive());

        Connection bound = (Connection) context.getResources().get(dataSource);
        assertNotNull(bound);
        assertFalse(bound.getAutoCommit());
        bound.close();
    }

    @Test
    void commitAndRollbackDelegateToConnection() throws Exception {
        DataSource dataSource = newDataSource("dsmgr2");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);

        TransactionContext context = manager.getTransaction(TransactionDefinition.DEFAULT);
        assertDoesNotThrow(() -> manager.commit(context));
        assertDoesNotThrow(() -> manager.rollback(context));

        Connection bound = (Connection) context.getResources().get(dataSource);
        bound.close();
    }

    @Test
    void afterCompletionResetsAndClosesConnection() throws Exception {
        DataSource dataSource = newDataSource("dsmgr3");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);

        TransactionContext context = manager.getTransaction(TransactionDefinition.DEFAULT);
        Connection bound = (Connection) context.getResources().get(dataSource);

        context.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.CompletionStatus.COMMITTED));

        assertTrue(bound.isClosed());
    }

    private DataSource newDataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
