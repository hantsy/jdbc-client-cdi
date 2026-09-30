package io.github.fludakit.jdbc.cdi;

import io.github.fludakit.jdbc.JdbcClient;
import io.github.fludakit.jdbc.JdbcConfig;
import io.github.fludakit.jdbc.converter.ConverterRegistry;

import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

/**
 * Produces the {@code @ApplicationScoped} {@link JdbcClient} bean. The {@link JdbcConfig} bean is
 * optional: when the {@code config} module is absent, {@link JdbcConfig#DEFAULT} is used.
 */
@ApplicationScoped
public class JdbcClientProducer {

    @Produces
    @ApplicationScoped
    public JdbcClient produce(DataSource dataSource, ConverterRegistry converters, Instance<JdbcConfig> configs) {
        JdbcConfig config = configs.isResolvable() ? configs.get() : JdbcConfig.DEFAULT;
        return JdbcClient.builder(dataSource)
                .config(config)
                .converters(converters)
                .build();
    }
}
