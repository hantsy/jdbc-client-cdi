package io.github.hantsy.jdbc.tx.support;

import java.util.concurrent.Callable;

/**
 * Holds the current {@link TransactionContext} for the calling thread/scope.
 *
 * <p>This is the base (JDK 21) implementation backed by an {@link InheritableThreadLocal}. On
 * JDK 25+ the multi-release override carries the state via {@code ScopedValue} so it also
 * propagates to structured-concurrency subtasks.</p>
 */
public final class TransactionContextHolder {

    private static final InheritableThreadLocal<TransactionContext> CURRENT = new InheritableThreadLocal<>();

    private TransactionContextHolder() {
    }

    /** Returns the bound context, or {@code null} if none is bound. */
    public static TransactionContext get() {
        return CURRENT.get();
    }

    /** Whether any context (including {@link TransactionContext#NON_TRANSACTIONAL}) is bound. */
    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    /**
     * Binds the given context for the duration of the action, restoring the previous context afterwards.
     * Nested calls shadow and then restore the outer context.
     */
    public static <T> T call(TransactionContext context, Callable<T> action) throws Exception {
        TransactionContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return action.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
