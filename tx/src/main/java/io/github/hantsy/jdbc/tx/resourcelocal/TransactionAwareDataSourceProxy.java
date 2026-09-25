package io.github.hantsy.jdbc.tx.resourcelocal;

import io.github.hantsy.jdbc.tx.TransactionSystemException;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronization;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link DataSource} decorator that makes a plain pool transaction-aware.
 *
 * <p>When a transaction is active and this pool is bound, {@link #getConnection()} returns a
 * close-suppressing proxy over the transaction-bound connection. If this pool is not yet bound it
 * joins the current transaction by opening and binding a connection (best-effort, non-XA). Outside
 * a transaction it delegates straight to the pool.</p>
 *
 * <p>The delegate must be the <em>same</em> raw {@code DataSource} instance that the
 * {@link DataSourceTransactionManager} uses as its bind key.</p>
 */
public class TransactionAwareDataSourceProxy implements DataSource {

    private static final Logger LOG = Logger.getLogger(TransactionAwareDataSourceProxy.class.getName());

    private final DataSource delegate;

    public TransactionAwareDataSourceProxy(DataSource delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            Connection txConnection = (Connection) TransactionSynchronizationManager.getResource(delegate);
            if (txConnection == null) {
                txConnection = joinTransaction();
            }
            return closeSuppressingProxy(txConnection);
        }
        return delegate.getConnection();
    }

    private Connection joinTransaction() throws SQLException {
        Connection connection = delegate.getConnection();
        boolean bound = false;
        try {
            connection.setAutoCommit(false);
            TransactionSynchronizationManager.bindResource(delegate, connection);
            TransactionSynchronizationManager.registerSynchronization(new JoiningSynchronization(connection));
            bound = true;
            return connection;
        } finally {
            if (!bound) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    LOG.log(Level.WARNING, "Failed to close connection after failed join", ex);
                }
            }
        }
    }

    private static Connection closeSuppressingProxy(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static final class JoiningSynchronization implements TransactionSynchronization {

        private final Connection connection;

        JoiningSynchronization(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            try {
                connection.commit();
            } catch (SQLException ex) {
                throw new TransactionSystemException("Could not commit joined JDBC transaction", ex);
            }
        }

        @Override
        public void afterCompletion(CompletionStatus status) {
            try {
                if (status == CompletionStatus.ROLLED_BACK) {
                    connection.rollback();
                }
                if (!connection.getAutoCommit()) {
                    connection.setAutoCommit(true);
                }
                connection.close();
            } catch (SQLException ex) {
                LOG.log(Level.WARNING, "Failed to clean up joined JDBC Connection", ex);
            }
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
