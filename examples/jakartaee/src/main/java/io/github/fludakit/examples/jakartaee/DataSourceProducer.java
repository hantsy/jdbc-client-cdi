package io.github.fludakit.examples.jakartaee;

import javax.sql.DataSource;
import jakarta.annotation.Resource;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Declares a self-contained PostgreSQL {@link DataSource} under the portable {@code java:comp/MyDS}
 * JNDI name and exposes it as a CDI bean.
 */
@DataSourceDefinition(
        name = "java:comp/MyDS",
        className = "org.postgresql.ds.PGSimpleDataSource", // org.postgresql.xa.PGXADataSource for JTA multiple ds
        url = "jdbc:postgresql://localhost:5432/postgres",
        user = "postgres",
        password = "postgres"
)
@ApplicationScoped
public class DataSourceProducer {

    @Resource(lookup = "java:comp/MyDS")
    private DataSource dataSource;

    @Produces
    @ApplicationScoped
    public DataSource expose() {
        return dataSource;
    }
}
