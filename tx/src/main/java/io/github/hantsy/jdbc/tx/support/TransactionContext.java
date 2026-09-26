package io.github.hantsy.jdbc.tx.support;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The per-transaction context/status — the single object that captures one resource-local
 * transaction.
 *
 * <p>Created and populated by a {@link io.github.hantsy.jdbc.tx.PlatformTransactionManager}, bound
 * to the thread/scope by {@link TransactionContextHolder}, and mutated through
 * {@link TransactionSynchronizationManager}. It holds the bound
 * resources, the registered synchronizations, the deferred event payloads, and the rollback-only
 * and completed flags.</p>
 */
public final class TransactionContext {

    /**
     * Shared context representing "no active transaction" (used by NOT_SUPPORTED/NEVER/SUPPORTS).
     */
    public static final TransactionContext NON_TRANSACTIONAL = new TransactionContext(false);

    private final boolean actualTransactionActive;
    private final Map<Object, Object> resources = new IdentityHashMap<>();
    private final List<TransactionSynchronization> synchronizations = new ArrayList<>();
    private final TransactionEventStore eventStore = new TransactionEventStore();
    private volatile boolean rollbackOnly;
    private volatile boolean completed;

    public TransactionContext(boolean actualTransactionActive) {
        this.actualTransactionActive = actualTransactionActive;
    }

    public boolean isActualTransactionActive() {
        return actualTransactionActive;
    }

    /**
     * Resources bound by owner identity (e.g. DataSource -> Connection).
     */
    public Map<Object, Object> getResources() {
        return resources;
    }

    public List<TransactionSynchronization> getSynchronizations() {
        return synchronizations;
    }

    public TransactionEventStore getEventStore() {
        return eventStore;
    }

    public boolean isRollbackOnly() {
        return rollbackOnly;
    }

    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void markCompleted() {
        this.completed = true;
    }
}
