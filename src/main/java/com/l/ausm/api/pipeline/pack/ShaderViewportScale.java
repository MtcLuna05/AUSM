package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

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
