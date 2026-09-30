package io.github.hantsy.jdbc.sqlinit.cdi;

import io.github.hantsy.jdbc.sqlinit.DbMigrator;
import io.github.hantsy.jdbc.sqlinit.SqlInitConfig;
import io.github.hantsy.jdbc.sqlinit.resource.ResourceResolver;
import io.github.hantsy.jdbc.sqlinit.resource.ResourceResolverRegistry;

import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Applies the configured SQL migrations when the application starts.
 *
 * <p>A plain CDI bean, not a portable extension: it observes the container's {@link Startup} event
 * and runs the migrations. The {@link DataSource} is resolved by preferring one qualified with
 * {@link SqlInit} and falling back to the default unqualified bean. Any user-provided
 * {@link ResourceResolver} bean whose {@link ResourceResolver#protocol()} is set is registered so
 * migrations can be loaded from additional protocols.</p>
 */
@ApplicationScoped
public class SqlInitBootstrapper {

    private static final Logger LOGGER = Logger.getLogger(SqlInitBootstrapper.class.getName());

    @Inject
    @SqlInit
    Instance<DataSource> qualifiedDataSource;

    @Inject
    Instance<DataSource> dataSource;

    @Inject
    Instance<SqlInitConfig> configs;

    @Inject
    Instance<ResourceResolver> resolvers;

    public void onStartup(@Observes Startup event) {
        run();
    }

    void run() {
        DataSource ds = resolveDataSource();
        if (ds == null) {
            LOGGER.warning("No DataSource bean found, skipping SQL script initialization");
            return;
        }
        SqlInitConfig config = configs.isResolvable() ? configs.get() : SqlInitConfig.defaults();
        try {
            new DbMigrator(ds, config, resourceResolver()).migrate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize the database from SQL scripts", e);
        }
    }

    private DataSource resolveDataSource() {
        if (!qualifiedDataSource.isUnsatisfied()) {
            LOGGER.fine("Initializing the @SqlInit qualified DataSource");
            return qualifiedDataSource.get();
        }
        return dataSource.isUnsatisfied() ? null : dataSource.get();
    }

    ResourceResolver resourceResolver() {
        ResourceResolverRegistry registry = new ResourceResolverRegistry();
        for (ResourceResolver resolver : resolvers) {
            if (resolver.protocol() != null) {
                registry.register(resolver.protocol(), resolver);
            }
        }
        return registry;
    }
}
