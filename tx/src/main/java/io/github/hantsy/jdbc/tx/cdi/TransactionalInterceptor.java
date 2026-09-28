package io.github.hantsy.jdbc.tx.cdi;

import io.github.hantsy.jdbc.tx.PlatformTransactionManager;
import io.github.hantsy.jdbc.tx.TransactionDefinition;
import io.github.hantsy.jdbc.tx.support.TransactionContext;
import io.github.hantsy.jdbc.tx.support.TransactionContextHolder;
import io.github.hantsy.jdbc.tx.support.TransactionEventNotifier;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronization;
import io.github.hantsy.jdbc.tx.support.TransactionSynchronizationManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionalException;

/**
 * CDI interceptor honoring {@code jakarta.transaction.Transactional}: resolves the annotation,
 * dispatches on {@code TxType}, and drives the resource-local transaction lifecycle (begin, bind,
 * run, complete, fire phase events).
 *
 * <p>Bound to the internal {@link TransactionalBinding} marker (added by
 * {@link TransactionalCdiExtension} to every {@code @Transactional} type) so a single interceptor
 * matches all six {@code TxType} values.</p>
 */
@Interceptor
@TransactionalBinding
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 10)
public class TransactionalInterceptor {

    @Inject
    private PlatformTransactionManager transactionManager;

    @Inject
    private TransactionEventNotifier eventNotifier;

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    // --- lifecycle ---

    @AroundInvoke
    public Object manageTransaction(InvocationContext context) throws Exception {
        TransactionDefinition definition = resolve(context);
        if (definition == null) {
            return context.proceed();
        }

        Transactional.TxType type = definition.propagation();
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();

        return switch (type) {
            case MANDATORY -> {
                if (!active) {
                    throw new TransactionalException("No active transaction (MANDATORY)", null);
                }
                yield context.proceed();
            }
            case NEVER -> {
                if (active) {
                    throw new TransactionalException("Active transaction not allowed (NEVER)", null);
                }
                yield context.proceed();
            }
            case SUPPORTS -> context.proceed();
            case NOT_SUPPORTED -> TransactionContextHolder.call(TransactionContext.NON_TRANSACTIONAL, context::proceed);
            case REQUIRES_NEW -> executeNew(context, definition);
            case REQUIRED -> active ? context.proceed() : executeNew(context, definition);
        };
    }

    private Object executeNew(InvocationContext invocation, TransactionDefinition definition) throws Exception {
        TransactionContext context = transactionManager.getTransaction(definition);
        return TransactionContextHolder.call(context, () -> {
            try {
                Object result = invocation.proceed();
                complete(context, definition, null);
                return result;
            } catch (Throwable failure) {
                complete(context, definition, failure);
                throw sneakyThrow(failure);
            }
        });
    }

    private void complete(TransactionContext context, TransactionDefinition definition, Throwable failure) {
        if (context.isCompleted()) {
            return;
        }
        boolean rollback = context.isRollbackOnly() || (failure != null && shouldRollback(failure, definition));
        if (rollback) {
            rollbackTransaction(context);
        } else {
            commitTransaction(context);
        }
    }

    private void commitTransaction(TransactionContext context) {
        TransactionSynchronizationManager.triggerBeforeCommit(false);
        TransactionSynchronizationManager.triggerBeforeCompletion();
        eventNotifier.notify(TransactionPhase.BEFORE_COMPLETION, context.getEventStore());
        try {
            transactionManager.commit(context);
        } catch (RuntimeException ex) {
            TransactionSynchronizationManager.triggerAfterCompletion(TransactionSynchronization.CompletionStatus.UNKNOWN);
            context.markCompleted();
            throw ex;
        }
        TransactionSynchronizationManager.triggerAfterCommit();
        TransactionSynchronizationManager.triggerAfterCompletion(TransactionSynchronization.CompletionStatus.COMMITTED);
        eventNotifier.notify(TransactionPhase.AFTER_SUCCESS, context.getEventStore());
        eventNotifier.notify(TransactionPhase.AFTER_COMPLETION, context.getEventStore());
        context.markCompleted();
    }

    private void rollbackTransaction(TransactionContext context) {
        TransactionSynchronizationManager.triggerBeforeCompletion();
        eventNotifier.notify(TransactionPhase.BEFORE_COMPLETION, context.getEventStore());
        try {
            transactionManager.rollback(context);
        } catch (RuntimeException ex) {
            TransactionSynchronizationManager.triggerAfterCompletion(TransactionSynchronization.CompletionStatus.UNKNOWN);
            context.markCompleted();
            throw ex;
        }
        TransactionSynchronizationManager.triggerAfterCompletion(TransactionSynchronization.CompletionStatus.ROLLED_BACK);
        eventNotifier.notify(TransactionPhase.AFTER_FAILURE, context.getEventStore());
        eventNotifier.notify(TransactionPhase.AFTER_COMPLETION, context.getEventStore());
        context.markCompleted();
    }

    // --- annotation resolution ---

    private boolean shouldRollback(Throwable failure, TransactionDefinition definition) {
        Throwable candidate = failure;
        while (candidate.getCause() != null
                && (candidate instanceof InvocationTargetException || candidate instanceof ExecutionException)) {
            candidate = candidate.getCause();
        }
        for (Class<? extends Throwable> type : definition.dontRollbackOn()) {
            if (type.isAssignableFrom(candidate.getClass())) {
                return false;
            }
        }
        for (Class<? extends Throwable> type : definition.rollbackOn()) {
            if (type.isAssignableFrom(candidate.getClass())) {
                return true;
            }
        }
        return candidate instanceof RuntimeException || candidate instanceof Error;
    }

    private TransactionDefinition resolve(InvocationContext context) {
        Transactional annotation = resolveAnnotation(context);
        return annotation == null ? null : new TransactionDefinition(annotation);
    }

    private Transactional resolveAnnotation(InvocationContext context) {
        Method method = context.getMethod();
        if (method != null) {
            Transactional annotation = method.getAnnotation(Transactional.class);
            if (annotation != null) {
                return annotation;
            }
            Class<?> targetType = context.getTarget() != null ? context.getTarget().getClass() : method.getDeclaringClass();
            annotation = findMethodAnnotation(targetType, method.getName(), method.getParameterTypes());
            if (annotation != null) {
                return annotation;
            }
            annotation = findOnType(method.getDeclaringClass());
            if (annotation != null) {
                return annotation;
            }
        }
        return context.getTarget() != null ? findOnType(context.getTarget().getClass()) : null;
    }

    private Transactional findMethodAnnotation(Class<?> type, String name, Class<?>[] parameterTypes) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Transactional annotation = methodAnnotation(current, name, parameterTypes);
            if (annotation != null) {
                return annotation;
            }
            for (Class<?> iface : current.getInterfaces()) {
                annotation = methodAnnotation(iface, name, parameterTypes);
                if (annotation != null) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private Transactional methodAnnotation(Class<?> type, String name, Class<?>[] parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes).getAnnotation(Transactional.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Transactional findOnType(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Transactional annotation = current.getAnnotation(Transactional.class);
            if (annotation != null) {
                return annotation;
            }
            for (Class<?> iface : current.getInterfaces()) {
                annotation = iface.getAnnotation(Transactional.class);
                if (annotation != null) {
                    return annotation;
                }
            }
        }
        return null;
    }
}
