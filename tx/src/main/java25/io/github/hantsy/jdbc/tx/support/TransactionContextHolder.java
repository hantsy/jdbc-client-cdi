package io.github.hantsy.jdbc.tx.support;

import java.util.concurrent.Callable;

/**
 * Holds the current {@link TransactionContext} for the calling thread/scope.
 *
 * <p>JDK 25+ implementation. {@code ScopedValue} is the primary carrier (so the state propagates
 * to {@code StructuredTaskScope} subtasks); an inheritable ThreadLocal is retained as a fallback
 * for threads created by pre-existing executors, which ScopedValue does not reach.</p>
 */
public final class TransactionContextHolder {

    private static final ScopedValue<TransactionContext> CURRENT = ScopedValue.newInstance();
    private static final InheritableThreadLocal<TransactionContext> FALLBACK = new InheritableThreadLocal<>();

    private TransactionContextHolder() {
    }

    /** Returns the bound context, or {@code null} if none is bound. */
    public static TransactionContext get() {
        return CURRENT.isBound() ? CURRENT.get() : FALLBACK.get();
    }

    /** Whether any context (including {@link TransactionContext#NON_TRANSACTIONAL}) is bound. */
    public static boolean isBound() {
        return CURRENT.isBound() || FALLBACK.get() != null;
    }

    /**
     * Binds the given context for the duration of the action, restoring the previous context afterwards.
     * Nested calls shadow and then restore the outer context.
     */
    public static <T> T call(TransactionContext context, Callable<T> action) throws Exception {
        TransactionContext previous = FALLBACK.get();
        FALLBACK.set(context);
        try {
            return ScopedValue.where(CURRENT, context).call(action::call);
        } finally {
            if (previous == null) {
                FALLBACK.remove();
            } else {
                FALLBACK.set(previous);
            }
        }
    }
}
