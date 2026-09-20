package io.github.hantsy.jdbc.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default {@link KeyHolder} implementation backed by a mutable list.
 */
public class GeneratedKeyHolder implements KeyHolder {

    private final List<Map<String, Object>> keyList = new ArrayList<>();

    @Override
    public Object getKey() {
        if (keyList.isEmpty()) {
            return null;
        }
        Map<String, Object> first = keyList.getFirst();
        if (first.isEmpty()) {
            return null;
        }
        return first.values().iterator().next();
    }

    @Override
    public Map<String, Object> getKeys() {
        return keyList.isEmpty() ? Map.of() : keyList.getFirst();
    }

    @Override
    public List<Map<String, Object>> getKeyList() {
        return keyList;
    }
}
