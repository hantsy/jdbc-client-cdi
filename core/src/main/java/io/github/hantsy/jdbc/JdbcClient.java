package io.github.hantsy.jdbc;

import io.github.hantsy.jdbc.converter.Converter;
import io.github.hantsy.jdbc.converter.ConverterRegistry;
import io.github.hantsy.jdbc.support.KeyHolder;

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

public class JdbcClient {

    private DataSource dataSource;
    private ConverterRegistry converterRegistry;
    private JdbcConfig config;

    private JdbcClient(DataSource dataSource, ConverterRegistry converterRegistry, JdbcConfig config) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        Objects.requireNonNull(converterRegistry, "converterRegistry must not be null");
        Objects.requireNonNull(config, "config must not be null");

        this.dataSource = dataSource;
        this.converterRegistry = converterRegistry;
        this.config = config;
    }

    /**
     * Creates a client with {@link JdbcConfig#DEFAULT default config} and an empty converter registry.
     */
    public JdbcClient(DataSource dataSource) {
        this(dataSource, new ConverterRegistry(), JdbcConfig.DEFAULT);
    }


    /**
     * No-arg constructor required for CDI client proxies; a client created this way is not usable until
     * its {@link DataSource} is supplied. Use {@link #JdbcClient(DataSource)} or
     * {@link #builder(DataSource)} for normal construction.
     */
    public JdbcClient() {
    }


    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setConverterRegistry(ConverterRegistry converterRegistry) {
        this.converterRegistry = converterRegistry;
    }

    public void setConfig(JdbcConfig config) {
        this.config = config;
    }

    public static Builder builder(DataSource dataSource) {
        return new Builder(dataSource);
    }

    public static class Builder {
        private final DataSource dataSource;
        private ConverterRegistry converters = new ConverterRegistry();
        private String placeholder = JdbcConfig.DEFAULT.placeholder();
        private int queryTimeout = JdbcConfig.DEFAULT.queryTimeout();
        private int fetchSize = JdbcConfig.DEFAULT.fetchSize();

        private Builder(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public Builder placeholder(String placeholder) {
            this.placeholder = Objects.requireNonNull(placeholder, "placeholder must not be null");
            return this;
        }

        public Builder queryTimeout(int queryTimeout) {
            this.queryTimeout = queryTimeout;
            return this;
        }

        public Builder fetchSize(int fetchSize) {
            this.fetchSize = fetchSize;
            return this;
        }

        public Builder converters(ConverterRegistry converters) {
            this.converters = Objects.requireNonNull(converters, "converters must not be null");
            return this;
        }

        public Builder config(JdbcConfig config) {
            Objects.requireNonNull(config, "config must not be null");
            this.placeholder = config.placeholder();
            this.queryTimeout = config.queryTimeout();
            this.fetchSize = config.fetchSize();
            return this;
        }

        public JdbcClient build() {
            return new JdbcClient(dataSource, converters,
                    new JdbcConfig(placeholder, queryTimeout, fetchSize));
        }
    }

    public SqlSpec sql(String sql) {
        return new SqlSpec(sql);
    }

    public class SqlSpec {
        private final String rawSql;
        private final Map<String, Object> namedParams = new HashMap<>();
        private final List<Object> positionalParams = new ArrayList<>();
        private Integer fetchSize;
        private Integer queryTimeout;

        public SqlSpec(String sql) {
            this.rawSql = sql;
        }

        public SqlSpec fetchSize(int fetchSize) {
            this.fetchSize = fetchSize;
            return this;
        }

        public SqlSpec queryTimeout(int queryTimeout) {
            this.queryTimeout = queryTimeout;
            return this;
        }

        public SqlSpec param(String name, Object value) {
            if (!positionalParams.isEmpty()) {
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
            return this;
        }

        public SqlSpec params(Map<String, ?> values) {
            if (!positionalParams.isEmpty()) {
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
            return this;
        }

        public SqlSpec params(List<?> values) {
            if (!namedParams.isEmpty()) {
                throw new IllegalArgumentException("Cannot mix named and positional parameters");
            }
            this.positionalParams.addAll(values);
            return this;
        }

        public <T> QuerySpec<T> query(Class<T> clazz) {
            return new QuerySpec<>(new TypedRowMapper<>(clazz));
        }

        public <T> QuerySpec<T> query(RowMapper<T> rowMapper) {
            return new QuerySpec<>(rowMapper);
        }

        public <T> T singleValue(Class<T> clazz) {
            return optionalValue(clazz)
                    .orElseThrow(() -> new IncorrectResultSizeException("Expected a single value but got none", 1, 0));
        }

        public <T> Optional<T> optionalValue(Class<T> clazz) {
            ParsedSql parsed = parseSql(rawSql);
            TypedRowMapper<T> mapper = new TypedRowMapper<>(clazz);
            return executeQuery(parsed, rs -> {
                if (!rs.next()) {
                    return Optional.<T>empty();
                }
                return Optional.ofNullable(mapper.mapRow(rs, 0));
            });
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
            return execute(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = returnKeys
                             ? conn.prepareStatement(parsed.jdbcSql, Statement.RETURN_GENERATED_KEYS)
                             : conn.prepareStatement(parsed.jdbcSql)) {
                    applyStatementHints(ps);
                    bindParameters(ps, parsed.orderedParamNames);
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
            return execute(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(parsed.jdbcSql)) {
                    applyStatementHints(ps);
                    for (Map<String, Object> args : batchArgs) {
                        for (int i = 0; i < parsed.orderedParamNames.size(); i++) {
                            ps.setObject(i + 1, args.get(parsed.orderedParamNames.get(i)));
                        }
                        ps.addBatch();
                    }
                    return ps.executeBatch();
                }
            });
        }

        public int[] batchUpdate(Object[][] batchArgs) {
            ParsedSql parsed = parseSql(rawSql);
            return execute(() -> {
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
                        throw new IncorrectResultSizeException("Expected exactly 1 row but got 0", 1, 0);
                    }
                    T result = rowMapper.mapRow(rs, 0);
                    if (rs.next()) {
                        throw new IncorrectResultSizeException("Expected exactly 1 row but got more than 1", 1, -1);
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
                        throw new IncorrectResultSizeException("Expected at most 1 row but got more than 1", 1, -1);
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
                    bindParameters(ps, parsed.orderedParamNames);
                    rs = ps.executeQuery();
                    opened = true;
                    final Connection c = conn;
                    final PreparedStatement s = ps;
                    final ResultSet r = rs;
                    return StreamSupport.stream(new ResultSetSpliterator<>(r, rowMapper), false)
                            .onClose(() -> closeQuietly(r, s, c));
                } catch (SQLException e) {
                    throw new DataAccessException(e.getMessage(), e);
                } finally {
                    if (!opened) {
                        closeQuietly(rs, ps, conn);
                    }
                }
            }
        }

        private <R> R execute(SQLExecutor<R> executor) {
            try {
                return executor.execute();
            } catch (SQLException e) {
                throw new DataAccessException(e.getMessage(), e);
            }
        }

        private <R> R executeQuery(ParsedSql parsed, SQLResultHandler<R> handler) {
            return execute(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(parsed.jdbcSql)) {
                    applyStatementHints(ps);
                    bindParameters(ps, parsed.orderedParamNames);
                    try (ResultSet rs = ps.executeQuery()) {
                        return handler.handle(rs);
                    }
                }
            });
        }

        private void applyStatementHints(PreparedStatement ps) throws SQLException {
            int fs = fetchSize != null ? fetchSize : config.fetchSize();
            int qt = queryTimeout != null ? queryTimeout : config.queryTimeout();
            if (fs > 0) {
                ps.setFetchSize(fs);
            }
            if (qt > 0) {
                ps.setQueryTimeout(qt);
            }
        }

        private void bindParameters(PreparedStatement ps, List<String> orderedNames) throws SQLException {
            if (!positionalParams.isEmpty()) {
                for (int i = 0; i < positionalParams.size(); i++) {
                    ps.setObject(i + 1, positionalParams.get(i));
                }
            } else {
                for (int i = 0; i < orderedNames.size(); i++) {
                    ps.setObject(i + 1, namedParams.get(orderedNames.get(i)));
                }
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
         * Rewrites {@code :name} placeholders into the configured symbol — positional {@code ?},
         * numbered {@code $1}/{@code :1}, or named {@code @name} — and returns the ordered parameter
         * names.
         */
        private ParsedSql parseSql(String sql) {
            if (!positionalParams.isEmpty()) {
                return new ParsedSql(sql, List.of());
            }

            Pattern pattern = Pattern.compile("(?<!:):([a-zA-Z0-9_]+)");
            Matcher matcher = pattern.matcher(sql);
            StringBuilder sb = new StringBuilder();
            List<String> parameterNames = new ArrayList<>();

            String targetSymbol = config.placeholder();
            int parameterIndex = 1;

            while (matcher.find()) {
                parameterNames.add(matcher.group(1));
                if (targetSymbol.contains("@")) {
                    // named: @name (SQL Server)
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(targetSymbol + matcher.group(1)));
                } else if (targetSymbol.contains("$") || targetSymbol.contains(":")) {
                    // numbered: $1, :1, ... (H2/PostgreSQL, Oracle)
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(targetSymbol + parameterIndex++));
                } else {
                    // positional: ? (MySQL and standard JDBC)
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(targetSymbol));
                }
            }
            matcher.appendTail(sb);
            return new ParsedSql(sb.toString(), parameterNames);
        }

    }

    /**
     * Maps a {@link ResultSet} row to a typed object — a record, a POJO, or a single scalar value —
     * using reflection and the registered {@link Converter}s.
     */
    private class TypedRowMapper<T> implements RowMapper<T> {

        private static final Set<Class<?>> SIMPLE_TYPES = Set.of(
                String.class, Boolean.class, Character.class,
                java.sql.Date.class, java.sql.Time.class, java.sql.Timestamp.class,
                byte[].class
        );

        private final Class<T> targetClass;

        TypedRowMapper(Class<T> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        public T mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                if (isSimpleType(targetClass)) {
                    return targetClass.cast(applyConversions(rs.getObject(1), targetClass));
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
                        args[i] = applyConversions(val, paramTypes[i]);
                    }
                    return targetClass.cast(c.newInstance(args));
                } else {
                    T instance = targetClass.getDeclaredConstructor().newInstance();
                    for (Field f : targetClass.getDeclaredFields()) {
                        String name = f.getName().toLowerCase();
                        if (rowValues.containsKey(name)) {
                            f.setAccessible(true);
                            f.set(instance, applyConversions(rowValues.get(name), f.getType()));
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

        private Object applyConversions(Object value, Class<?> targetType) {
            if (value == null) {
                return null;
            }
            Class<?> dataType = value.getClass();
            if (targetType.isAssignableFrom(dataType)) {
                return value;
            }

            Converter converter = converterRegistry.getConverter(dataType, targetType);
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

    private record ParsedSql(String jdbcSql, List<String> orderedParamNames) {
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
                throw new DataAccessException(e.getMessage(), e);
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
