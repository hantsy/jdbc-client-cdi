package io.github.hantsy.jdbc;

/**
 * Thrown when a query returns a number of rows other than expected, e.g. {@code single()} expected
 * exactly one row but got zero or more than one.
 *
 * <p>{@link #getActualSize()} is {@code -1} when the exact row count is not known (the "more than
 * expected" cases).
 */
public class IncorrectResultSizeException extends DataAccessException {

    private final int expectedSize;
    private final int actualSize;

    public IncorrectResultSizeException(int expectedSize, int actualSize) {
        this("Incorrect result size: expected " + expectedSize + " but got " + actualSize, expectedSize, actualSize);
    }

    public IncorrectResultSizeException(String message, int expectedSize, int actualSize) {
        super(message);
        this.expectedSize = expectedSize;
        this.actualSize = actualSize;
    }

    public int getExpectedSize() {
        return expectedSize;
    }

    public int getActualSize() {
        return actualSize;
    }
}
