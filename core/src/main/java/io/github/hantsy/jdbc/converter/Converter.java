package io.github.hantsy.jdbc.converter;

/**
 * Converts a value of source type {@code S} into target type {@code T}.
 *
 * <p>Converters translate a JDBC/database value (e.g. {@link
 * java.sql.Timestamp}, {@link java.math.BigDecimal}) into a Java type (e.g.
 * {@link java.time.LocalDateTime}, {@link Long}) when mapping result-set
 * columns to records, POJOs, and scalar values.
 *
 * @param <S> the source type
 * @param <T> the target type
 */
@FunctionalInterface
public interface Converter<S, T> {
    T convert(S source);
}
