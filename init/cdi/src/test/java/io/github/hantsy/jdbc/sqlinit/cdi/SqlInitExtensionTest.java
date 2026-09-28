package io.github.hantsy.jdbc.sqlinit.cdi;

import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.ServiceLoader;
import javax.sql.DataSource;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Inject;

import static io.github.hantsy.jdbc.sqlinit.cdi.TestDatabases.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableAutoWeld
@AddBeanClasses(FallbackDataSourceProducer.class)
class SqlInitExtensionTest {

    @Inject
    DataSource dataSource;

    @Test
    void isRegisteredAsAPortableExtension() {
        assertTrue(ServiceLoader.load(Extension.class).stream()
                .anyMatch(provider -> provider.type() == SqlInitExtension.class));
    }

    @Test
    void fallsBackToTheDefaultDataSource() throws SQLException {
        new SqlInitExtension().run();

        assertEquals(List.of("Ada", "Grace"), query(dataSource, "SELECT name FROM engineers ORDER BY id"));
    }
}
