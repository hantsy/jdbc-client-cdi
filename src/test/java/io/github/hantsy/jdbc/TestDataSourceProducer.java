package io.github.hantsy.jdbc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;

@ApplicationScoped
public class TestDataSourceProducer {

    /**
     * Produces a highly performant, embedded H2 driver proxy instance directly into the test container.
     */
    @Produces
    @ApplicationScoped
    public DataSource produceTestDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }
}
