package io.github.hantsy.jdbc.sqlinit.resource;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A registry mapping a resource protocol to its {@link ResourceResolver}.
 *
 * <p>The {@code classpath:}, {@code file:} and {@code filesystem:} protocols are registered by
 * default; a bare location (no protocol prefix) defaults to {@code classpath:}. Custom protocols,
 * such as {@code s3:}, can be added with {@link #register(String, ResourceResolver)}.</p>
 */
public class ResourceResolverRegistry implements ResourceResolver {

    private final Map<String, ResourceResolver> resolvers = new LinkedHashMap<>();

    public ResourceResolverRegistry() {
        register("classpath", new ClassPathResourceResolver());
        register("file", new FileSystemResourceResolver());
        register("filesystem", new FileSystemResourceResolver());
    }

    /**
     * Registers the resolver for the given protocol (without the trailing colon).
     */
    public ResourceResolverRegistry register(String protocol, ResourceResolver resolver) {
        resolvers.put(protocol, resolver);
        return this;
    }

    @Override
    public Resource getResource(String location) {
        String protocol = protocol(location);
        return resolvers.get(protocol).getResource(stripProtocol(location, protocol));
    }

    @Override
    public List<Resource> getResources(String locationPattern) throws IOException {
        String protocol = protocol(locationPattern);
        return resolvers.get(protocol).getResources(stripProtocol(locationPattern, protocol));
    }

    private String protocol(String location) {
        for (String protocol : resolvers.keySet()) {
            if (location.startsWith(protocol + ":")) {
                return protocol;
            }
        }
        int colon = location.indexOf(':');
        int slash = location.indexOf('/');
        if (colon > 0 && (slash < 0 || colon < slash)) {
            throw new IllegalArgumentException("Unknown resource protocol '" + location.substring(0, colon)
                    + "' in location: " + location);
        }
        return "classpath";
    }

    private static String stripProtocol(String location, String protocol) {
        String prefix = protocol + ":";
        return location.startsWith(prefix) ? location.substring(prefix.length()) : location;
    }
}
