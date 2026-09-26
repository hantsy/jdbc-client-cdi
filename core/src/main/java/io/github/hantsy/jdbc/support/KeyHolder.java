package io.github.hantsy.jdbc.support;

import io.github.hantsy.jdbc.JdbcClient;

import java.util.List;
import java.util.Map;

/**
 * Holds the auto-generated keys returned by a JDBC {@code
 * RETURN_GENERATED_KEYS} update. Mirrors Spring's {@code
 * org.springframework.jdbc.support.KeyHolder}.
 */
public interface KeyHolder {

    /**
     * The single generated key (first column of the first row), or {@code null}
     * if no key was generated. The concrete type depends on the column — e.g.
     * {@code Long} for an auto-increment id, {@link java.util.UUID} for a UUID
     * key, or {@code String}.
     */
    Object getKey();

    /**
     * The generated keys of the first row, keyed by column label.
     */
    Map<String, Object> getKeys();

    /**
     * All generated-key rows, each keyed by column label. Populated by {@link
     * JdbcClient}.
     */
    List<Map<String, Object>> getKeyList();
}
