package io.github.hantsy.jdbc.sqlinit.resource;

/**
 * Resolves a literal location to a single {@link Resource}.
 */
public interface ResourceLoader {

    Resource getResource(String location);

    ClassLoader getClassLoader();
}
