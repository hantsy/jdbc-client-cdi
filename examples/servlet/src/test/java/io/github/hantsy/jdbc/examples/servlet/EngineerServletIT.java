package io.github.hantsy.jdbc.examples.servlet;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ArquillianTest
public class EngineerServletIT {

    @ArquillianResource
    private URL baseUrl;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        var libs = Maven.resolver().loadPomFromFile("pom.xml")
                .resolve("io.github.hantsy.jdbc:jdbc-client-core",
                        "com.h2database:h2")
                .withTransitivity()
                .asFile();

        String webXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.1">
                  <resource-ref>
                    <res-ref-name>jdbc/myDS</res-ref-name>
                    <res-type>javax.sql.DataSource</res-type>
                    <res-auth>Container</res-auth>
                  </resource-ref>
                </web-app>
                """;

        String contextXml = """
                <Context>
                  <Resource name="jdbc/myDS" auth="Container" type="javax.sql.DataSource"
                            driverClassName="org.h2.Driver"
                            url="jdbc:h2:mem:servlet;DB_CLOSE_DELAY=-1"
                            username="sa" password="" maxTotal="8" maxIdle="4"/>
                </Context>
                """;

        return ShrinkWrap.create(WebArchive.class, "servlet-example.war")
                .addClass(EngineerServlet.class)
                .addClass(Engineer.class)
                .addAsLibraries(libs)
                .addAsWebInfResource(new StringAsset(webXml), "web.xml")
                .add(new StringAsset(contextXml), "META-INF/context.xml");
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
