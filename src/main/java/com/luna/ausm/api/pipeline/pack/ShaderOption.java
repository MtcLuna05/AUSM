package com.luna.ausm.api.pipeline.pack;

import java.util.List;

public record ShaderOption(
        String name,
        String defaultValue,
        String value,
        List<String> choices,
        boolean slider,
        boolean toggle
) {
    public ShaderOption withValue(String value) {
        return new ShaderOption(name, defaultValue, value, choices, slider, toggle);
    }

    public boolean changed() {
        return !defaultValue.equals(value);
    }

    public String nextValue() {
        if (toggle()) {
            return asBoolean() ? "false" : "true";
        }

        if (choices.isEmpty()) {
            return value;
        }

        int index = choices.indexOf(value);
        if (index < 0) {
            return choices.get(0);
        }
        return choices.get((index + 1) % choices.size());
    }

    public boolean asBoolean() {
        if (value == null || value.isBlank()) {
            return true;
        }

        return switch (value.toLowerCase()) {
            case "false", "off", "0" -> false;
            default -> true;
        };
    }
}
