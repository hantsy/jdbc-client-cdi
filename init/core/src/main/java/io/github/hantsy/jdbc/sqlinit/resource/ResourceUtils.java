package io.github.hantsy.jdbc.sqlinit.resource;

final class ResourceUtils {

    private ResourceUtils() {
    }

    static ClassLoader defaultClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : ResourceUtils.class.getClassLoader();
    }

    static String stripLeadingSlash(String location) {
        String result = location;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }
}
