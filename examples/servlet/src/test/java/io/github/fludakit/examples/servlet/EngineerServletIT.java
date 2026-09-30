package io.github.fludakit.examples.servlet;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ArquillianTest
public class EngineerServletIT {
    private static final Logger LOGGER = Logger.getLogger(EngineerServletIT.class.getName());
    @ArquillianResource
    private URL baseUrl;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        WebArchive archive = ShrinkWrap.create(WebArchive.class, "servlet-example.war")
                .addClasses(
                        DataSourceProducer.class,
                        DatabaseInitializer.class,
                        Engineer.class,
                        EngineerServlet.class
                )
                .addAsWebInfResource("test-web.xml", "web.xml")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource("test-context.xml", "context.xml");
        LOGGER.log(Level.INFO, "deployment archive: {0}", new Object[]{archive.toString(true)});
        return archive;
    }

    @Test
    void crudOverHttp() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        URI base = URI.create(baseUrl.toString().endsWith("/")
                ? baseUrl.toString() : baseUrl + "/");

        // insert
        HttpResponse<String> created = http.send(
                HttpRequest.newBuilder(base.resolve("engineers?name=Ada"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, created.statusCode(), created.body());

        // get all
        HttpResponse<String> all = http.send(
                HttpRequest.newBuilder(base.resolve("engineers")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, all.statusCode(), all.body());
        assertTrue(all.body().contains("Ada"), all.body());

        // get by id (the only row has id 1)
        HttpResponse<String> byId = http.send(
                HttpRequest.newBuilder(base.resolve("engineers/1")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, byId.statusCode(), byId.body());
        assertTrue(byId.body().contains("Ada"), byId.body());

        // update
        HttpResponse<String> updated = http.send(
                HttpRequest.newBuilder(base.resolve("engineers/1?name=Ada%20Lovelace"))
                        .PUT(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updated.statusCode(), updated.body());

        // delete
        HttpResponse<String> deleted = http.send(
                HttpRequest.newBuilder(base.resolve("engineers/1")).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleted.statusCode(), deleted.body());
    }
}
