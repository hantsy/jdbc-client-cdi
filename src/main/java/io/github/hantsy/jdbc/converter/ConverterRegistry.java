package io.github.hantsy.jdbc.converter;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ConverterRegistry {

    @Inject
    @Any
    private Instance<Converter<?, ?>> discoveredConverters;

    private final Map<ConversionKey, Converter<?, ?>> matrix = new HashMap<>();

    public record ConversionKey(Class<?> sourceType, Class<?> targetType) {
    }

    @PostConstruct
    public void init() {
        for (Converter<?, ?> converter : discoveredConverters) {
            Class<?> currentClass = converter.getClass();
            ParameterizedType pt = findConverter(currentClass);
            if (pt != null) {
                Type[] args = pt.getActualTypeArguments();
                Class<?> src = getClassFromType(args[0]);
                Class<?> tgt = getClassFromType(args[1]);
                if (src != null && tgt != null) {
                    matrix.put(new ConversionKey(src, tgt), converter);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <S, T> Converter<S, T> getConverter(Class<S> source, Class<T> target) {
        return (Converter<S, T>) matrix.get(new ConversionKey(source, target));
    }

    private ParameterizedType findConverter(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            for (Type iface : clazz.getGenericInterfaces()) {
                if (iface instanceof ParameterizedType pt && pt.getRawType() == Converter.class) {
                    return pt;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private Class<?> getClassFromType(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType pt) return (Class<?>) pt.getRawType();
        return null;
    }
}
