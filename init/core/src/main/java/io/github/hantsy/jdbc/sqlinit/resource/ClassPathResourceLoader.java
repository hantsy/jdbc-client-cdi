package io.github.hantsy.jdbc.sqlinit.resource;

/**
 * A {@link ResourceLoader} that resolves locations against the classpath through a {@link ClassLoader}.
 */
public class ClassPathResourceLoader implements ResourceLoader {

    private final ClassLoader classLoader;

    public ClassPathResourceLoader() {
        this(ResourceUtils.defaultClassLoader());
    }

    public ClassPathResourceLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Resource getResource(String location) {
        return new ClassPathResource(ResourceUtils.stripLeadingSlash(location), classLoader);
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
