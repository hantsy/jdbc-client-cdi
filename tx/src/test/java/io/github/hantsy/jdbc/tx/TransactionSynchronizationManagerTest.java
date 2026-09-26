package io.github.hantsy.jdbc.tx;

import io.github.hantsy.jdbc.tx.support.TransactionContext;
import io.github.hantsy.jdbc.tx.support.TransactionContextHolder;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronization;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionSynchronizationManagerTest {

    @Test
    void bindGetUnbindResource() throws Exception {
        TransactionContextHolder.call(new TransactionContext(true), () -> {
            Object key = new Object();
            Object value = new Object();

            TransactionSynchronizationManager.bindResource(key, value);
            assertSame(value, TransactionSynchronizationManager.getResource(key));
            assertSame(value, TransactionSynchronizationManager.unbindResource(key));
            assertNull(TransactionSynchronizationManager.getResource(key));
            return null;
        });
    }

    @Test
    void isActualTransactionActiveReflectsState() throws Exception {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());

        TransactionContextHolder.call(new TransactionContext(true), () -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        });

        TransactionContextHolder.call(TransactionContext.NON_TRANSACTIONAL, () -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        });

        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void registerAndTriggerInOrder() throws Exception {
        TransactionContextHolder.call(new TransactionContext(true), () -> {
            List<String> calls = new ArrayList<>();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    calls.add("beforeCommit");
                }

                @Override
                public void beforeCompletion() {
                    calls.add("beforeCompletion");
                }

                @Override
                public void afterCommit() {
                    calls.add("afterCommit");
                }

                @Override
                public void afterCompletion(CompletionStatus status) {
                    calls.add("afterCompletion:" + status);
                }
            });

            TransactionSynchronizationManager.triggerBeforeCommit(false);
            TransactionSynchronizationManager.triggerBeforeCompletion();
            TransactionSynchronizationManager.triggerAfterCommit();
            TransactionSynchronizationManager.triggerAfterCompletion(TransactionSynchronization.CompletionStatus.COMMITTED);

            assertEquals(List.of("beforeCommit", "beforeCompletion", "afterCommit", "afterCompletion:COMMITTED"), calls);
            return null;
        });
    }

    @Test
    void rollbackOnlyFlag() throws Exception {
        TransactionContextHolder.call(new TransactionContext(true), () -> {
            assertFalse(TransactionSynchronizationManager.isRollbackOnly());
            TransactionSynchronizationManager.setRollbackOnly();
            assertTrue(TransactionSynchronizationManager.isRollbackOnly());
            return null;
        });
    }
}
