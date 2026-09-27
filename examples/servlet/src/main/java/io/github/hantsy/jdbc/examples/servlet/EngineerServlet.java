package io.github.hantsy.jdbc.examples.servlet;

import java.io.IOException;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A minimal CRUD servlet backed by the transactional {@link EngineerService}.
 *
 * <p>Endpoints: {@code GET /engineers}, {@code GET /engineers/{id}}, {@code POST /engineers?name=...},
 * {@code PUT /engineers/{id}?name=...}, {@code DELETE /engineers/{id}}.</p>
 */
@WebServlet("/engineers/*")
public class EngineerServlet extends HttpServlet {

    @Inject
    EngineerService service;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");
        String id = idFromPath(req);
        if (id == null) {
            List<Engineer> all = service.findAll();
            resp.getWriter().println(all);
        } else {
            Engineer engineer = service.findById(Long.parseLong(id));
            resp.getWriter().println(engineer);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String name = req.getParameter("name");
        long id = service.create(name);
        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.getWriter().println("created id=" + id);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String id = idFromPath(req);
        String name = req.getParameter("name");
        service.update(Long.parseLong(id), name);
        resp.getWriter().println("updated id=" + id);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String id = idFromPath(req);
        service.delete(Long.parseLong(id));
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
