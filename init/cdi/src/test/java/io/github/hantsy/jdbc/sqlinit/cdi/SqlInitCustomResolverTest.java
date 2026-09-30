package io.github.hantsy.jdbc.sqlinit.cdi;

import io.github.hantsy.jdbc.sqlinit.resource.ResourceResolver;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableAutoWeld
@AddBeanClasses({SqlInitBootstrapper.class, MemoryResourceResolver.class})
class SqlInitCustomResolverTest {

    @Inject
    SqlInitBootstrapper bootstrapper;

    @Test
    void registersCustomResolvers() throws IOException {
        ResourceResolver resolver = bootstrapper.resourceResolver();

        // memory: is registered, so resolving it dispatches to MemoryResourceResolver (empty) rather
        // than failing with "unknown protocol".
        assertTrue(resolver.getResources("memory:migrations").isEmpty());
    }
}
