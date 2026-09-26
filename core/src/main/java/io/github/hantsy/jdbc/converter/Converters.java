package io.github.hantsy.jdbc.converter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Reflection helpers that resolve a {@link Converter} implementation's generic
 * {@code <S, T>} type arguments. Used by {@link ConverterRegistry} and by the
 * CDI layer when discovering converter beans.
 */
public final class Converters {

    private Converters() {
    }

    /**
     * Walks the class hierarchy to find the {@code Converter<S, T>} generic
     * interface and return its type arguments.
     */
    public static Type[] typeArguments(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Type iface : current.getGenericInterfaces()) {
                if (iface instanceof ParameterizedType pt && pt.getRawType() == Converter.class) {
                    return pt.getActualTypeArguments();
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static Class<?> getClassFromType(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        return null;
    }
}
