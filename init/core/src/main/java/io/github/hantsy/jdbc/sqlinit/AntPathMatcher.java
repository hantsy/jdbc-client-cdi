package io.github.hantsy.jdbc.sqlinit;

/**
 * Ant-style path matching over {@code /} separated classpath resource paths.
 *
 * <p>{@code ?} matches a single character, {@code *} matches any run of characters inside one path
 * segment, and {@code **} matches any number of segments including none.</p>
 */
final class AntPathMatcher {

    private AntPathMatcher() {
    }

    static boolean match(String pattern, String path) {
        return matchSegments(split(pattern), 0, split(path), 0);
    }

    static boolean isPattern(String path) {
        return path.indexOf('*') >= 0 || path.indexOf('?') >= 0;
    }

    private static String[] split(String path) {
        String normalized = path;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return new String[0];
        }
        return normalized.split("/");
    }

    private static boolean matchSegments(String[] pattern, int p, String[] path, int s) {
        if (p == pattern.length) {
            return s == path.length;
        }
        if ("**".equals(pattern[p])) {
            for (int skip = s; skip <= path.length; skip++) {
                if (matchSegments(pattern, p + 1, path, skip)) {
                    return true;
                }
            }
            return false;
        }
        if (s == path.length || !matchSegment(pattern[p], path[s])) {
            return false;
        }
        return matchSegments(pattern, p + 1, path, s + 1);
    }

    private static boolean matchSegment(String pattern, String text) {
        return matchChars(pattern, 0, text, 0);
    }

    private static boolean matchChars(String pattern, int p, String text, int t) {
        while (p < pattern.length()) {
            char c = pattern.charAt(p);
            if (c == '*') {
                for (int skip = t; skip <= text.length(); skip++) {
                    if (matchChars(pattern, p + 1, text, skip)) {
                        return true;
                    }
                }
                return false;
            }
            if (t == text.length()) {
                return false;
            }
            if (c != '?' && c != text.charAt(t)) {
                return false;
            }
            p++;
            t++;
        }
        return t == text.length();
    }
}
