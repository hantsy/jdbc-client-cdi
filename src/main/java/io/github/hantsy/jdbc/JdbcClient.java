package io.github.hantsy.jdbc;

import io.github.hantsy.jdbc.converter.Converter;
import io.github.hantsy.jdbc.converter.ConverterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class JdbcClient {

    private final DataSource dataSource;
    private final ConverterRegistry converterRegistry;
    private final JdbcConfig jdbcConfig; // Injected Configurations

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

        private static final System.Logger LOGGER = System.getLogger(JdbcClient.class.getName());

        public SqlSpec(String sql) {
            this.rawSql = sql;
        }

        public SqlSpec enableMetrics(boolean enable) {
            this.clientMetricOverride = enable;
            return this;
        }

        public SqlSpec param(String name, Object value) {
            this.namedParams.put(name, value);
            return this;
        }

        public SqlSpec param(Object value) {
            this.positionalParams.add(value);
            this.usePositional = true;
            return this;
        }

        public <T> QuerySpec<T> query(Class<T> clazz) {
            return new QuerySpec<>((rs, rowNum) -> mapToType(rs, clazz));
        }

        private boolean isMetricsEnabled() {
            return clientMetricOverride != null ? clientMetricOverride : jdbcConfig.isMetricsEnabled();
        }

        private <R> R executeWithMetrics(String actionType, String sql, int batchSize, SQLExecutor<R> executor) {
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
                LOGGER.log(System.Logger.Level.INFO, "SQL Metrics | {0} | {1}ms | {2}", actionType, ms, sql.replaceAll("\\s+", " "));
            }
        }

        public int update() {
            ParsedSql parsed = parseSql(rawSql);
            return executeWithMetrics("UPDATE", parsed.jdbcSql, 0, () -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(parsed.jdbcSql)) {
                    bindParameters(ps, parsed.orderedParamNames);
                    return ps.executeUpdate();
                }
            });
        }

        public int[] batchUpdate(List<Map<String, Object>> batchArgs) {
            ParsedSql parsed = parseSql(rawSql);
            return executeWithMetrics("BATCH", parsed.jdbcSql, batchArgs.size(), () -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(parsed.jdbcSql)) {
                    for (Map<String, Object> args : batchArgs) {
                        this.namedParams.clear();
                        this.namedParams.putAll(args);
                        bindParameters(ps, parsed.orderedParamNames);
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
                return executeWithMetrics("QUERY", parsed.jdbcSql, 0, () -> {
                    List<T> results = new ArrayList<>();
                    try (Connection conn = dataSource.getConnection();
                         PreparedStatement ps = conn.prepareStatement(parsed.jdbcSql)) {
                        bindParameters(ps, parsed.orderedParamNames);
                        try (ResultSet rs = ps.executeQuery()) {
                            int rowNum = 0;
                            while (rs.next()) {
                                results.add(rowMapper.mapRow(rs, rowNum++));
                            }
                        }
                    }
                    return results;
                });
            }
        }

        private void bindParameters(PreparedStatement ps, List<String> orderedNames) throws SQLException {
            if (usePositional) {
                for (int i = 0; i < positionalParams.size(); i++) {
                    ps.setObject(i + 1, positionalParams.get(i));
                }
            } else {
                for (int i = 0; i < orderedNames.size(); i++) {
                    ps.setObject(i + 1, namedParams.get(orderedNames.get(i)));
                }
            }
        }

        /**
         * Refactored: Dynamically injects placeholder formatting symbols out of JdbcConfig settings
         */
        private ParsedSql parseSql(String sql) {
            if (usePositional) return new ParsedSql(sql, List.of());

            Pattern pattern = Pattern.compile("(?<!:):([a-zA-Z0-9_]+)");
            Matcher matcher = pattern.matcher(sql);
            StringBuilder sb = new StringBuilder();
            List<String> order = new ArrayList<>();

            String targetSymbol = jdbcConfig.getPositionalPlaceholderSymbol();
            int parameterIndex = 1;

            while (matcher.find()) {
                order.add(matcher.group(1));

                // If the target database uses numbered symbols like '$1', '$2', append the variable index
                if (targetSymbol.contains("$")) {
                    matcher.appendReplacement(sb, targetSymbol + parameterIndex++);
                } else {
                    matcher.appendReplacement(sb, targetSymbol);
                }
            }
            matcher.appendTail(sb);
            return new ParsedSql(sb.toString(), order);
        }

        @SuppressWarnings("unchecked")
        private <T> T mapToType(ResultSet rs, Class<T> targetClass) throws SQLException {
            try {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                Map<String, Object> rowValues = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    rowValues.put(metaData.getColumnLabel(i).toLowerCase().replace("_", ""), rs.getObject(i));
                }

                if (targetClass.isRecord()) {
                    java.lang.reflect.Constructor<?> c = targetClass.getDeclaredConstructors()[0];
                    Class<?>[] paramTypes = c.getParameterTypes();
                    java.lang.reflect.RecordComponent[] components = targetClass.getRecordComponents();
                    Object[] args = new Object[components.length];
                    for (int i = 0; i < components.length; i++) {
                        Object val = rowValues.get(components[i].getName().toLowerCase());
                        args[i] = runConversionPipeline(val, paramTypes[i]);
                    }
                    return (T) c.newInstance(args);
                } else {
                    T instance = targetClass.getDeclaredConstructor().newInstance();
                    for (java.lang.reflect.Field f : targetClass.getDeclaredFields()) {
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

        private Object runConversionPipeline(Object value, Class<?> targetType) {
            if (value == null) return null;
            if (targetType.isAssignableFrom(value.getClass())) return value;
            Converter c = converterRegistry.getConverter(value.getClass(), targetType);
            if (c != null) return c.convert(value);
            if (value instanceof Number num) {
                if (targetType == Long.class || targetType == long.class) return num.longValue();
                if (targetType == Integer.class || targetType == int.class) return num.intValue();
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
}
