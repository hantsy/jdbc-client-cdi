package io.github.hantsy.jdbc.sqlinit.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntPathMatcherTest {

    private final PathMatcher matcher = new AntPathMatcher();

    @Test
    void matchesALiteralPath() {
        assertTrue(matcher.match("db/schema.sql", "db/schema.sql"));
        assertFalse(matcher.match("db/schema.sql", "db/other.sql"));
        assertFalse(matcher.match("db/schema.sql", "db/schema.sql.bak"));
    }

    @Test
    void matchesASingleSegmentWildcard() {
        assertTrue(matcher.match("*.sql", "schema.sql"));
        assertFalse(matcher.match("*.sql", "db/schema.sql"));
        assertTrue(matcher.match("db/*.sql", "db/schema.sql"));
        assertFalse(matcher.match("db/*.sql", "db/nested/schema.sql"));
    }

    @Test
    void matchesASingleCharacterWildcard() {
        assertTrue(matcher.match("V?.sql", "V1.sql"));
        assertFalse(matcher.match("V?.sql", "V12.sql"));
    }

    @Test
    void matchesDoubleStarAcrossSegments() {
        assertTrue(matcher.match("**/*.sql", "schema.sql"));
        assertTrue(matcher.match("**/*.sql", "db/nested/schema.sql"));
        assertFalse(matcher.match("**/*.sql", "db/nested/schema.txt"));
    }

    @Test
    void matchesDoubleStarInMiddleOfPattern() {
        assertTrue(matcher.match("db/**/schema.sql", "db/schema.sql"));
        assertTrue(matcher.match("db/**/schema.sql", "db/a/b/schema.sql"));
        assertFalse(matcher.match("db/**/schema.sql", "other/schema.sql"));
    }

    @Test
    void ignoresLeadingSlashes() {
        assertTrue(matcher.match("/db/*.sql", "/db/schema.sql"));
        assertTrue(matcher.match("/db/*.sql", "db/schema.sql"));
    }

    @Test
    void detectsPatterns() {
        assertTrue(matcher.isPattern("db/*.sql"));
        assertTrue(matcher.isPattern("db/?/x.sql"));
        assertFalse(matcher.isPattern("db/schema.sql"));
    }
}
