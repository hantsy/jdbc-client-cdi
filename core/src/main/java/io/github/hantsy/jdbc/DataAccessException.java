package io.github.hantsy.jdbc;

/**
 * Root of the unchecked exceptions thrown by {@link JdbcClient}. Wraps the underlying
 * {@link java.sql.SQLException} (if any) as its cause.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
