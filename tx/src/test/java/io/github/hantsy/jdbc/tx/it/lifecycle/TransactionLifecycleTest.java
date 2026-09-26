package io.github.hantsy.jdbc.tx.it.lifecycle;

import io.github.hantsy.jdbc.tx.PlatformTransactionManager;
import io.github.hantsy.jdbc.tx.TransactionDefinition;
import io.github.hantsy.jdbc.tx.TransactionSystemException;
import io.github.hantsy.jdbc.tx.cdi.TransactionalCdiExtension;
import io.github.hantsy.jdbc.tx.cdi.TransactionalInterceptor;
import io.github.hantsy.jdbc.tx.support.TransactionContext;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronization;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronizationManager;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Drives the interceptor lifecycle against a stub {@link PlatformTransactionManager} to verify the
 * completion contract (exactly-once completion, rollback-only, begin/commit/rollback failures).
 */
@ExtendWith(WeldJunit5Extension.class)
class TransactionLifecycleTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
            StubTransactionManager.class,
            LifecycleService.class,
            TransactionalInterceptor.class,
            TransactionalCdiExtension.class
    ).build();

    @Inject
    private StubTransactionManager manager;

    @Inject
    private LifecycleService service;

    @BeforeEach
    void reset() {
        manager.reset();
    }

    @Test
    void commitFailureDoesNotDoubleComplete() {
        manager.commitFailure = new TransactionSystemException("boom", new RuntimeException("cause"));

        assertThrows(TransactionSystemException.class, service::succeeds);

        assertEquals(1, manager.commitCount);
        assertEquals(0, manager.rollbackCount);
        assertEquals(1, manager.afterCompletionCount);
    }

    @Test
    void rollbackOnlyForcesRollback() {
        service.marksRollbackOnly();

        assertEquals(0, manager.commitCount);
        assertEquals(1, manager.rollbackCount);
        assertEquals(1, manager.afterCompletionCount);
    }

    @Test
    void beginFailureDoesNotComplete() {
        manager.beginFailure = new TransactionSystemException("begin", new RuntimeException("cause"));

        assertThrows(TransactionSystemException.class, service::succeeds);

        assertEquals(0, manager.commitCount);
        assertEquals(0, manager.rollbackCount);
        assertEquals(0, manager.afterCompletionCount);
    }

    @Test
    void rollbackFailureFiresUnknownCompletion() {
        manager.rollbackFailure = new TransactionSystemException("rollback", new RuntimeException("cause"));

        assertThrows(TransactionSystemException.class, service::throwsRuntime);

        assertEquals(1, manager.rollbackCount);
        assertEquals(1, manager.afterCompletionCount);
    }

    @Singleton
    public static class StubTransactionManager implements PlatformTransactionManager {
        int beginCount;
        int commitCount;
        int rollbackCount;
        int afterCompletionCount;
        RuntimeException beginFailure;
        RuntimeException commitFailure;
        RuntimeException rollbackFailure;

        void reset() {
            beginCount = 0;
            commitCount = 0;
            rollbackCount = 0;
            afterCompletionCount = 0;
            beginFailure = null;
            commitFailure = null;
            rollbackFailure = null;
        }

        @Override
        public TransactionContext getTransaction(TransactionDefinition definition) {
            beginCount++;
            if (beginFailure != null) {
                throw beginFailure;
            }
            TransactionContext context = new TransactionContext(true);
            context.getSynchronizations().add(new TransactionSynchronization() {
                @Override
                public void afterCompletion(CompletionStatus status) {
                    afterCompletionCount++;
                }
            });
            return context;
        }

        @Override
        public void commit(TransactionContext context) {
            commitCount++;
            if (commitFailure != null) {
                throw commitFailure;
            }
        }

        @Override
        public void rollback(TransactionContext context) {
            rollbackCount++;
            if (rollbackFailure != null) {
                throw rollbackFailure;
            }
        }
    }

    @Singleton
    public static class LifecycleService {
        @Transactional
        public void succeeds() {
        }

        @Transactional
        public void marksRollbackOnly() {
            TransactionSynchronizationManager.setRollbackOnly();
        }

        @Transactional
        public void throwsRuntime() {
            throw new IllegalStateException("business");
        }
    }
}
