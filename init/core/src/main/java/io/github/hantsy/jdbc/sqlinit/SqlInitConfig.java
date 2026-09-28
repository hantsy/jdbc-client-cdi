package io.github.hantsy.jdbc.sqlinit;

import java.util.List;

/**
 * Plain configuration for SQL script initialization.
 */
public class SqlInitConfig {

    public static final SqlInitConfig DEFAULT = new SqlInitConfig(";", List.of("/schema.sql"), List.of("/data.sql"));

    private String separator;
    private List<String> schemaLocations;
    private List<String> dataLocations;

    /**
     * No-arg constructor required for CDI client proxies.
     */
    public SqlInitConfig() {
    }

    public SqlInitConfig(String separator, List<String> schemaLocations, List<String> dataLocations) {
        this.separator = separator;
        this.schemaLocations = schemaLocations;
        this.dataLocations = dataLocations;
    }

    /**
     * The character sequence that terminates a statement inside a script (default {@code ;}).
     *
     * <p>Separators may be longer than one character, which is what a {@code DELIMITER} directive
     * in a script switches to when it wraps a stored-procedure body.</p>
     */
    public String separator() {
        return separator;
    }

    /**
     * The classpath locations holding schema (DDL) scripts, executed before the data locations
     * (default {@code /schema.sql}).
     *
     * <p>A location is either a literal classpath resource such as {@code classpath:/db/schema.sql}
     * or an Ant-style pattern such as {@code classpath*:/db/migration/&#42;&#42;/&#42;.sql}. Scripts
     * resolved from one location are executed in alphabetical order of their resource path.</p>
     */
    public List<String> schemaLocations() {
        return schemaLocations;
    }

    /**
     * The classpath locations holding data (DML) scripts, executed after the schema locations
     * (default {@code /data.sql}).
     *
     * <p>Accepts the same literal paths and Ant-style patterns as
     * {@link #schemaLocations()}.</p>
     */
    public List<String> dataLocations() {
        return dataLocations;
    }
}
