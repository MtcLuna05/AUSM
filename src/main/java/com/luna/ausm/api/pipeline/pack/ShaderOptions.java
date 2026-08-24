package com.luna.ausm.api.pipeline.pack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShaderOptions {

    private static final ShaderOptions EMPTY = new ShaderOptions(Map.of());

    private final Map<String, ShaderOption> options;

    public ShaderOptions(Map<String, ShaderOption> options) {
        this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public static ShaderOptions empty() {
        return EMPTY;
    }

    public Map<String, ShaderOption> all() {
        return options;
    }

    public boolean contains(String name) {
        return options.containsKey(name);
    }

    public ShaderOption get(String name) {
        return options.get(name);
    }

    public boolean booleanValue(String name) {
        ShaderOption option = options.get(name);
        return option != null && option.asBoolean();
    }
}
