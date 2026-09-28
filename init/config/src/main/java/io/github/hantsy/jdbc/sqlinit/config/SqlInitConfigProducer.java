package io.github.hantsy.jdbc.sqlinit.config;

import io.github.hantsy.jdbc.sqlinit.SqlInitConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Produces the {@code @ApplicationScoped} {@link SqlInitConfig} bean from the
 * {@code jdbcclient.init.*} MicroProfile Config properties. This is the optional integration point
 * consumed by the {@code cdi} module.
 */
@ApplicationScoped
public class SqlInitConfigProducer {

    @Inject
    @ConfigProperty(name = "jdbcclient.init.separator", defaultValue = ";")
    private String separator;

    @Inject
    @ConfigProperty(name = "jdbcclient.init.schema-locations", defaultValue = "/schema.sql")
    private List<String> schemaLocations;

    @Inject
    @ConfigProperty(name = "jdbcclient.init.data-locations", defaultValue = "/data.sql")
    private List<String> dataLocations;

    @Produces
    @ApplicationScoped
    public SqlInitConfig produce() {
        return new SqlInitConfig(separator, schemaLocations, dataLocations);
    }
}
