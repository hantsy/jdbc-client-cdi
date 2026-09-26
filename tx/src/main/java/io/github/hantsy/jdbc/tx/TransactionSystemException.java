package io.github.hantsy.jdbc.tx;

/**
 * Wraps a failure while starting, committing, or rolling back a transaction.
 */
public class TransactionSystemException extends TransactionException {

    private final Throwable originalException;

    public TransactionSystemException(String message, Throwable cause) {
        super(message, cause);
        this.originalException = cause;
    }

    /**
     * The underlying failure (for example, a {@code SQLException}).
     */
    public Throwable getOriginalException() {
        return originalException;
    }
}
