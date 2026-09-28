package io.github.hantsy.jdbc.sqlinit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntPathMatcherTest {

    @Test
    void matchesALiteralPath() {
        assertTrue(AntPathMatcher.match("db/schema.sql", "db/schema.sql"));
        assertFalse(AntPathMatcher.match("db/schema.sql", "db/other.sql"));
        assertFalse(AntPathMatcher.match("db/schema.sql", "db/schema.sql.bak"));
    }

    @Test
    void matchesASingleSegmentWildcard() {
        assertTrue(AntPathMatcher.match("*.sql", "schema.sql"));
        assertFalse(AntPathMatcher.match("*.sql", "db/schema.sql"));
        assertTrue(AntPathMatcher.match("db/*.sql", "db/schema.sql"));
        assertFalse(AntPathMatcher.match("db/*.sql", "db/nested/schema.sql"));
    }

    @Test
    void matchesASingleCharacterWildcard() {
        assertTrue(AntPathMatcher.match("V?.sql", "V1.sql"));
        assertFalse(AntPathMatcher.match("V?.sql", "V12.sql"));
    }

    @Test
    void matchesDoubleStarAcrossSegments() {
        assertTrue(AntPathMatcher.match("**/*.sql", "schema.sql"));
        assertTrue(AntPathMatcher.match("**/*.sql", "db/nested/schema.sql"));
        assertFalse(AntPathMatcher.match("**/*.sql", "db/nested/schema.txt"));
    }

    @Test
    void matchesDoubleStarInMiddleOfPattern() {
        assertTrue(AntPathMatcher.match("db/**/schema.sql", "db/schema.sql"));
        assertTrue(AntPathMatcher.match("db/**/schema.sql", "db/a/b/schema.sql"));
        assertFalse(AntPathMatcher.match("db/**/schema.sql", "other/schema.sql"));
    }

    @Test
    void ignoresLeadingSlashes() {
        assertTrue(AntPathMatcher.match("/db/*.sql", "/db/schema.sql"));
        assertTrue(AntPathMatcher.match("/db/*.sql", "db/schema.sql"));
    }

    @Test
    void detectsPatterns() {
        assertTrue(AntPathMatcher.isPattern("db/*.sql"));
        assertTrue(AntPathMatcher.isPattern("db/?/x.sql"));
        assertFalse(AntPathMatcher.isPattern("db/schema.sql"));
    }
}
