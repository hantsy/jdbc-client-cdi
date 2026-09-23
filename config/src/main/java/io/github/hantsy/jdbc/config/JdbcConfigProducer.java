package io.github.hantsy.jdbc.config;

import io.github.hantsy.jdbc.JdbcConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Produces the {@code @ApplicationScoped} {@link JdbcConfig} bean from the {@code jdbcclient.*}
 * MicroProfile Config properties. This is the optional integration point consumed by the {@code cdi}
 * module.
 */
@ApplicationScoped
public class JdbcConfigProducer {

    @Inject
    @ConfigProperty(name = "jdbcclient.placeholder", defaultValue = "?")
    private String placeholder;

    @Inject
    @ConfigProperty(name = "jdbcclient.query-timeout", defaultValue = "0")
    private int queryTimeout;

    @Inject
    @ConfigProperty(name = "jdbcclient.fetch-size", defaultValue = "0")
    private int fetchSize;

    @Produces
    @ApplicationScoped
    public JdbcConfig produce() {
        return new JdbcConfig(placeholder, queryTimeout, fetchSize);
    }
}
