package io.github.hantsy.jdbc.sqlinit.cdi;

import io.github.hantsy.jdbc.sqlinit.DbMigrator;
import io.github.hantsy.jdbc.sqlinit.SqlInitConfig;

import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Extension;

/**
 * Applies the configured SQL scripts when the application starts.
 *
 * <p>Registered as a portable extension through
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}, so it becomes active as soon as
 * this artifact is on the classpath and needs no bean-discovery configuration.</p>
 *
 * <p>The {@link DataSource} is resolved by preferring one qualified with {@link SqlInit} and falling
 * back to the default unqualified {@code DataSource} bean. The {@link SqlInitConfig} bean is
 * optional: when the {@code config} module is absent, {@link SqlInitConfig#DEFAULT} is used.</p>
 */
public class SqlInitExtension implements Extension {

    private static final Logger LOGGER = Logger.getLogger(SqlInitExtension.class.getName());

    /**
     * Applies the scripts once the container has started.
     *
     * @param event the container startup notification
     */
    public void initialize(@Observes Startup event) {
        try {
            run();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize the database from SQL scripts", e);
        }
    }

    /**
     * Resolves the {@link DataSource} and {@link SqlInitConfig} beans and applies the scripts they
     * are configured with.
     *
     * @throws SQLException if a script cannot be read, parsed, or applied; everything already applied
     *                      in the transaction is rolled back first
     */
    public void run() throws SQLException {
        CDI<Object> cdi = CDI.current();
        DataSource dataSource = dataSource(cdi);
        if (dataSource == null) {
            LOGGER.warning("No DataSource bean found, skipping SQL script initialization");
            return;
        }
        Instance<SqlInitConfig> configs = cdi.select(SqlInitConfig.class);
        SqlInitConfig config = configs.isResolvable() ? configs.get() : SqlInitConfig.defaults();
        new DbMigrator(dataSource, config).migrate();
    }

    private DataSource dataSource(Instance<Object> beans) {
        Instance<DataSource> qualified = beans.select(DataSource.class, SqlInit.Literal.INSTANCE);
        if (qualified.isResolvable()) {
            LOGGER.fine("Initializing the @SqlInit qualified DataSource");
            return qualified.get();
        }
        Instance<DataSource> unqualified = beans.select(DataSource.class);
        return unqualified.isResolvable() ? unqualified.get() : null;
    }
}
