package io.github.hantsy.jdbc.sqlinit.cdi;

import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Provides an in-memory H2 {@link DataSource} for a database no test touches directly, so only the
 * container's startup notification can have populated it.
 */
@ApplicationScoped
public class StartupDataSourceProducer {

    @Produces
    @ApplicationScoped
    public DataSource dataSource() {
        return TestDatabases.h2("sqlinit_startup");
    }
}
