package io.github.hantsy.jdbc.init;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SqlScriptPopulator {

    private static final Logger LOGGER = Logger.getLogger(SqlScriptPopulator.class.getName());

    public static void executeScripts(DataSource dataSource, List<String> scriptPaths) {
        if (scriptPaths == null || scriptPaths.isEmpty()) {
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {

                for (String path : scriptPaths) {
                    LOGGER.info("Executing SQL script: " + path);
                    List<String> sqlStatements = parseScript(path);

                    for (String sql : sqlStatements) {
                        statement.addBatch(sql);
                    }
                    statement.executeBatch();
                }

                connection.commit();
                LOGGER.info("Database initialization completed successfully.");
            } catch (Exception e) {
                connection.rollback();
                throw new RuntimeException("Failed to execute SQL initialization scripts. Rolling back.", e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error obtaining connection from DataSource", e);
        }
    }

    private static List<String> parseScript(String path) {
        List<String> statements = new ArrayList<>();
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new IllegalArgumentException("Script not found on classpath: " + path);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // Skip single-line comments or empty lines
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("//")) {
                    continue;
                }
                sb.append(line).append("\n");

                // Naive split by semicolon (can be enhanced with regex or state machine for strings)
                if (trimmed.endsWith(";")) {
                    statements.add(sb.toString().replace(";", "").trim());
                    sb.setLength(0);
                }
            }
            if (sb.length() > 0 && !sb.toString().trim().isEmpty()) {
                statements.add(sb.toString().trim());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading script file: " + path, e);
        }
        return statements;
    }
}
