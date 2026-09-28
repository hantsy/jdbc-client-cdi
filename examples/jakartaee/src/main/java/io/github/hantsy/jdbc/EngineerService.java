package io.github.hantsy.jdbc;

import io.github.hantsy.jdbc.support.GeneratedKeyHolder;
import io.github.hantsy.jdbc.support.KeyHolder;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Service layer wrapping the {@link JdbcClient} CRUD operations in container-managed JTA
 * transactions. On a full Jakarta EE runtime the container supplies the {@code @Transactional}
 * interceptor, so no {@code jdbc-client-tx} dependency is needed.
 */
@ApplicationScoped
public class EngineerService {

    @Inject
    JdbcClient client;

    @Transactional
    public List<Engineer> findAll() {
        return client.sql("SELECT id, dev_name FROM engineers ORDER BY id")
                .query(Engineer.class)
                .list();
    }

    @Transactional
    public Engineer findById(Long id) {
        return client.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .param("id", id)
                .query(Engineer.class)
                .single();
    }

    @Transactional
    public Engineer create(Engineer engineer) {
        KeyHolder holder = new GeneratedKeyHolder();
        client.sql("INSERT INTO engineers (dev_name) VALUES (:devName)")
                .param("devName", engineer.devName())
                .update(holder);
        long id = ((Number) holder.getKey()).longValue();
        return new Engineer(id, engineer.devName());
    }

    @Transactional
    public void update(Long id, Engineer engineer) {
        client.sql("UPDATE engineers SET dev_name = :devName WHERE id = :id")
                .param("devName", engineer.devName())
                .param("id", id)
                .update();
    }

    @Transactional
    public void delete(Long id) {
        client.sql("DELETE FROM engineers WHERE id = :id")
                .param("id", id)
                .update();
    }
}
