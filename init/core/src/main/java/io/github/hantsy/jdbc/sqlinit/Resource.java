package io.github.hantsy.jdbc.sqlinit;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * A resolved SQL script: a display name and an openable location.
 */
record Resource(String name, URL url) implements Comparable<Resource> {

    InputStream open() throws IOException {
        URLConnection connection = url.openConnection();
        if (connection instanceof JarURLConnection jarConnection) {
            // The JDK jar cache would keep the archive open for the lifetime of the JVM.
            jarConnection.setUseCaches(false);
        }
        return connection.getInputStream();
    }

    String fileName() {
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    @Override
    public int compareTo(Resource other) {
        int result = name.compareTo(other.name);
        return result != 0 ? result : url.toExternalForm().compareTo(other.url.toExternalForm());
    }
}
