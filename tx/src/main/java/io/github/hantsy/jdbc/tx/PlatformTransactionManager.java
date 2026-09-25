package io.github.hantsy.jdbc.tx;

import io.github.hantsy.jdbc.tx.support.TransactionContext;

/**
 * Strategy SPI for resource-local transaction management.
 *
 * <p>An implementation drives the begin/commit/rollback of a single resource type (for example, a
 * JDBC {@code DataSource}). It does not interpret propagation rules: the
 * {@link io.github.hantsy.jdbc.tx.cdi.TransactionalInterceptor}
 * resolves {@code TxType} and only asks the manager to {@link #getTransaction(TransactionDefinition) start}
 * a new transaction when one is needed.</p>
 *
 * <p>The {@link TransactionContext} returned by {@code getTransaction} is the single handle for the
 * transaction — it carries the bound resource, synchronizations, and completion flags.</p>
 */
public interface PlatformTransactionManager {

    /**
     * Begin a new resource-local transaction, bind its resource, and return the transaction context.
     *
     * @param definition transaction attributes (propagation, rollback rules)
     * @return the transaction context for the started transaction
     * @throws TransactionException if the transaction could not be started
     */
    TransactionContext getTransaction(TransactionDefinition definition) throws TransactionException;

    /**
     * Commit the transaction represented by the given context.
     */
    void commit(TransactionContext context) throws TransactionException;

    /**
     * Roll back the transaction represented by the given context.
     */
    void rollback(TransactionContext context) throws TransactionException;
}
