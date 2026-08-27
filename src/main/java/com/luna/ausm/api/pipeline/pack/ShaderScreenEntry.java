package com.luna.ausm.api.pipeline.pack;

import java.util.Objects;

public final class ShaderScreenEntry {
    private final Type type;
    private final String name;

    public ShaderScreenEntry(Type type, String name) {
        this.type = type;
        this.name = name;
    }

    public Type type() { return type; }
    public String name() { return name; }

    public enum Type {
        OPTION,
        SCREEN,
        PROFILE,
        EMPTY
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ShaderScreenEntry)) return false;
        ShaderScreenEntry entry = (ShaderScreenEntry) other;
        return type == entry.type && Objects.equals(name, entry.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String toString() {
        return "ShaderScreenEntry[type=" + type + ", name=" + name + ']';
    }
}
