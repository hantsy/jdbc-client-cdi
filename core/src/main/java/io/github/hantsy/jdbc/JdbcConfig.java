package io.github.hantsy.jdbc;

/**
 * Plain configuration for {@link JdbcClient}.
 */
public class JdbcConfig {

    public static final JdbcConfig DEFAULT = new JdbcConfig("?", 0, 0);

    private String placeholder;
    private int queryTimeout;
    private int fetchSize;

    /** No-arg constructor required for CDI client proxies. */
    public JdbcConfig() {}

    public JdbcConfig(String placeholder, int queryTimeout, int fetchSize) {
        this.placeholder = placeholder;
        this.queryTimeout = queryTimeout;
        this.fetchSize = fetchSize;
    }

    /**
     * The JDBC placeholder symbol used when rewriting {@code :name} parameters
     * (default {@code ?}).
     *
     * <p>Supported symbols and how {@code :name} is rewritten:
     *
     * <ul>
     *   <li>{@code ?} — positional placeholder (MySQL and standard JDBC).
     *   <li>{@code $} — numbered {@code $1}, {@code $2}, … (H2/PostgreSQL).
     *   <li>{@code :} — numbered {@code :1}, {@code :2}, … (Oracle).
     *   <li>{@code @} — named {@code @name}, preserving the parameter name (SQL
     *       Server).
     * </ul>
     */
    public String placeholder() {
        return placeholder;
    }

    /** The default query timeout in seconds ({@code 0} = no timeout). */
    public int queryTimeout() {
        return queryTimeout;
    }

    /** The default fetch size hint ({@code 0} = driver default). */
    public int fetchSize() {
        return fetchSize;
    }
}
