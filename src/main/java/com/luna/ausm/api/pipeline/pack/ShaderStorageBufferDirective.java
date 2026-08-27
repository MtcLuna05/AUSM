package com.luna.ausm.api.pipeline.pack;

import java.util.Objects;

public final class ShaderStorageBufferDirective {
    private final int index;
    private final long size;
    private final boolean relative;
    private final float scaleX;
    private final float scaleY;
    private final String name;

    public ShaderStorageBufferDirective(int index, long size, boolean relative, float scaleX, float scaleY, String name) {
        this.index = index;
        this.size = size;
        this.relative = relative;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.name = name;
    }

    public int index() { return index; }
    public long size() { return size; }
    public boolean relative() { return relative; }
    public float scaleX() { return scaleX; }
    public float scaleY() { return scaleY; }
    public String name() { return name; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ShaderStorageBufferDirective)) return false;
        ShaderStorageBufferDirective directive = (ShaderStorageBufferDirective) other;
        return index == directive.index && size == directive.size && relative == directive.relative
                && Float.compare(scaleX, directive.scaleX) == 0 && Float.compare(scaleY, directive.scaleY) == 0
                && Objects.equals(name, directive.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, size, relative, scaleX, scaleY, name);
    }

    @Override
    public String toString() {
        return "ShaderStorageBufferDirective[index=" + index + ", size=" + size + ", relative=" + relative
                + ", scaleX=" + scaleX + ", scaleY=" + scaleY + ", name=" + name + ']';
    }
}
