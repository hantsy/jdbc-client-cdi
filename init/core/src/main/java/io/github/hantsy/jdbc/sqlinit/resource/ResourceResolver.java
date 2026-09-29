package io.github.hantsy.jdbc.sqlinit.resource;

import java.io.IOException;
import java.util.List;

/**
 * Resolves a location for a single protocol into one or more {@link Resource}s.
 *
 * <p>A resolver handles a location without its protocol prefix: {@code classpath:},
 * {@code file:}/{@code filesystem:}, or a custom scheme such as {@code s3:}.</p>
 */
public interface ResourceResolver {

    /** Resolves a literal location to a single resource, which may not exist. */
    Resource getResource(String location);

    /**
     * Resolves a location pattern (literal file, directory, or Ant-style pattern) into the resources
     * it matches, sorted by filename.
     */
    List<Resource> getResources(String locationPattern) throws IOException;
}
