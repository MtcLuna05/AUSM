package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import java.util.Locale;

public record ShaderBlendMode(boolean enabled, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    public static final int ZERO = 0;
    public static final int ONE = 1;
    public static final int SRC_COLOR = 0x0300;
    public static final int ONE_MINUS_SRC_COLOR = 0x0301;
    public static final int SRC_ALPHA = 0x0302;
    public static final int ONE_MINUS_SRC_ALPHA = 0x0303;
    public static final int DST_ALPHA = 0x0304;
    public static final int ONE_MINUS_DST_ALPHA = 0x0305;
    public static final int DST_COLOR = 0x0306;
    public static final int ONE_MINUS_DST_COLOR = 0x0307;
    public static final int SRC_ALPHA_SATURATE = 0x0308;

    public static final ShaderBlendMode OFF = new ShaderBlendMode(false, ONE, ZERO, ONE, ZERO);

    public static ShaderBlendMode parse(String value) {
        return parse(value, ShaderOptions.empty());
    }

    public static ShaderBlendMode parse(String value, ShaderOptions options) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.trim().equalsIgnoreCase("off")) {
            return OFF;
        }

        String[] parts = value.trim().split("\\s+");
        if (parts.length != 2 && parts.length != 4) {
            return null;
        }

        int srcRgb = blendFunction(resolveOptionToken(parts[0], options));
        int dstRgb = blendFunction(resolveOptionToken(parts[1], options));
        int srcAlpha = parts.length == 4 ? blendFunction(resolveOptionToken(parts[2], options)) : srcRgb;
        int dstAlpha = parts.length == 4 ? blendFunction(resolveOptionToken(parts[3], options)) : dstRgb;
        if (srcRgb == -1 || dstRgb == -1 || srcAlpha == -1 || dstAlpha == -1) {
            return null;
        }
        return new ShaderBlendMode(true, srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    private static String resolveOptionToken(String token, ShaderOptions options) {
        ShaderOption option = options.get(token);
        return option == null ? token : option.value();
    }

    private static int blendFunction(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "ZERO", "GL_ZERO" -> ZERO;
            case "ONE", "GL_ONE" -> ONE;
            case "SRC_COLOR", "GL_SRC_COLOR" -> SRC_COLOR;
            case "ONE_MINUS_SRC_COLOR", "GL_ONE_MINUS_SRC_COLOR" -> ONE_MINUS_SRC_COLOR;
            case "DST_COLOR", "GL_DST_COLOR" -> DST_COLOR;
            case "ONE_MINUS_DST_COLOR", "GL_ONE_MINUS_DST_COLOR" -> ONE_MINUS_DST_COLOR;
            case "SRC_ALPHA", "GL_SRC_ALPHA" -> SRC_ALPHA;
            case "ONE_MINUS_SRC_ALPHA", "GL_ONE_MINUS_SRC_ALPHA" -> ONE_MINUS_SRC_ALPHA;
            case "DST_ALPHA", "GL_DST_ALPHA" -> DST_ALPHA;
            case "ONE_MINUS_DST_ALPHA", "GL_ONE_MINUS_DST_ALPHA" -> ONE_MINUS_DST_ALPHA;
            case "SRC_ALPHA_SATURATE", "GL_SRC_ALPHA_SATURATE" -> SRC_ALPHA_SATURATE;
            default -> -1;
        };
    }
}
