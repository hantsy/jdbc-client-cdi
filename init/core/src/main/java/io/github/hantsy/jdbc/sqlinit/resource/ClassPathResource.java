package io.github.hantsy.jdbc.sqlinit.resource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * A {@link Resource} resolved from the classpath through a {@link ClassLoader}.
 */
public class ClassPathResource implements Resource {

    private final String path;
    private final URL url;

    public ClassPathResource(String path, ClassLoader classLoader) {
        this.path = path;
        this.url = classLoader.getResource(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        URLConnection connection = url().openConnection();
        if (connection instanceof JarURLConnection jarConnection) {
            jarConnection.setUseCaches(false);
        }
        return connection.getInputStream();
    }

    @Override
    public boolean exists() {
        return url != null;
    }

    @Override
    public long contentLength() throws IOException {
        URLConnection connection = url().openConnection();
        if (connection instanceof JarURLConnection jarConnection) {
            jarConnection.setUseCaches(false);
        }
        return connection.getContentLengthLong();
    }

    @Override
    public URL getURL() throws IOException {
        return url();
    }

    @Override
    public String getFilename() {
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private URL url() throws IOException {
        if (url == null) {
            throw new FileNotFoundException("Classpath resource not found: " + path);
        }
        return url;
    }
}
