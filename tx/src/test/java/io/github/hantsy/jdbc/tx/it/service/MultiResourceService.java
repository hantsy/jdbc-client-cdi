package io.github.hantsy.jdbc.tx.it.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

/**
 * Touches two DataSources within a single {@code @Transactional} method to exercise the
 * best-effort multi-resource join.
 */
@ApplicationScoped
public class MultiResourceService {

    @Inject
    @Named("orderDataSource")
    private DataSource orderDs;

    @Inject
    @Named("customerDataSource")
    private DataSource customerDs;

    @Transactional
    public void createOrderAndCustomer(int orderId, int customerId) throws Exception {
        try (Connection conn = orderDs.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO orders (id, info) VALUES (?, ?)")) {
            ps.setInt(1, orderId);
            ps.setString(2, "order");
            ps.executeUpdate();
        }
        try (Connection conn = customerDs.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO customers (id, name) VALUES (?, ?)")) {
            ps.setInt(1, customerId);
            ps.setString(2, "customer");
            ps.executeUpdate();
        }
    }
}
