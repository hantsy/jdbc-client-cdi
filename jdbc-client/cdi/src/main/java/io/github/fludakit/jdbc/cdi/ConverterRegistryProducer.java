package io.github.fludakit.jdbc.cdi;

import io.github.fludakit.jdbc.converter.Converter;
import io.github.fludakit.jdbc.converter.ConverterRegistry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

/**
 * Produces the {@code @ApplicationScoped} {@link ConverterRegistry}, populated with every CDI
 * {@link Converter} bean (source/target types detected from their generic {@code <S, T>} interface).
 */
@ApplicationScoped
public class ConverterRegistryProducer {

    @Produces
    @ApplicationScoped
    public ConverterRegistry produce(@Any Instance<Converter<?, ?>> converters) {
        ConverterRegistry registry = new ConverterRegistry();
        for (Converter<?, ?> converter : converters) {
            registry.register(converter);
        }
        return registry;
    }
}
