package com.l.ausm.impl.pipeline.shader;

/**
 * Iris-style runtime lookup table keyed by {@link ShaderKey}.
 */
public final class ShaderMap {
    private final ShaderProgram[] programs = new ShaderProgram[ShaderKey.values().length];

    public ShaderMap(ShaderLoadingMap loadingMap) {
        loadingMap.forEach((key, program) -> programs[key.ordinal()] = program);
    }

    public ShaderProgram get(ShaderKey key) {
        return key == null ? null : programs[key.ordinal()];
    }
}
