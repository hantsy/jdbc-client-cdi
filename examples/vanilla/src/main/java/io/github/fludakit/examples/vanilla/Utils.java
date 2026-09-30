package io.github.fludakit.examples.vanilla;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;

/**
 * Creates the in-memory H2 {@link DataSource} shared by the example and its test.
 */
public final class Utils {

    private Utils() {
    }

    public static DataSource newDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:vanilla;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }
}
