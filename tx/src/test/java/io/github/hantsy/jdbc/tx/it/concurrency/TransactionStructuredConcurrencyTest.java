package io.github.hantsy.jdbc.tx.it.concurrency;

import io.github.hantsy.jdbc.tx.cdi.TransactionalCdiExtension;
import io.github.hantsy.jdbc.tx.cdi.TransactionalInterceptor;
import io.github.hantsy.jdbc.tx.it.config.ApplicationDatabaseConfig;
import io.github.hantsy.jdbc.tx.it.service.IsolatedChildService;
import io.github.hantsy.jdbc.tx.it.service.OrderProcessingService;
import io.github.hantsy.jdbc.tx.support.TransactionContextHolder;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(WeldJunit5Extension.class)
class TransactionStructuredConcurrencyTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
            ApplicationDatabaseConfig.class,
            OrderProcessingService.class,
            IsolatedChildService.class,
            TransactionalInterceptor.class,
            TransactionalCdiExtension.class
    ).build();

    @Inject
    private OrderProcessingService orderService;

    @Test
    void verifyTransactionContextInheritedByVirtualSubtasks() throws Exception {
        assertFalse(TransactionContextHolder.isBound());

        boolean inheritanceResult = orderService.processParallelInTransaction();

        assertTrue(inheritanceResult, "Concurrent subtasks must inherit the transaction context.");
        assertFalse(TransactionContextHolder.isBound(), "Context must dissolve on exit.");
    }
}
