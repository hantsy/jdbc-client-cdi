package io.github.hantsy.jdbc.tx;

import jakarta.transaction.Transactional;

/**
 * Immutable transaction attributes, built from a {@link Transactional} annotation and consumed by
 * {@link PlatformTransactionManager#getTransaction(TransactionDefinition)}.
 *
 * <p>Defaults to {@code REQUIRED} with empty rollback rules.</p>
 */
public record TransactionDefinition(Transactional.TxType propagation,
                                    Class<? extends Throwable>[] rollbackOn,
                                    Class<? extends Throwable>[] dontRollbackOn) {

    private static final Class<? extends Throwable>[] EMPTY = new Class[0];

    public static final TransactionDefinition DEFAULT =
            new TransactionDefinition(Transactional.TxType.REQUIRED, EMPTY, EMPTY);

    public TransactionDefinition {
        rollbackOn = rollbackOn == null ? EMPTY : rollbackOn;
        dontRollbackOn = dontRollbackOn == null ? EMPTY : dontRollbackOn;
    }

    public TransactionDefinition(Transactional annotation) {
        this(annotation == null ? Transactional.TxType.REQUIRED : annotation.value(),
                annotation == null ? EMPTY : annotation.rollbackOn(),
                annotation == null ? EMPTY : annotation.dontRollbackOn());
    }
}
