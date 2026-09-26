package io.github.hantsy.jdbc.tx.it.event;

import java.util.ArrayList;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

@ApplicationScoped
public class EventObserver {

    private final List<String> calls = new ArrayList<>();

    public List<String> getCalls() {
        return List.copyOf(calls);
    }

    public void reset() {
        calls.clear();
    }

    public void onInProgress(@Observes TestEvent event) {
        calls.add("IN_PROGRESS");
    }

    public void onBeforeCompletion(@Observes(during = TransactionPhase.BEFORE_COMPLETION) TestEvent event) {
        calls.add("BEFORE_COMPLETION");
    }

    public void onAfterSuccess(@Observes(during = TransactionPhase.AFTER_SUCCESS) TestEvent event) {
        calls.add("AFTER_SUCCESS");
    }

    public void onAfterFailure(@Observes(during = TransactionPhase.AFTER_FAILURE) TestEvent event) {
        calls.add("AFTER_FAILURE");
    }

    public void onAfterCompletion(@Observes(during = TransactionPhase.AFTER_COMPLETION) TestEvent event) {
        calls.add("AFTER_COMPLETION");
    }
}
