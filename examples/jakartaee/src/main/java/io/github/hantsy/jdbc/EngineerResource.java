package io.github.hantsy.jdbc;

import io.github.hantsy.jdbc.support.GeneratedKeyHolder;
import io.github.hantsy.jdbc.support.KeyHolder;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * A minimal CRUD resource backed by a CDI-injected {@link JdbcClient}.
 */
@Path("engineers")
@RequestScoped
public class EngineerResource {

    @Inject
    JdbcClient client;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Engineer> all() {
        return client.sql("SELECT id, dev_name FROM engineers ORDER BY id")
                .query(Engineer.class)
                .list();
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Engineer byId(@PathParam("id") Long id) {
        return client.sql("SELECT id, dev_name FROM engineers WHERE id = :id")
                .param("id", id)
                .query(Engineer.class)
                .single();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response insert(Engineer engineer) {
        KeyHolder holder = new GeneratedKeyHolder();
        client.sql("INSERT INTO engineers (dev_name) VALUES (:devName)")
                .param("devName", engineer.devName())
                .update(holder);
        long id = ((Number) holder.getKey()).longValue();
        return Response.status(Response.Status.CREATED).entity(new Engineer(id, engineer.devName())).build();
    }

    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void update(@PathParam("id") Long id, Engineer engineer) {
        client.sql("UPDATE engineers SET dev_name = :devName WHERE id = :id")
                .param("devName", engineer.devName())
                .param("id", id)
                .update();
    }

    @DELETE
    @Path("{id}")
    public void delete(@PathParam("id") Long id) {
        client.sql("DELETE FROM engineers WHERE id = :id")
                .param("id", id)
                .update();
    }
}
