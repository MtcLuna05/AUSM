package com.l.ausm.impl.pipeline.pack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

final class ShaderPropertiesCache {
    private static final int LIMIT = 24;

    private final Map<String, ShaderProperties> values = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ShaderProperties> eldest) {
            return size() > LIMIT;
        }
    };

    ShaderProperties get(String key) {
        return values.get(key);
    }

    void put(String key, ShaderProperties properties) {
        values.put(key, properties);
    }

    void clear() {
        values.clear();
    }

    void clearExcept(String packName) {
        if (packName == null || packName.isBlank()) {
            clear();
            return;
        }
        values.keySet().removeIf(key -> !keyBelongsToPack(key, packName));
    }

    static String key(String packName, Map<String, String> overrides, int dimensionId, String packFingerprint) {
        String safePackName = packName != null ? packName : "";
        StringBuilder builder = new StringBuilder(safePackName.length() + 32);
        builder.append(safePackName)
                .append('\0')
                .append(dimensionId)
                .append('\0')
                .append(packFingerprint != null ? packFingerprint : "");
        if (overrides != null && !overrides.isEmpty()) {
            new TreeMap<>(overrides).forEach((name, value) -> builder.append('\0')
                    .append(name != null ? name : "")
                    .append('=')
                    .append(value != null ? value : ""));
        }
        return builder.toString();
    }

    private static boolean keyBelongsToPack(String cacheKey, String packName) {
        String safePackName = packName != null ? packName : "";
        return cacheKey != null && cacheKey.startsWith(safePackName + '\0');
    }
}
