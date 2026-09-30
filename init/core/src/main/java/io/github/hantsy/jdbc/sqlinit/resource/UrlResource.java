package io.github.hantsy.jdbc.sqlinit.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * A {@link Resource} backed by a resolved {@link URL}, such as a classpath or jar entry.
 */
public class UrlResource implements Resource {

    private final URL url;

    public UrlResource(URL url) {
        this.url = url;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        URLConnection connection = url.openConnection();
        if (connection instanceof JarURLConnection jarConnection) {
            // The JDK jar cache would keep the archive open for the lifetime of the JVM.
            jarConnection.setUseCaches(false);
        }
        return connection.getInputStream();
    }

    @Override
    public boolean exists() {
        // The URL was already resolved when this resource was built.
        return true;
    }

    @Override
    public long contentLength() throws IOException {
        URLConnection connection = url.openConnection();
        if (connection instanceof JarURLConnection jarConnection) {
            jarConnection.setUseCaches(false);
        }
        return connection.getContentLengthLong();
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public String getFilename() {
        return filename(url.getPath());
    }

    private static String filename(String path) {
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }
}
