package io.github.hantsy.jdbc.tx.it.propagation;

import io.github.hantsy.jdbc.tx.cdi.TransactionalCdiExtension;
import io.github.hantsy.jdbc.tx.cdi.TransactionalInterceptor;
import io.github.hantsy.jdbc.tx.it.config.ApplicationDatabaseConfig;
import io.github.hantsy.jdbc.tx.it.service.IsolatedChildService;
import io.github.hantsy.jdbc.tx.it.service.OrderProcessingService;
import jakarta.inject.Inject;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(WeldJunit5Extension.class)
class PropagationRequiresNewTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
            ApplicationDatabaseConfig.class,
            OrderProcessingService.class,
            IsolatedChildService.class,
            TransactionalInterceptor.class,
            TransactionalCdiExtension.class
    ).build();

    @Inject private OrderProcessingService orderService;
    @Inject private DataSource dataSource;

    @BeforeEach
    void setupDatabaseTable() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, detail VARCHAR(255))");
            stmt.execute("DELETE FROM orders");
        }
    }

    @Test
    void verifyRequiresNewIsolationBehavior() throws Exception {
        orderService.executeParentWithIsolatedChild();

        assertEquals(1, countOrders(), "Only the parent row must survive.");
        assertTrue(orderExists(10), "Parent data must be committed.");
        assertFalse(orderExists(20), "Isolated child transaction data must be rolled back.");
    }

    private int countOrders() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM orders")) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            fail("Database monitoring check failed.");
        }
        return -1;
    }

    private boolean orderExists(int id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM orders WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
