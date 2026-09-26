package io.github.hantsy.jdbc.tx.it.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class EventPublisher {

    @Inject
    private Event<TestEvent> event;

    @Transactional
    public void publishAndCommit(String payload) {
        event.fire(new TestEvent(payload));
    }

    @Transactional
    public void publishAndRollback(String payload) {
        event.fire(new TestEvent(payload));
        throw new RuntimeException("rollback");
    }

    public void publishWithoutTransaction(String payload) {
        event.fire(new TestEvent(payload));
    }
}
