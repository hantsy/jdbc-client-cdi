package io.github.hantsy.jdbc.sqlinit.cdi;

import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import static io.github.hantsy.jdbc.sqlinit.cdi.TestDatabases.query;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Asserts the bootstrapper runs on the container's startup notification alone: nothing here calls
 * {@link SqlInitBootstrapper#run()}.
 */
@EnableAutoWeld
@AddBeanClasses({SqlInitBootstrapper.class, StartupDataSourceProducer.class})
class SqlInitStartupTest {

    @Inject
    DataSource dataSource;

    @Test
    void appliesTheScriptsWhenTheContainerStarts() throws SQLException {
        assertEquals(List.of("Ada", "Grace"), query(dataSource, "SELECT name FROM engineers ORDER BY id"));
    }
}
