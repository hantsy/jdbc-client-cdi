package io.github.hantsy.jdbc;

/**
 * Thrown by {@code single()} / {@code optional()} when the result set has an unexpected number of rows.
 */
public class IncorrectResultSizeException extends RuntimeException {

    public IncorrectResultSizeException(String message) {
        super(message);
    }
}
