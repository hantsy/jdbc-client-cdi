package io.github.hantsy.jdbc.tx.it.multids;

import io.github.hantsy.jdbc.tx.cdi.TransactionalCdiExtension;
import io.github.hantsy.jdbc.tx.cdi.TransactionalInterceptor;
import io.github.hantsy.jdbc.tx.it.config.MultiDatabaseConfig;
import io.github.hantsy.jdbc.tx.it.service.CustomerService;
import io.github.hantsy.jdbc.tx.it.service.MultiResourceService;
import io.github.hantsy.jdbc.tx.it.service.OrderService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(WeldJunit5Extension.class)
class MultiDataSourceTxTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
            MultiDatabaseConfig.class,
            OrderService.class,
            CustomerService.class,
            MultiResourceService.class,
            TransactionalInterceptor.class,
            TransactionalCdiExtension.class
    ).build();

    @Inject private OrderService orderService;
    @Inject private CustomerService customerService;
    @Inject private MultiResourceService multiResourceService;

    @Inject @Named("orderRaw") private DataSource orderRaw;
    @Inject @Named("customerRaw") private DataSource customerRaw;

    @BeforeEach
    void initTables() throws Exception {
        try (Connection conn = orderRaw.getConnection(); Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, info VARCHAR(50))");
            s.execute("DELETE FROM orders");
        }
        try (Connection conn = customerRaw.getConnection(); Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS customers (id INT PRIMARY KEY, name VARCHAR(50))");
            s.execute("DELETE FROM customers");
        }
    }

    @Test
    void verifyIndependentMultiDataSourceTransactions() throws Exception {
        orderService.createOrder(301, "Java 25 Book");
        customerService.createCustomer(701, "Hantsy");

        assertEquals(1, countRows(orderRaw, "orders"));
        assertEquals(1, countRows(customerRaw, "customers"));
    }

    @Test
    void verifySingleTransactionJoinsBothDataSources() throws Exception {
        multiResourceService.createOrderAndCustomer(401, 801);

        assertEquals(1, countRows(orderRaw, "orders"));
        assertEquals(1, countRows(customerRaw, "customers"));
    }

    private int countRows(DataSource ds, String tableName) throws Exception {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) return rs.getInt(1);
        }
        return -1;
    }
}
