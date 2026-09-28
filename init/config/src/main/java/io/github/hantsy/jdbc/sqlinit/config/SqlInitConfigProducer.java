package io.github.hantsy.jdbc.sqlinit.config;

import io.github.hantsy.jdbc.sqlinit.SqlInitConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Produces the {@code @ApplicationScoped} {@link SqlInitConfig} bean from the
 * {@code jdbcclient.init.*} MicroProfile Config properties.
 */
@ApplicationScoped
public class SqlInitConfigProducer {

    @Inject
    @ConfigProperty(name = "jdbcclient.init.script-locations", defaultValue = "classpath:db/migration")
    private List<String> scriptLocations;

    @Inject
    @ConfigProperty(name = "jdbcclient.init.separator", defaultValue = ";")
    private String separator;

    @Inject
    @ConfigProperty(name = "jdbcclient.init.db-type", defaultValue = "")
    private String dbType;

    @Produces
    @ApplicationScoped
    public SqlInitConfig produce() {
        SqlInitConfig.Builder builder = SqlInitConfig.builder()
                .scriptLocations(scriptLocations)
                .separator(separator);
        if (dbType != null && !dbType.isBlank()) {
            builder.dbType(dbType);
        }
        return builder.build();
    }
}
