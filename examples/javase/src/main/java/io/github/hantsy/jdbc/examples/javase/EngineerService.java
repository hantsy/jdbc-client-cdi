package io.github.hantsy.jdbc.examples.javase;

import io.github.hantsy.jdbc.JdbcClient;
import io.github.hantsy.jdbc.support.GeneratedKeyHolder;
import io.github.hantsy.jdbc.support.KeyHolder;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Service layer wrapping the {@link JdbcClient} CRUD operations in resource-local transactions
 * driven by the {@code jdbc-client-tx} interceptor.
 */
@ApplicationScoped
public class EngineerService {

    @Inject
    JdbcClient client;

    @Transactional
    public List<Engineer> findAll() {
        return client.sql("SELECT id, name FROM engineers ORDER BY id")
                .query(Engineer.class)
                .list();
    }

    @Transactional
    public Engineer findById(Long id) {
        return client.sql("SELECT id, name FROM engineers WHERE id = :id")
                .param("id", id)
                .query(Engineer.class)
                .single();
    }

    @Transactional
    public long create(String name) {
        KeyHolder holder = new GeneratedKeyHolder();
        client.sql("INSERT INTO engineers (name) VALUES (:name)")
                .param("name", name)
                .update(holder);
        return ((Number) holder.getKey()).longValue();
    }

    @Transactional
    public void update(Long id, String name) {
        client.sql("UPDATE engineers SET name = :name WHERE id = :id")
                .param("name", name)
                .param("id", id)
                .update();
    }

    @Transactional
    public void delete(Long id) {
        client.sql("DELETE FROM engineers WHERE id = :id")
                .param("id", id)
                .update();
    }

    /**
     * Demonstrates rollback: inserts {@code firstName}, then fails on a blank {@code secondName}, so
     * neither row is committed.
     */
    @Transactional
    public void createTwo(String firstName, String secondName) {
        client.sql("INSERT INTO engineers (name) VALUES (:name)")
                .param("name", firstName)
                .update();
        if (secondName == null || secondName.isBlank()) {
            throw new IllegalStateException("secondName is required");
        }
        client.sql("INSERT INTO engineers (name) VALUES (:name)")
                .param("name", secondName)
                .update();
    }
}
