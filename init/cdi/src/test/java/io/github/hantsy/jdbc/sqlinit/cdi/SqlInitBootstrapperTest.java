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

@EnableAutoWeld
@AddBeanClasses({SqlInitBootstrapper.class, FallbackDataSourceProducer.class})
class SqlInitBootstrapperTest {

    @Inject
    SqlInitBootstrapper bootstrapper;

    @Inject
    DataSource dataSource;

    @Test
    void fallsBackToTheDefaultDataSource() throws SQLException {
        bootstrapper.run();

        assertEquals(List.of("Ada", "Grace"), query(dataSource, "SELECT name FROM engineers ORDER BY id"));
    }
}
