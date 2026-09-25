package io.github.hantsy.jdbc.tx.it.classification;

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
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(WeldJunit5Extension.class)
class TransactionExceptionClassificationTest {

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
    void verifyExplicitRollbackOnCheckedException() {
        assertThrows(Exception.class, () -> orderService.writeAndThrowCustomChecked());
        assertEquals(0, countOrders(), "rollbackOn checked exception must roll back.");
    }

    @Test
    void verifyExplicitDontRollbackOnUncheckedException() {
        assertThrows(RuntimeException.class, () -> orderService.writeAndThrowCustomUnchecked());
        assertEquals(1, countOrders(), "dontRollbackOn runtime exception must commit.");
    }

    @Test
    void verifyDefaultRollbackOnRuntimeException() {
        assertThrows(RuntimeException.class, () -> orderService.writeAndThrowRuntime());
        assertEquals(0, countOrders(), "default rule must roll back on RuntimeException.");
    }

    @Test
    void verifyDefaultCommitOnCheckedException() {
        assertThrows(Exception.class, () -> orderService.writeAndThrowChecked());
        assertEquals(1, countOrders(), "default rule must commit on a checked exception.");
    }

    @Test
    void verifyDefaultRollbackOnError() {
        assertThrows(Error.class, () -> orderService.writeAndThrowError());
        assertEquals(0, countOrders(), "default rule must roll back on an Error.");
    }

    @Test
    void verifyDontRollbackOnTakesPrecedenceOverRollbackOn() {
        assertThrows(RuntimeException.class, () -> orderService.writeAndThrowInBoth());
        assertEquals(1, countOrders(), "dontRollbackOn must win when a class is listed in both.");
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
}
