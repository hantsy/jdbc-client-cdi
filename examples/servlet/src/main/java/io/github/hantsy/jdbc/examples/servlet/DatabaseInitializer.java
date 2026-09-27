package io.github.hantsy.jdbc.examples.servlet;

import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;

/**
 * Creates the {@code engineers} table once the application scope is initialized.
 */
@ApplicationScoped
public class DatabaseInitializer {

    @Inject
    private DataSource dataSource;

    public void init(@Observes Startup event) throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS engineers ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))");
        }
    }
}
