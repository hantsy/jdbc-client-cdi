package io.github.hantsy.jdbc.sqlinit.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link Resource} backed by a file on the file system.
 */
public class FileSystemResource implements Resource {

    private final Path path;

    public FileSystemResource(Path path) {
        this.path = path;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public boolean exists() {
        return Files.exists(path);
    }

    @Override
    public long contentLength() throws IOException {
        return Files.size(path);
    }

    @Override
    public URL getURL() throws IOException {
        return path.toUri().toURL();
    }

    @Override
    public String getFilename() {
        Path fileName = path.getFileName();
        return fileName == null ? null : fileName.toString();
    }
}
