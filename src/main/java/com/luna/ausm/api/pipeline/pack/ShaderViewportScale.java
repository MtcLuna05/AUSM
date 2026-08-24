package com.luna.ausm.api.pipeline.pack;

public record ShaderViewportScale(float scale, float offsetX, float offsetY) {
    public static final ShaderViewportScale DEFAULT = new ShaderViewportScale(1.0f, 0.0f, 0.0f);

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
}
