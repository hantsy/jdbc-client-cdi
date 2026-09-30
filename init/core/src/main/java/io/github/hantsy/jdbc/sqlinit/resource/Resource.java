package io.github.hantsy.jdbc.sqlinit.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * A physical resource: a file, a classpath entry, or a URL, accessed uniformly.
 */
public interface Resource {

    InputStream getInputStream() throws IOException;

    boolean exists();

    long contentLength() throws IOException;

    URL getURL() throws IOException;

    /** The filename (last path segment) of this resource, or {@code null} if it has none. */
    String getFilename();
}
