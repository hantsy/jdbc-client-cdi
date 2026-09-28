package io.github.hantsy.jdbc.sqlinit;

import io.github.hantsy.jdbc.sqlinit.resource.Resource;

/**
 * A single versioned migration parsed from a resolved script resource.
 */
record Migration(int version, String description, Resource resource) {

    String script() {
        return resource.getFilename();
    }
}
