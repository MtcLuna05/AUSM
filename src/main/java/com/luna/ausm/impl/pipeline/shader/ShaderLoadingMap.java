package com.luna.ausm.impl.pipeline.shader;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Transitional Iris-style loading map keyed by {@link ShaderKey}.
 */
public final class ShaderLoadingMap {
    private final Map<ShaderKey, ShaderProgram> programs = new EnumMap<>(ShaderKey.class);

    public void put(ShaderKey key, ShaderProgram program) {
        if (key != null && program != null) {
            programs.put(key, program);
        }
    }

    public ShaderProgram get(ShaderKey key) {
        return programs.get(key);
    }

    public void forEach(BiConsumer<ShaderKey, ShaderProgram> consumer) {
        programs.forEach(consumer);
    }
}
