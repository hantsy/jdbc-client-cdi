package io.github.hantsy.jdbc.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;

/**
 * Provides an in-memory H2 {@link DataSource} for the CDI unit tests.
 */
@ApplicationScoped
public class TestDataSourceProducer {

    @Produces
    @ApplicationScoped
    public DataSource dataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }
}
