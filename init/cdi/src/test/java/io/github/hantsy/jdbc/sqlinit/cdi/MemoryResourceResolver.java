package io.github.hantsy.jdbc.sqlinit.cdi;

import io.github.hantsy.jdbc.sqlinit.resource.Resource;
import io.github.hantsy.jdbc.sqlinit.resource.ResourceResolver;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A test {@link ResourceResolver} that claims the {@code memory:} protocol.
 */
@ApplicationScoped
public class MemoryResourceResolver implements ResourceResolver {

    @Override
    public String protocol() {
        return "memory";
    }

    @Override
    public Resource getResource(String location) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Resource> getResources(String pattern) {
        return List.of();
    }
}
