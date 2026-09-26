package io.github.hantsy.jdbc.tx;

/**
 * Indicates a transaction was rolled back unexpectedly (for example, a participant marked it
 * rollback-only and the outer transaction then attempted to commit).
 */
public class UnexpectedRollbackException extends TransactionException {

    public UnexpectedRollbackException(String message) {
        super(message);
    }

    public UnexpectedRollbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
