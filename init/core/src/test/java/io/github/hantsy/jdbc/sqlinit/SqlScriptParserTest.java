package io.github.hantsy.jdbc.sqlinit;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptParserTest {

    @Test
    void splitsOnTheDefaultSeparator() throws SQLException {
        assertEquals(List.of("SELECT 1", "SELECT 2"), SqlScriptParser.parse("SELECT 1; SELECT 2;", ";"));
    }

    @Test
    void emitsATrailingStatementWithoutASeparator() throws SQLException {
        assertEquals(List.of("SELECT 1"), SqlScriptParser.parse("SELECT 1", ";"));
    }

    @Test
    void keepsASeparatorInsideAStringLiteral() throws SQLException {
        assertEquals(List.of("INSERT INTO t VALUES ('a;b')"), SqlScriptParser.parse("INSERT INTO t VALUES ('a;b');",
                ";"));
    }

    @Test
    void keepsASeparatorInsideAQuotedIdentifier() throws SQLException {
        assertEquals(List.of("SELECT \"odd;name\" FROM t"), SqlScriptParser.parse("SELECT \"odd;name\" FROM t;", ";"));
        assertEquals(List.of("SELECT `odd;name` FROM t"), SqlScriptParser.parse("SELECT `odd;name` FROM t;", ";"));
    }

    @Test
    void keepsEscapedQuotesInsideAStringLiteral() throws SQLException {
        assertEquals(List.of("SELECT 'it''s'"), SqlScriptParser.parse("SELECT 'it''s';", ";"));
        assertEquals(List.of("SELECT 'a\\'b'"), SqlScriptParser.parse("SELECT 'a\\'b';", ";"));
        assertEquals(List.of("SELECT 'back\\\\slash'"), SqlScriptParser.parse("SELECT 'back\\\\slash';", ";"));
    }

    @Test
    void honoursACustomSeparator() throws SQLException {
        assertEquals(List.of("SELECT 1", "SELECT 2"), SqlScriptParser.parse("SELECT 1\n/\nSELECT 2\n/", "/"));
    }

    @Test
    void honoursAMultiCharacterSeparator() throws SQLException {
        assertEquals(List.of("SELECT 1", "SELECT 2"), SqlScriptParser.parse("SELECT 1//SELECT 2//", "//"));
    }

    @Test
    void stripsLineComments() throws SQLException {
        String script = """
                -- a leading comment
                SELECT 1; -- a trailing comment
                # a hash comment
                SELECT 2;
                """;
        assertEquals(List.of("SELECT 1", "SELECT 2"), SqlScriptParser.parse(script, ";"));
    }

    @Test
    void treatsDashesWithoutTrailingWhitespaceAsOperators() throws SQLException {
        assertEquals(List.of("SELECT a--1 FROM t"), SqlScriptParser.parse("SELECT a--1 FROM t;", ";"));
    }

    @Test
    void stripsNestedBlockComments() throws SQLException {
        assertEquals(List.of("SELECT 1"), SqlScriptParser.parse("/* outer /* inner */ still outer */ SELECT 1;", ";"));
    }

    @Test
    void ignoresBlankAndCommentOnlyScripts() throws SQLException {
        assertTrue(SqlScriptParser.parse("-- nothing\n\n/* nope */\n", ";").isEmpty());
    }

    @Test
    void switchesSeparatorOnADelimiterDirective() throws SQLException {
        String script = """
                DELIMITER //
                CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES (1); INSERT INTO t VALUES (2); END//
                DELIMITER ;
                SELECT 1;
                """;

        List<String> statements = SqlScriptParser.parse(script, ";");

        assertEquals(List.of("CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES (1); INSERT INTO t VALUES (2); END",
                "SELECT 1"), statements);
    }

    @Test
    void ignoresADelimiterDirectiveInTheMiddleOfAStatement() throws SQLException {
        assertEquals(List.of("SELECT 'DELIMITER x'"), SqlScriptParser.parse("SELECT 'DELIMITER x';", ";"));
    }

    @Test
    void rejectsAnEmptySeparator() {
        assertThrows(SQLException.class, () -> SqlScriptParser.parse("SELECT 1", ""));
        assertThrows(SQLException.class, () -> SqlScriptParser.parse("SELECT 1", null));
    }

    @Test
    void rejectsAnUnbalancedQuote() {
        SQLException e = assertThrows(SQLException.class, () -> SqlScriptParser.parse("SELECT 'abc", ";"));
        assertTrue(e.getMessage().contains("Unbalanced"));
    }

    @Test
    void rejectsAnUnterminatedBlockComment() {
        SQLException e = assertThrows(SQLException.class, () -> SqlScriptParser.parse("SELECT 1 /* oops", ";"));
        assertTrue(e.getMessage().contains("Unterminated"));
    }

    @Test
    void rejectsADelimiterDirectiveWithoutAValue() {
        assertThrows(SQLException.class, () -> SqlScriptParser.parse("DELIMITER\nSELECT 1;", ";"));
    }
}
