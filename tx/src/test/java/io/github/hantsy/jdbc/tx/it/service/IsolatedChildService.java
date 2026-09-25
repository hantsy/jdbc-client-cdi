package io.github.hantsy.jdbc.tx.it.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

@ApplicationScoped
public class IsolatedChildService {

    @Inject
    private DataSource dataSource;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void executeIsolatedChildTransaction() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO orders (id, detail) VALUES (20, 'Child Data')")) {
            ps.executeUpdate();
        }
        throw new RuntimeException("Isolated child transaction branch failure!");
    }
}
