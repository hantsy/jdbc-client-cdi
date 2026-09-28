package io.github.hantsy.jdbc.tx;

import io.github.hantsy.jdbc.tx.support.TransactionContext;
import io.github.hantsy.jdbc.tx.support.TransactionContextHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionContextHolderTest {

    @Test
    void callBindsAndRestores() throws Exception {
        assertFalse(TransactionContextHolder.isBound());
        assertNull(TransactionContextHolder.get());

        TransactionContextHolder.call(new TransactionContext(true), () -> {
            assertTrue(TransactionContextHolder.isBound());
            assertNotNull(TransactionContextHolder.get());
            return null;
        });

        assertFalse(TransactionContextHolder.isBound());
        assertNull(TransactionContextHolder.get());
    }

    @Test
    void nestedCallShadowsAndRestores() throws Exception {
        TransactionContext outer = new TransactionContext(true);
        TransactionContext inner = new TransactionContext(true);

        TransactionContextHolder.call(outer, () -> {
            assertSame(outer, TransactionContextHolder.get());

            TransactionContextHolder.call(inner, () -> {
                assertSame(inner, TransactionContextHolder.get());
                return null;
            });

            assertSame(outer, TransactionContextHolder.get(), "outer must be restored after the inner scope");
            return null;
        });

        assertFalse(TransactionContextHolder.isBound());
    }

    @Test
    void nonTransactionalContextIsStillBound() throws Exception {
        TransactionContextHolder.call(TransactionContext.NON_TRANSACTIONAL, () -> {
            assertTrue(TransactionContextHolder.isBound());
            assertSame(TransactionContext.NON_TRANSACTIONAL, TransactionContextHolder.get());
            assertFalse(TransactionContextHolder.get().isActualTransactionActive());
            return null;
        });

        assertFalse(TransactionContextHolder.isBound());
    }
}
