package com.luna.ausm.api.pipeline.pack;

public final class ShaderViewportScale {
    public static final ShaderViewportScale DEFAULT = new ShaderViewportScale(1.0f, 0.0f, 0.0f);

    private final float scale;
    private final float offsetX;
    private final float offsetY;

    public ShaderViewportScale(float scale, float offsetX, float offsetY) {
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public float scale() {
        return scale;
    }

    public float offsetX() {
        return offsetX;
    }

    public float offsetY() {
        return offsetY;
    }

    public int x(int width) {
        return Math.round(width * offsetX);
    }

    public int y(int height) {
        return Math.round(height * offsetY);
    }

    public int width(int width) {
        return Math.max(1, Math.round(width * scale));
    }

    public int height(int height) {
        return Math.max(1, Math.round(height * scale));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ShaderViewportScale)) {
            return false;
        }
        ShaderViewportScale scale = (ShaderViewportScale) other;
        return Float.compare(this.scale, scale.scale) == 0
                && Float.compare(offsetX, scale.offsetX) == 0
                && Float.compare(offsetY, scale.offsetY) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(scale);
        result = 31 * result + Float.hashCode(offsetX);
        return 31 * result + Float.hashCode(offsetY);
    }

    @Override
    public String toString() {
        return "ShaderViewportScale[scale=" + scale + ", offsetX=" + offsetX + ", offsetY=" + offsetY + ']';
    }
}
