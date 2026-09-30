package io.github.hantsy.jdbc.sqlinit.resource;

/**
 * Matches paths against Ant-style wildcard patterns ({@code ?}, {@code *}, {@code **}).
 */
public interface PathMatcher {

    boolean isPattern(String path);

    boolean match(String pattern, String path);
}
