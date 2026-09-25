package io.github.hantsy.jdbc.tx.it.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

@ApplicationScoped
public class OrderService {

    @Inject
    @Named("orderDataSource")
    private DataSource orderDs;

    @Transactional
    public void createOrder(int id, String info) throws Exception {
        try (Connection conn = orderDs.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO orders (id, info) VALUES (?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, info);
            ps.executeUpdate();
        }
    }
}
