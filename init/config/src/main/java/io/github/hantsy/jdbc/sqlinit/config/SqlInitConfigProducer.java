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
    @ConfigProperty(name = "jdbcclient.init.platform", defaultValue = "")
    private String platform;

    @Inject
    @ConfigProperty(name = "jdbcclient.init.history-table", defaultValue = "sqlinit_migration")
    private String historyTable;

    @Produces
    @ApplicationScoped
    public SqlInitConfig produce() {
        SqlInitConfig.Builder builder = SqlInitConfig.builder()
                .scriptLocations(scriptLocations)
                .separator(separator)
                .historyTable(historyTable);
        if (platform != null && !platform.isBlank()) {
            builder.platform(platform);
        }
        return builder.build();
    }
}
