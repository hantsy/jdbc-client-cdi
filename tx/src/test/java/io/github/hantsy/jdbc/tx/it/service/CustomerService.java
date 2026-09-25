package io.github.hantsy.jdbc.tx.it.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

@ApplicationScoped
public class CustomerService {

    @Inject
    @Named("customerDataSource")
    private DataSource customerDs;

    @Transactional
    public void createCustomer(int id, String name) throws Exception {
        try (Connection conn = customerDs.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO customers (id, name) VALUES (?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }
}
