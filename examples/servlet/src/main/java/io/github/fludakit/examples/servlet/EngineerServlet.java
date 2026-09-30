package io.github.fludakit.examples.servlet;

import io.github.fludakit.jdbc.JdbcClient;
import io.github.fludakit.jdbc.support.GeneratedKeyHolder;
import io.github.fludakit.jdbc.support.KeyHolder;

import java.io.IOException;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A minimal CRUD servlet backed by a CDI-injected {@link JdbcClient}.
 *
 * <p>Endpoints: {@code GET /engineers}, {@code GET /engineers/{id}}, {@code POST /engineers?name=...},
 * {@code PUT /engineers/{id}?name=...}, {@code DELETE /engineers/{id}}.</p>
 */
@WebServlet("/engineers/*")
public class EngineerServlet extends HttpServlet {

    @Inject
    JdbcClient client;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");
        String id = idFromPath(req);
        if (id == null) {
            List<Engineer> all = client.sql("SELECT id, name FROM engineers ORDER BY id")
                    .query(Engineer.class).list();
            resp.getWriter().println(all);
        } else {
            Engineer engineer = client.sql("SELECT id, name FROM engineers WHERE id = :id")
                    .param("id", Long.parseLong(id)).query(Engineer.class).single();
            resp.getWriter().println(engineer);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String name = req.getParameter("name");
        KeyHolder holder = new GeneratedKeyHolder();
        client.sql("INSERT INTO engineers (name) VALUES (:name)").param("name", name).update(holder);
        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.getWriter().println("created id=" + ((Number) holder.getKey()).longValue());
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String id = idFromPath(req);
        String name = req.getParameter("name");
        client.sql("UPDATE engineers SET name = :name WHERE id = :id")
                .param("name", name).param("id", Long.parseLong(id)).update();
        resp.getWriter().println("updated id=" + id);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String id = idFromPath(req);
        client.sql("DELETE FROM engineers WHERE id = :id").param("id", Long.parseLong(id)).update();
        resp.getWriter().println("deleted id=" + id);
    }

    private String idFromPath(HttpServletRequest req) {
        String path = req.getPathInfo();
        if (path == null || path.equals("/") || path.isBlank()) {
            return null;
        }
        return path.substring(1);
    }
}
