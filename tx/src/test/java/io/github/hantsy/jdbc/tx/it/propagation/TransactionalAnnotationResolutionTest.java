package io.github.hantsy.jdbc.tx.it.propagation;

import io.github.hantsy.jdbc.tx.cdi.TransactionalCdiExtension;
import io.github.hantsy.jdbc.tx.cdi.TransactionalInterceptor;
import io.github.hantsy.jdbc.tx.it.config.ApplicationDatabaseConfig;
import io.github.hantsy.jdbc.tx.it.service.ConcreteService;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(WeldJunit5Extension.class)
class TransactionalAnnotationResolutionTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
            ApplicationDatabaseConfig.class,
            ConcreteService.class,
            TransactionalInterceptor.class,
            TransactionalCdiExtension.class
    ).build();

    @Inject
    private ConcreteService service;

    @Test
    void resolvesClassLevelAnnotationFromSuperclass() {
        assertTrue(service.active(), "Class-level @Transactional on the superclass must be honored.");
    }

    @Test
    void methodLevelAnnotationOverridesClassLevel() {
        assertFalse(service.notSupported(), "Method-level @Transactional must override the inherited class-level one.");
    }
}
