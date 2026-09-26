package io.github.hantsy.jdbc.tx.it.propagation;

import io.github.hantsy.jdbc.tx.cdi.TransactionalCdiExtension;
import io.github.hantsy.jdbc.tx.cdi.TransactionalInterceptor;
import io.github.hantsy.jdbc.tx.it.config.ApplicationDatabaseConfig;
import io.github.hantsy.jdbc.tx.it.service.InnerService;
import io.github.hantsy.jdbc.tx.it.service.OuterService;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.inject.Inject;
import jakarta.transaction.TransactionalException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(WeldJunit5Extension.class)
class PropagationTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
            ApplicationDatabaseConfig.class,
            InnerService.class,
            OuterService.class,
            TransactionalInterceptor.class,
            TransactionalCdiExtension.class
    ).build();

    @Inject
    private InnerService inner;
    @Inject
    private OuterService outer;

    @Test
    void requiredStartsTransactionWhenAbsent() {
        assertTrue(inner.required());
    }

    @Test
    void requiredJoinsExistingTransaction() {
        assertTrue(outer.callRequired());
    }

    @Test
    void requiresNewStartsNewTransaction() {
        assertTrue(outer.callRequiresNew());
    }

    @Test
    void supportsJoinsWhenActive() {
        assertTrue(outer.callSupports());
    }

    @Test
    void supportsRunsWithoutTransactionWhenAbsent() {
        assertFalse(inner.supports());
    }

    @Test
    void notSupportedSuspendsActiveTransaction() {
        assertFalse(outer.callNotSupported());
    }

    @Test
    void mandatoryThrowsWithoutTransaction() {
        assertThrows(TransactionalException.class, () -> inner.mandatory());
    }

    @Test
    void mandatoryProceedsWithTransaction() {
        assertDoesNotThrow(() -> outer.callMandatory());
    }

    @Test
    void neverProceedsWithoutTransaction() {
        assertDoesNotThrow(() -> inner.never());
    }

    @Test
    void neverThrowsWithTransaction() {
        assertThrows(TransactionalException.class, () -> outer.callNever());
    }
}
