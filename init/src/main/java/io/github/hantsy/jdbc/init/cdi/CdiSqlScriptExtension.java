package io.github.hantsy.jdbc.init.cdi;

import io.github.hantsy.jdbc.init.SqlScriptPopulator;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.enterprise.inject.spi.CDI;

@ApplicationScoped
public class CdiSqlScriptExtension {

    private static final Logger LOGGER = Logger.getLogger(CdiSqlScriptExtension.class.getName());

    // Fired automatically when the Application context boots up
    public void initDatabase(@Observes Startup event) {
        LOGGER.info("CDI Application initialized. Starting SQL script execution processing...");

        try {
            // Programmatically fetch the DataSource targeting our qualifier
            DataSource dataSource = CDI.current()
                    .select(DataSource.class, DatabaseInitializer.Literal.INSTANCE)
                    .get();

            // Emulating Spring Boot defaults: schema.sql followed by data.sql
            List<String> scripts = Arrays.asList("schema.sql", "data.sql");

            SqlScriptPopulator.executeScripts(dataSource, scripts);

        } catch (Exception e) {
            LOGGER.warning("No @DatabaseInitializer qualified DataSource bean found or script missing. Skipping auto-init.");
        }
    }
}

