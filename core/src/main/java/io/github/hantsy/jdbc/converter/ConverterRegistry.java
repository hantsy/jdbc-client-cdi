package io.github.hantsy.jdbc.converter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A plain (non-CDI) registry of {@link Converter} implementations. Register converters manually, or
 * (from the CDI layer) register discovered converter beans and let their generic type arguments be
 * detected automatically.
 */
public class ConverterRegistry {

    private final Map<ConversionKey, Converter<?, ?>> matrix = new HashMap<>();

    public record ConversionKey(Class<?> sourceType, Class<?> targetType) {
    }

    public <S, T> ConverterRegistry register(Class<S> sourceType, Class<T> targetType, Converter<S, T> converter) {
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(converter, "converter must not be null");
        matrix.put(new ConversionKey(sourceType, targetType), converter);
        return this;
    }

    /** Registers a converter, resolving its source/target types from its {@code Converter<S, T>} generic interface. */
    public ConverterRegistry register(Converter<?, ?> converter) {
        Objects.requireNonNull(converter, "converter must not be null");
        Type[] args = Converters.typeArguments(converter.getClass());
        if (args != null && args.length == 2) {
            Class<?> sourceType = Converters.getClassFromType(args[0]);
            Class<?> targetType = Converters.getClassFromType(args[1]);
            if (sourceType != null && targetType != null) {
                matrix.put(new ConversionKey(sourceType, targetType), converter);
            }
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <S, T> Converter<S, T> getConverter(Class<S> source, Class<T> target) {
        return (Converter<S, T>) matrix.get(new ConversionKey(source, target));
    }
}
