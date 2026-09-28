package io.github.hantsy.jdbc.tx;

/**
 * Base unchecked exception for transaction failures, replacing raw {@code throws Exception}.
 */
public class TransactionException extends RuntimeException {

    public TransactionException(String message) {
        super(message);
    }

    public TransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
