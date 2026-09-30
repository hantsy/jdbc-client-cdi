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
@AddBeanClasses({SqlInitBootstrapper.class, PreferredDataSourceProducer.class})
class SqlInitQualifiedDataSourceTest {

    @Inject
    SqlInitBootstrapper bootstrapper;

    @Inject
    DataSource dataSource;

    @Inject
    @SqlInit
    DataSource sqlInitDataSource;

    @Test
    void prefersTheQualifiedDataSource() throws SQLException {
        bootstrapper.run();

        assertEquals(List.of("Ada", "Grace"), query(sqlInitDataSource, "SELECT name FROM engineers ORDER BY id"));
        assertEquals(List.of("0"), query(dataSource,
                "SELECT CAST(COUNT(*) AS VARCHAR) FROM information_schema.tables WHERE table_name = 'ENGINEERS'"));
    }
}
