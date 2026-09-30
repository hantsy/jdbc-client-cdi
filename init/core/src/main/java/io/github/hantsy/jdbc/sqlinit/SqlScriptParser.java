package io.github.hantsy.jdbc.sqlinit;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a SQL script into the individual statements it contains.
 *
 * <p>The scanner tracks single-quoted strings, double-quoted and back-tick quoted identifiers,
 * {@code --} and {@code #} line comments and nestable {@code /* *&#47;} block comments, so a
 * separator that appears inside any of them does not terminate a statement.</p>
 *
 * <p>A {@code DELIMITER <value>} line encountered where no statement has been accumulated yet
 * replaces the active separator for the remainder of the script, which is how MySQL and MariaDB
 * scripts wrap a stored-procedure body that contains the default separator.</p>
 */
public final class SqlScriptParser {

    private static final String DELIMITER = "DELIMITER";

    private SqlScriptParser() {
    }

    /**
     * Reads a script and returns the statements it declares, in script order.
     *
     * @param source    the script content, not closed by this method
     * @param separator the initial statement separator
     * @return the statements, without their trailing separator and without comments
     * @throws SQLException if the script cannot be read, a quote is left unbalanced, a block
     *                      comment is left unterminated, or a {@code DELIMITER} line has no value
     */
    public static List<String> parse(Reader source, String separator) throws SQLException {
        requireSeparator(separator);
        StringWriter content = new StringWriter();
        try {
            source.transferTo(content);
        } catch (IOException e) {
            throw new SQLException("Failed to read the SQL script", e);
        }
        return parse(content.toString(), separator);
    }

    /**
     * Splits an already-read script into the statements it declares, in script order.
     *
     * @param script    the script content
     * @param separator the initial statement separator
     * @return the statements, without their trailing separator and without comments
     * @throws SQLException if a quote is left unbalanced, a block comment is left unterminated, or
     *                      a {@code DELIMITER} line has no value
     */
    static List<String> parse(String script, String separator) throws SQLException {
        requireSeparator(separator);

        List<String> statements = new ArrayList<>();
        StringBuilder statement = new StringBuilder();
        String activeSeparator = separator;
        int blockCommentDepth = 0;
        char quote = 0;
        boolean lineComment = false;
        boolean lineStart = true;
        int length = script.length();
        int i = 0;

        while (i < length) {
            char c = script.charAt(i);

            if (blockCommentDepth > 0) {
                if (c == '*' && next(script, i) == '/') {
                    blockCommentDepth--;
                    i += 2;
                } else if (c == '/' && next(script, i) == '*') {
                    blockCommentDepth++;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }

            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                    lineStart = true;
                }
                i++;
                continue;
            }

            if (quote != 0) {
                statement.append(c);
                if (c == '\\' && i + 1 < length) {
                    statement.append(script.charAt(i + 1));
                    i += 2;
                } else if (c == quote && next(script, i) == quote) {
                    statement.append(script.charAt(i + 1));
                    i += 2;
                } else {
                    if (c == quote) {
                        quote = 0;
                    }
                    i++;
                }
                continue;
            }

            if (c == '/' && next(script, i) == '*') {
                blockCommentDepth = 1;
                i += 2;
                continue;
            }

            if (c == '#') {
                lineComment = true;
                i++;
                continue;
            }

            if (c == '-' && next(script, i) == '-' && isLineCommentStart(script, i + 2)) {
                lineComment = true;
                i += 2;
                continue;
            }

            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                statement.append(c);
                i++;
                continue;
            }

            if (lineStart && isBlank(statement) && isDirectiveAt(script, i)) {
                int endOfLine = endOfLine(script, i);
                String value = script.substring(i + DELIMITER.length(), endOfLine).trim();
                if (value.isEmpty()) {
                    throw new SQLException("The DELIMITER directive requires a separator value");
                }
                activeSeparator = value;
                i = endOfLine;
                continue;
            }

            if (script.startsWith(activeSeparator, i)) {
                addStatement(statements, statement);
                i += activeSeparator.length();
                lineStart = false;
                continue;
            }

            if (c == '\n') {
                lineStart = true;
            } else if (!Character.isWhitespace(c)) {
                lineStart = false;
            }
            statement.append(c);
            i++;
        }

        if (quote != 0) {
            throw new SQLException("Unbalanced " + quote + " quote in the SQL script");
        }
        if (blockCommentDepth > 0) {
            throw new SQLException("Unterminated block comment in the SQL script");
        }
        addStatement(statements, statement);
        return statements;
    }

    private static void requireSeparator(String separator) throws SQLException {
        if (separator == null || separator.isEmpty()) {
            throw new SQLException("The SQL statement separator must not be null or empty");
        }
    }

    private static void addStatement(List<String> statements, StringBuilder statement) {
        String sql = statement.toString().trim();
        if (!sql.isEmpty()) {
            statements.add(sql);
        }
        statement.setLength(0);
    }

    private static char next(String script, int i) {
        return i + 1 < script.length() ? script.charAt(i + 1) : '\0';
    }

    private static boolean isLineCommentStart(String script, int i) {
        return i >= script.length() || Character.isWhitespace(script.charAt(i));
    }

    private static boolean isDirectiveAt(String script, int i) {
        if (!script.regionMatches(true, i, DELIMITER, 0, DELIMITER.length())) {
            return false;
        }
        int after = i + DELIMITER.length();
        return after >= script.length() || Character.isWhitespace(script.charAt(after));
    }

    private static int endOfLine(String script, int i) {
        int end = script.indexOf('\n', i);
        return end < 0 ? script.length() : end;
    }

    private static boolean isBlank(StringBuilder value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
