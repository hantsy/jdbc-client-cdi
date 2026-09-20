package io.github.hantsy.jdbc;

import io.github.hantsy.jdbc.converter.Converter;
import io.github.hantsy.jdbc.converter.ConverterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class JdbcClient {

    private static final System.Logger LOGGER = System.getLogger(JdbcClient.class.getName());

    private static final Set<Class<?>> SIMPLE_TYPES = Set.of(
            String.class, Boolean.class, Character.class,
            java.sql.Date.class, java.sql.Time.class, java.sql.Timestamp.class,
            byte[].class
    );

    private final DataSource dataSource;
    private final ConverterRegistry converterRegistry;
    private final JdbcConfig jdbcConfig;

    @Inject
    public JdbcClient(DataSource dataSource, ConverterRegistry converterRegistry, JdbcConfig jdbcConfig) {
        this.dataSource = dataSource;
        this.converterRegistry = converterRegistry;
        this.jdbcConfig = jdbcConfig;
    }

    public SqlSpec sql(String sql) {
        return new SqlSpec(sql);
    }

    public class SqlSpec {
        private final String rawSql;
        private final Map<String, Object> namedParams = new HashMap<>();
        private final List<Object> positionalParams = new ArrayList<>();
        private boolean usePositional = false;
        private Boolean clientMetricOverride = null;
        private Integer fetchSize;
        private Integer maxRows;

        public SqlSpec(String sql) {
            this.rawSql = sql;
        }

        public SqlSpec enableMetrics(boolean enable) {
            this.clientMetricOverride = enable;
            return this;
        }

        public SqlSpec fetchSize(int fetchSize) {
            this.fetchSize = fetchSize;
            return this;
        }

        public SqlSpec maxRows(int maxRows) {
            this.maxRows = maxRows;
            return this;
        }

        public SqlSpec param(String name, Object value) {
            if (usePositional) {
                throw new IllegalArgumentException("Cannot mix named and positional parameters");
            }
            this.namedParams.put(name, value);
            return this;
        }

        public SqlSpec param(Object value) {
            if (!namedParams.isEmpty()) {
                throw new IllegalArgumentException("Cannot mix named and positional parameters");
            }
            this.positionalParams.add(value);
            this.usePositional = true;
            return this;
        }

        public SqlSpec params(Map<String, ?> values) {
            if (usePositional) {
                throw new IllegalArgumentException("Cannot mix named and positional parameters");
            }
            this.namedParams.putAll(values);
            return this;
        }

        public SqlSpec params(Object... values) {
            if (!namedParams.isEmpty()) {
                throw new IllegalArgumentException("Cannot mix named and positional parameters");
            }
            this.positionalParams.addAll(Arrays.asList(values));
            if (values.length > 0) {
                this.usePositional = true;
            }
            return this;
        }

        public SqlSpec params(List<?> values) {
            if (!namedParams.isEmpty()) {
                throw new IllegalArgumentException("Cannot mix named and positional parameters");
            }
            this.positionalParams.addAll(values);
            if (!values.isEmpty()) {
                this.usePositional = true;
            }
            return this;
        }

        public <T> QuerySpec<T> query(Class<T> clazz) {
            return new QuerySpec<>((rs, rowNum) -> mapToType(rs, clazz));
        }

        public <T> QuerySpec<T> query(RowMapper<T> rowMapper) {
            return new QuerySpec<>(rowMapper);
        }

        public <T> Optional<T> singleValue(Class<T> clazz) {
            ParsedSql parsed = parseSql(rawSql);
            return executeQuery(parsed, rs -> {
                if (!rs.next()) {
                    return Optional.<T>empty();
                }
                return Optional.ofNullable(clazz.cast(runConversionPipeline(rs.getObject(1), clazz)));
            });
        }

        public <T> Optional<T> optionalValue(Class<T> clazz) {
            return singleValue(clazz);
        }

        public int update() {
            return doUpdate(null);
        }

        public int update(KeyHolder keyHolder) {
            Objects.requireNonNull(keyHolder, "keyHolder must not be null");
            return doUpdate(keyHolder);
        }

        private int doUpdate(KeyHolder keyHolder) {
            ParsedSql parsed = parseSql(rawSql);
            boolean returnKeys = keyHolder != null;
            return executeWithMetrics("UPDATE", parsed.jdbcSql, () -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = returnKeys
                             ? conn.prepareStatement(parsed.jdbcSql, Statement.RETURN_GENERATED_KEYS)
                             : conn.prepareStatement(parsed.jdbcSql)) {
                    applyStatementHints(ps);
                    bindParameters(ps, parsed.orderedParamNames, namedParams, positionalParams);
                    int rows = ps.executeUpdate();
                    if (returnKeys) {
                        collectGeneratedKeys(ps, keyHolder);
                    }
                    return rows;
                }
            });
        }

        public int[] batchUpdate(List<Map<String, Object>> batchArgs) {
            ParsedSql parsed = parseSql(rawSql);
            return executeWithMetrics("BATCH", parsed.jdbcSql, () -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(parsed.jdbcSql)) {
                    applyStatementHints(ps);
                    for (Map<String, Object> args : batchArgs) {
                        bindNamed(ps, parsed.orderedParamNames, args);
                        ps.addBatch();
                    }
                    return ps.executeBatch();
                }
            });
        }

        public int[] batchUpdate(Object[][] batchArgs) {
            ParsedSql parsed = parseSql(rawSql);
            return executeWithMetrics("BATCH", parsed.jdbcSql, () -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(parsed.jdbcSql)) {
                    applyStatementHints(ps);
                    for (Object[] args : batchArgs) {
                        for (int i = 0; i < args.length; i++) {
                            ps.setObject(i + 1, args[i]);
                        }
                        ps.addBatch();
                    }
                    return ps.executeBatch();
                }
            });
        }

        public class QuerySpec<T> {
            private final RowMapper<T> rowMapper;

            public QuerySpec(RowMapper<T> rowMapper) {
                this.rowMapper = rowMapper;
            }

            public List<T> list() {
                ParsedSql parsed = parseSql(rawSql);
                return executeQuery(parsed, rs -> {
                    List<T> results = new ArrayList<>();
                    int rowNum = 0;
                    while (rs.next()) {
                        results.add(rowMapper.mapRow(rs, rowNum++));
                    }
                    return results;
                });
            }

            public T single() {
                ParsedSql parsed = parseSql(rawSql);
                return executeQuery(parsed, rs -> {
                    if (!rs.next()) {
                        throw new IncorrectResultSizeException("Expected exactly 1 row but got 0");
                    }
                    T result = rowMapper.mapRow(rs, 0);
                    if (rs.next()) {
                        throw new IncorrectResultSizeException("Expected exactly 1 row but got more than 1");
                    }
                    return result;
                });
            }

            public Optional<T> optional() {
                ParsedSql parsed = parseSql(rawSql);
                return executeQuery(parsed, rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    T result = rowMapper.mapRow(rs, 0);
                    if (rs.next()) {
                        throw new IncorrectResultSizeException("Expected at most 1 row but got more than 1");
                    }
                    return Optional.of(result);
                });
            }

            public Stream<T> stream() {
                ParsedSql parsed = parseSql(rawSql);
                Connection conn = null;
                PreparedStatement ps = null;
                ResultSet rs = null;
                boolean opened = false;
                try {
                    conn = dataSource.getConnection();
                    ps = conn.prepareStatement(parsed.jdbcSql);
                    applyStatementHints(ps);
                    bindParameters(ps, parsed.orderedParamNames, namedParams, positionalParams);
                    rs = ps.executeQuery();
                    opened = true;
                    final Connection c = conn;
                    final PreparedStatement s = ps;
                    final ResultSet r = rs;
                    return StreamSupport.stream(new ResultSetSpliterator<>(r, rowMapper), false)
                            .onClose(() -> closeQuietly(r, s, c));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                } finally {
                    if (!opened) {
                        closeQuietly(rs, ps, conn);
                    }
                }
            }
        }

        private boolean isMetricsEnabled() {
            return clientMetricOverride != null ? clientMetricOverride : jdbcConfig.isMetricsEnabled();
        }

        private <R> R executeWithMetrics(String actionType, String sql, SQLExecutor<R> executor) {
            if (!isMetricsEnabled()) {
                try {
                    return executor.execute();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            long startTime = System.nanoTime();
            try {
                return executor.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                long ms = (System.nanoTime() - startTime) / 1_000_000;
                LOGGER.log(System.Logger.Level.INFO, "SQL Metrics | {0} | {1}ms | {2}",
                        actionType, ms, sql.replaceAll("\\s+", " "));
            }
        }

        private <R> R executeQuery(ParsedSql parsed, SQLResultHandler<R> handler) {
            return executeWithMetrics("QUERY", parsed.jdbcSql, () -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(parsed.jdbcSql)) {
                    applyStatementHints(ps);
                    bindParameters(ps, parsed.orderedParamNames, namedParams, positionalParams);
                    try (ResultSet rs = ps.executeQuery()) {
                        return handler.handle(rs);
                    }
                }
            });
        }

        private void applyStatementHints(PreparedStatement ps) throws SQLException {
            if (fetchSize != null) {
                ps.setFetchSize(fetchSize);
            }
            if (maxRows != null) {
                ps.setMaxRows(maxRows);
            }
        }

        private void bindParameters(PreparedStatement ps, List<String> orderedNames,
                                    Map<String, Object> named, List<Object> positional) throws SQLException {
            if (usePositional) {
                for (int i = 0; i < positional.size(); i++) {
                    ps.setObject(i + 1, positional.get(i));
                }
            } else {
                for (int i = 0; i < orderedNames.size(); i++) {
                    ps.setObject(i + 1, named.get(orderedNames.get(i)));
                }
            }
        }

        private void bindNamed(PreparedStatement ps, List<String> orderedNames, Map<String, Object> args) throws SQLException {
            for (int i = 0; i < orderedNames.size(); i++) {
                ps.setObject(i + 1, args.get(orderedNames.get(i)));
            }
        }

        private void collectGeneratedKeys(PreparedStatement ps, KeyHolder keyHolder) throws SQLException {
            try (ResultSet gk = ps.getGeneratedKeys()) {
                ResultSetMetaData meta = gk.getMetaData();
                int cols = meta.getColumnCount();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (gk.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnLabel(i), gk.getObject(i));
                    }
                    rows.add(row);
                }
                keyHolder.getKeyList().addAll(rows);
            }
        }

        /**
         * Rewrites {@code :name} placeholders into the configured JDBC symbol (e.g. {@code ?} or
         * numbered {@code $1}, {@code $2}, ...), returning the ordered parameter names.
         */
        private ParsedSql parseSql(String sql) {
            if (usePositional) {
                return new ParsedSql(sql, List.of());
            }

            Pattern pattern = Pattern.compile("(?<!:):([a-zA-Z0-9_]+)");
            Matcher matcher = pattern.matcher(sql);
            StringBuilder sb = new StringBuilder();
            List<String> order = new ArrayList<>();

            String targetSymbol = jdbcConfig.getPositionalPlaceholderSymbol();
            int parameterIndex = 1;

            while (matcher.find()) {
                order.add(matcher.group(1));
                if (targetSymbol.contains("$")) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(targetSymbol + parameterIndex++));
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(targetSymbol));
                }
            }
            matcher.appendTail(sb);
            return new ParsedSql(sb.toString(), order);
        }

        @SuppressWarnings("unchecked")
        private <T> T mapToType(ResultSet rs, Class<T> targetClass) throws SQLException {
            try {
                if (isSimpleType(targetClass)) {
                    return (T) runConversionPipeline(rs.getObject(1), targetClass);
                }

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                Map<String, Object> rowValues = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    rowValues.put(metaData.getColumnLabel(i).toLowerCase().replace("_", ""), rs.getObject(i));
                }

                if (targetClass.isRecord()) {
                    RecordComponent[] components = targetClass.getRecordComponents();
                    Class<?>[] paramTypes = new Class<?>[components.length];
                    for (int i = 0; i < components.length; i++) {
                        paramTypes[i] = components[i].getType();
                    }
                    Constructor<?> c = targetClass.getDeclaredConstructor(paramTypes);
                    Object[] args = new Object[components.length];
                    for (int i = 0; i < components.length; i++) {
                        Object val = rowValues.get(components[i].getName().toLowerCase());
                        args[i] = runConversionPipeline(val, paramTypes[i]);
                    }
                    return (T) c.newInstance(args);
                } else {
                    T instance = targetClass.getDeclaredConstructor().newInstance();
                    for (Field f : targetClass.getDeclaredFields()) {
                        String name = f.getName().toLowerCase();
                        if (rowValues.containsKey(name)) {
                            f.setAccessible(true);
                            f.set(instance, runConversionPipeline(rowValues.get(name), f.getType()));
                        }
                    }
                    return instance;
                }
            } catch (Exception e) {
                throw new SQLException(e);
            }
        }

        private boolean isSimpleType(Class<?> type) {
            return type.isPrimitive()
                    || type.isEnum()
                    || type.getPackageName().startsWith("java.time")
                    || SIMPLE_TYPES.contains(type)
                    || Number.class.isAssignableFrom(type);
        }

        private Object runConversionPipeline(Object value, Class<?> targetType) {
            if (value == null) {
                return null;
            }
            Class<?> sourceType = value.getClass();
            if (targetType.isAssignableFrom(sourceType)) {
                return value;
            }

            Converter converter = converterRegistry.getConverter(sourceType, targetType);
            if (converter != null) {
                return converter.convert(value);
            }

            if (value instanceof Number num) {
                if (targetType == Long.class || targetType == long.class) return num.longValue();
                if (targetType == Integer.class || targetType == int.class) return num.intValue();
                if (targetType == Short.class || targetType == short.class) return num.shortValue();
                if (targetType == Byte.class || targetType == byte.class) return num.byteValue();
                if (targetType == Double.class || targetType == double.class) return num.doubleValue();
                if (targetType == Float.class || targetType == float.class) return num.floatValue();
                if (targetType == java.math.BigDecimal.class) return new java.math.BigDecimal(num.toString());
                if (targetType == java.math.BigInteger.class) return java.math.BigInteger.valueOf(num.longValue());
                if (targetType == String.class) return num.toString();
                if (targetType == Boolean.class || targetType == boolean.class) return num.intValue() != 0;
            }

            if (targetType == String.class) {
                return value.toString();
            }

            if (targetType == Boolean.class || targetType == boolean.class) {
                if (value instanceof String s) return Boolean.parseBoolean(s);
                if (value instanceof Boolean b) return b;
            }

            if (value instanceof java.sql.Timestamp ts) {
                if (targetType == java.time.LocalDateTime.class) return ts.toLocalDateTime();
                if (targetType == java.time.LocalDate.class) return ts.toLocalDateTime().toLocalDate();
                if (targetType == java.time.Instant.class) return ts.toInstant();
                if (targetType == java.time.OffsetDateTime.class)
                    return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
            }
            if (value instanceof java.sql.Date d && targetType == java.time.LocalDate.class) {
                return d.toLocalDate();
            }
            if (value instanceof java.sql.Time t && targetType == java.time.LocalTime.class) {
                return t.toLocalTime();
            }

            return value;
        }
    }

    private static class ParsedSql {
        final String jdbcSql;
        final List<String> orderedParamNames;

        ParsedSql(String jdbcSql, List<String> orderedParamNames) {
            this.jdbcSql = jdbcSql;
            this.orderedParamNames = orderedParamNames;
        }
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T mapRow(ResultSet rs, int rowNum) throws SQLException;
    }

    @FunctionalInterface
    private interface SQLExecutor<R> {
        R execute() throws SQLException;
    }

    @FunctionalInterface
    private interface SQLResultHandler<R> {
        R handle(ResultSet rs) throws SQLException;
    }

    private static class ResultSetSpliterator<T> implements Spliterator<T> {
        private final ResultSet rs;
        private final RowMapper<T> mapper;
        private int rowNum = 0;

        ResultSetSpliterator(ResultSet rs, RowMapper<T> mapper) {
            this.rs = rs;
            this.mapper = mapper;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            try {
                if (rs.next()) {
                    action.accept(mapper.mapRow(rs, rowNum++));
                    return true;
                }
                return false;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Spliterator<T> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL | IMMUTABLE;
        }
    }

    private static void closeQuietly(AutoCloseable... closeables) {
        for (AutoCloseable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    // ignore close failures
                }
            }
        }
    }
}
