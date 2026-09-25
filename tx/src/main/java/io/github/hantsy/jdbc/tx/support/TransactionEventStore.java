package io.github.hantsy.jdbc.tx.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-transaction accumulator of deferred CDI event payloads.
 *
 * <p>Filled by the transaction-aware event capturing observer when an event is fired inside an
 * active transaction; drained by {@code TransactionEventNotifier} at each completion phase. Pure
 * storage — it knows nothing about observers, phases, or dispatch, and is discarded with its
 * transaction state.</p>
 */
public final class TransactionEventStore {

    private final List<Object> events = new ArrayList<>();

    /** Adds an event payload fired during the transaction. */
    public void add(Object payload) {
        events.add(payload);
    }

    /** Returns a snapshot of the buffered payloads. */
    public List<Object> payloads() {
        return List.copyOf(events);
    }

    /** Whether no events have been buffered. */
    public boolean isEmpty() {
        return events.isEmpty();
    }
}
