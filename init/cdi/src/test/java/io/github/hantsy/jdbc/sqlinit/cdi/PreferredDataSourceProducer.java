package io.github.hantsy.jdbc.sqlinit.cdi;

import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Provides two in-memory H2 data sources, so the extension has to prefer the {@link SqlInit}
 * qualified one over the default.
 */
@ApplicationScoped
public class PreferredDataSourceProducer {

    @Produces
    @ApplicationScoped
    public DataSource dataSource() {
        return TestDatabases.h2("sqlinit_preferred_default");
    }

    @Produces
    @ApplicationScoped
    @SqlInit
    public DataSource sqlInitDataSource() {
        return TestDatabases.h2("sqlinit_preferred_qualified");
    }
}
