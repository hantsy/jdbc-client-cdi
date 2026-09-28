package io.github.fludakit.examples.jakartaee;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Activates JAX-RS under the {@code /api} context path.
 */
@ApplicationPath("api")
public class JaxRsApplication extends Application {
}
