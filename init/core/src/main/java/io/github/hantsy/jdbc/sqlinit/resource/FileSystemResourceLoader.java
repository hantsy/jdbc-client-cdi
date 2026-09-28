package io.github.hantsy.jdbc.sqlinit.resource;

import java.nio.file.Paths;

/**
 * A {@link ResourceLoader} that resolves locations against the operating system's file system.
 */
public class FileSystemResourceLoader implements ResourceLoader {

    @Override
    public Resource getResource(String location) {
        return new FileSystemResource(Paths.get(location));
    }

    @Override
    public ClassLoader getClassLoader() {
        return ResourceUtils.defaultClassLoader();
    }
}
