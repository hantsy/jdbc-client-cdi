package io.github.hantsy.jdbc.tx.support;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.spi.ObserverMethod;

/**
 * Resolves and notifies transactional CDI observers at a transaction phase.
 *
 * <p>Holds the vetoed {@code @Observes(during != IN_PROGRESS)} observers collected by
 * {@code TransactionalCdiExtension}, and replays the payloads captured in a
 * {@link TransactionEventStore} against them at the matching phase. Saving and firing are
 * deliberately decoupled: the store is inert per-transaction state; this notifier is the reusable
 * dispatcher invoked by the coordinator.</p>
 */
public final class TransactionEventNotifier {

    private static final Logger LOG = Logger.getLogger(TransactionEventNotifier.class.getName());

    private final List<Entry> observers = new ArrayList<>();

    private static boolean matches(ObserverMethod<?> observer, Object payload) {
        return rawType(observer.getObservedType()).isAssignableFrom(payload.getClass());
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return Object.class;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void notifySafely(ObserverMethod observer, Object payload) {
        try {
            observer.notify(payload);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Transactional observer failed to handle event", e);
        }
    }

    /**
     * Registers a transactional observer for a specific phase.
     */
    public void register(TransactionPhase phase, ObserverMethod<?> observer) {
        observers.add(new Entry(phase, observer));
    }

    /**
     * Notifies all matching observers for every payload in the store, at the given phase.
     */
    public void notify(TransactionPhase phase, TransactionEventStore store) {
        for (Object payload : store.payloads()) {
            notify(phase, payload);
        }
    }

    /**
     * Notifies all matching observers for a single payload, at the given phase.
     */
    public void notify(TransactionPhase phase, Object payload) {
        for (Entry entry : observers) {
            if (entry.phase == phase && matches(entry.observer, payload)) {
                notifySafely(entry.observer, payload);
            }
        }
    }

    private record Entry(TransactionPhase phase, ObserverMethod<?> observer) {
    }
}
