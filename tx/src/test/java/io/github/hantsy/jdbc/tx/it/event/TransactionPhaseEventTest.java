package io.github.hantsy.jdbc.tx.it.event;

import io.github.hantsy.jdbc.tx.cdi.TransactionalCdiExtension;
import io.github.hantsy.jdbc.tx.cdi.TransactionalInterceptor;
import io.github.hantsy.jdbc.tx.it.config.ApplicationDatabaseConfig;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(WeldJunit5Extension.class)
class TransactionPhaseEventTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
            ApplicationDatabaseConfig.class,
            EventObserver.class,
            EventPublisher.class,
            TransactionalInterceptor.class,
            TransactionalCdiExtension.class
    ).build();

    @Inject
    private EventObserver observer;
    @Inject
    private EventPublisher publisher;

    @BeforeEach
    void reset() {
        observer.reset();
    }

    @Test
    void afterSuccessFiresOnCommit() {
        publisher.publishAndCommit("ok");

        assertEquals(List.of("IN_PROGRESS", "BEFORE_COMPLETION", "AFTER_SUCCESS", "AFTER_COMPLETION"), observer.getCalls());
    }

    @Test
    void afterFailureFiresOnRollback() {
        assertThrows(RuntimeException.class, () -> publisher.publishAndRollback("fail"));

        assertEquals(List.of("IN_PROGRESS", "BEFORE_COMPLETION", "AFTER_FAILURE", "AFTER_COMPLETION"), observer.getCalls());
    }

    @Test
    void transactionalObserverDoesNotFireWithoutTransaction() {
        publisher.publishWithoutTransaction("no-tx");

        assertEquals(List.of("IN_PROGRESS"), observer.getCalls());
    }
}
