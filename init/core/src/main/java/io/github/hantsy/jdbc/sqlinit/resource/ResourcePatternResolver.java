package io.github.hantsy.jdbc.sqlinit.resource;

import java.io.IOException;
import java.util.List;

/**
 * Resolves a location pattern to the resources it matches.
 *
 * <p>A location is a literal file, a directory (scanned recursively), or an Ant-style pattern
 * ({@code **}, {@code *}, {@code ?}), prefixed with {@code classpath:}, {@code file:} or
 * {@code filesystem:} (a bare location defaults to {@code classpath:}).</p>
 */
public interface ResourcePatternResolver extends ResourceLoader {

    /**
     * Resolves a single location pattern into the resources it matches, sorted by filename.
     *
     * <p>A literal file that cannot be found throws a {@link java.io.FileNotFoundException}; a
     * directory or pattern that matches nothing returns an empty list.</p>
     */
    List<Resource> getResources(String locationPattern) throws IOException;
}
