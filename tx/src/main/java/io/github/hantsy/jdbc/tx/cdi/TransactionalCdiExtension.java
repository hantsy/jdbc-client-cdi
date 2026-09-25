package io.github.hantsy.jdbc.tx.cdi;

import io.github.hantsy.jdbc.tx.support.TransactionContextHolder;
import io.github.hantsy.jdbc.tx.support.TransactionEventNotifier;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronizationManager;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessObserverMethod;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;
import jakarta.transaction.Transactional;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Portable extension that wires {@code jakarta.transaction.Transactional} to the CDI container:
 *
 * <ul>
 *   <li>Adds the internal {@link TransactionalBinding} to every {@code @Transactional} type, so the
 *       single {@link TransactionalInterceptor} matches all six {@code TxType} values.</li>
 *   <li>Vetoes every {@code @Observes(during != IN_PROGRESS)} observer and replays it at the matching
 *       phase through a {@link TransactionEventNotifier}.</li>
 * </ul>
 */
public class TransactionalCdiExtension implements Extension {

    private final List<CollectedObserver> transactionalObservers = new ArrayList<>();

    public <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> event) {
        AnnotatedType<T> annotatedType = event.getAnnotatedType();
        if (hasTransactional(annotatedType)) {
            event.configureAnnotatedType().add(TransactionalBinding.Literal.INSTANCE);
        }
    }

    public <T, X> void processObserverMethod(@Observes ProcessObserverMethod<T, X> event) {
        ObserverMethod<T> observer = event.getObserverMethod();
        if (observer.getTransactionPhase() != TransactionPhase.IN_PROGRESS) {
            event.veto();
            transactionalObservers.add(new CollectedObserver(observer.getTransactionPhase(), observer));
        }
    }

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        TransactionEventNotifier notifier = new TransactionEventNotifier();
        for (CollectedObserver collected : transactionalObservers) {
            notifier.register(collected.phase(), collected.observer());
        }

        event.addBean()
                .types(TransactionEventNotifier.class)
                .scope(Singleton.class)
                .createWith(ctx -> notifier);

        event.addObserverMethod(new CapturingObserver());
    }

    private static <T> boolean hasTransactional(AnnotatedType<T> annotatedType) {
        if (annotatedType.isAnnotationPresent(Transactional.class)) {
            return true;
        }
        for (AnnotatedMethod<? super T> method : annotatedType.getMethods()) {
            if (method.isAnnotationPresent(Transactional.class)) {
                return true;
            }
        }
        return false;
    }

    private record CollectedObserver(TransactionPhase phase, ObserverMethod<?> observer) {
    }

    private static final class CapturingObserver implements ObserverMethod<Object> {

        @Override
        public Class<?> getBeanClass() {
            return TransactionalCdiExtension.class;
        }

        @Override
        public Type getObservedType() {
            return Object.class;
        }

        @Override
        public Set<Annotation> getObservedQualifiers() {
            return Set.of(Any.Literal.INSTANCE);
        }

        @Override
        public Reception getReception() {
            return Reception.ALWAYS;
        }

        @Override
        public TransactionPhase getTransactionPhase() {
            return TransactionPhase.IN_PROGRESS;
        }

        @Override
        public int getPriority() {
            return Interceptor.Priority.APPLICATION + 500;
        }

        @Override
        public void notify(Object event) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionContextHolder.get().getEventStore().add(event);
            }
        }
    }
}
