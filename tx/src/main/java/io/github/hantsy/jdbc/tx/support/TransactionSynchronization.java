package io.github.hantsy.jdbc.tx.support;

public interface TransactionSynchronization {
    default void beforeCommit(boolean readOnly) {}
    default void beforeCompletion() {}
    default void afterCommit() {}
    default void afterCompletion(CompletionStatus status) {}

    enum CompletionStatus {
        COMMITTED, ROLLED_BACK, UNKNOWN
    }
}
