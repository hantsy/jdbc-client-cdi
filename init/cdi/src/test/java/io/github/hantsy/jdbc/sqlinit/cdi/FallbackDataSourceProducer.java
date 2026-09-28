package io.github.hantsy.jdbc.sqlinit.cdi;

import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Provides a single unqualified in-memory H2 {@link DataSource}, so the extension has to fall back
 * to it.
 */
@ApplicationScoped
public class FallbackDataSourceProducer {

    @Produces
    @ApplicationScoped
    public DataSource dataSource() {
        return TestDatabases.h2("sqlinit_fallback");
    }
}
