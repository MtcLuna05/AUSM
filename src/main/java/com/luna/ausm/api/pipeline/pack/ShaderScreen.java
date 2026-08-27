package com.luna.ausm.api.pipeline.pack;

import java.util.List;
import java.util.Objects;

public final class ShaderScreen {
    private final String id;
    private final List<ShaderScreenEntry> entries;

    public ShaderScreen(String id, List<ShaderScreenEntry> entries) {
        this.id = id;
        this.entries = entries;
    }

    public String id() { return id; }
    public List<ShaderScreenEntry> entries() { return entries; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ShaderScreen)) return false;
        ShaderScreen screen = (ShaderScreen) other;
        return Objects.equals(id, screen.id) && Objects.equals(entries, screen.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, entries);
    }

    @Override
    public String toString() {
        return "ShaderScreen[id=" + id + ", entries=" + entries + ']';
    }
}
