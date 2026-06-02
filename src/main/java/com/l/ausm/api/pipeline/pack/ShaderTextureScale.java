package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

public record ShaderTextureScale(String widthValue, String heightValue) {
    public int width(int baseWidth) {
        return dimension(widthValue, baseWidth);
    }

    public int height(int baseHeight) {
        return dimension(heightValue, baseHeight);
    }

    private static int dimension(String value, int base) {
        try {
            if (value.contains(".")) {
                return Math.max(1, (int) (base * Float.parseFloat(value)));
            }
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return Math.max(1, base);
        }
    }
}
