package io.github.fludakit.examples.servlet;

import javax.sql.DataSource;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Bridges the JNDI {@link DataSource} declared in {@code META-INF/context.xml} to a CDI bean so the
 * {@code cdi} module's {@code JdbcClientProducer} can consume it, and creates the table on startup.
 */
@ApplicationScoped
public class DataSourceProducer {

    @Resource(name = "jdbc/myDS")
    private DataSource dataSource;

    @Produces
    @ApplicationScoped
    public DataSource expose() {
        return dataSource;
    }
}
