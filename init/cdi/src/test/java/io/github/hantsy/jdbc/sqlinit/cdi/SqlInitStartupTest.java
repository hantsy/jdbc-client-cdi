package io.github.hantsy.jdbc.sqlinit.cdi;

import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import static io.github.hantsy.jdbc.sqlinit.cdi.TestDatabases.query;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Asserts the extension runs on the container's startup notification alone: nothing here calls
 * {@link SqlInitExtension#run()}.
 *
 * <p>{@code @AddExtensions} registers the extension with the Weld test harness, which does not scan
 * the classpath for {@code META-INF/services}. In a runtime the service file does that job, which is
 * what {@link SqlInitExtensionTest#isRegisteredAsAPortableExtension()} pins down.</p>
 */
@EnableAutoWeld
@AddExtensions(SqlInitExtension.class)
@AddBeanClasses(StartupDataSourceProducer.class)
class SqlInitStartupTest {

    @Inject
    DataSource dataSource;

    @Test
    void appliesTheScriptsWhenTheContainerStarts() throws SQLException {
        assertEquals(List.of("Ada", "Grace"), query(dataSource, "SELECT name FROM engineers ORDER BY id"));
    }
}
