package io.github.hantsy.jdbc;

/**
 * Unchecked exception raised while preparing or executing a {@link JdbcClient}
 * operation.
 *
 * <p>The code identifies the kind of failure without requiring callers to
 * depend on multiple exception subclasses. The original cause is retained when
 * the failure originated in JDBC or row-mapping code.
 */
public class JdbcClientException extends RuntimeException {

    /**
     * Stable failure codes exposed by {@link JdbcClient}.
     *
     * <p>Codes describe the failure contract rather than the underlying
     * implementation exception. Callers should use these values for
     * programmatic handling and use the exception message and cause for
     * diagnostics.
     */
    public enum Code {
        /** A failure that does not fit another specific code. */
        UNCATEGORIZED,

        /** A JDBC connection, statement, SQL, or result-set operation failed. */
        JDBC,

        /** A custom row mapper or reflective row-to-object mapping failed. */
        MAPPING_FAILURE,

        /**
         * A single-result operation expected one row but the query returned no
         * rows. This is emitted by {@code single()} and {@code
         * singleValue(...)} only.
         */
        NO_RESULT,

        /**
         * A single-result operation received more than one row. This is emitted
         * by {@code single()} and {@code optional()} only.
         *
         * <p>An empty list from {@code list()} and {@code Optional.empty()}
         * from {@code optional()} are successful results and do not produce
         * this code.
         */
        TOO_MANY_RESULTS
    }

    private final Code code;

    /**
     * Creates an exception without an underlying cause.
     *
     * @param code failure code
     * @param message diagnostic message
     */
    public JdbcClientException(Code code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Creates an exception preserving the underlying cause.
     *
     * @param code failure code
     * @param message diagnostic message
     * @param cause underlying failure
     */
    public JdbcClientException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Returns the stable code for this failure.
     *
     * @return failure code
     */
    public Code getCode() {
        return code;
    }
}
