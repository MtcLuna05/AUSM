package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import java.util.Locale;

public record ShaderAlphaTest(int function, float reference) {
    public static final int NEVER = 0x0200;
    public static final int LESS = 0x0201;
    public static final int EQUAL = 0x0202;
    public static final int LEQUAL = 0x0203;
    public static final int GREATER = 0x0204;
    public static final int NOTEQUAL = 0x0205;
    public static final int GEQUAL = 0x0206;
    public static final int ALWAYS_FUNCTION = 0x0207;

    public static final ShaderAlphaTest ALWAYS = new ShaderAlphaTest(ALWAYS_FUNCTION, 0.0f);
    public static final ShaderAlphaTest NON_ZERO_ALPHA = new ShaderAlphaTest(GREATER, 0.0001F);
    public static final ShaderAlphaTest ONE_TENTH_ALPHA = new ShaderAlphaTest(GREATER, 0.1F);
    public static final ShaderAlphaTest HALF_ALPHA = new ShaderAlphaTest(GREATER, 0.5F);
    public static final ShaderAlphaTest VERTEX_ALPHA = new ShaderAlphaTest(NEVER, Float.MAX_VALUE);

    public static ShaderAlphaTest parse(String value) {
        return parse(value, ShaderOptions.empty());
    }

    public static ShaderAlphaTest parse(String value, ShaderOptions options) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("off") || trimmed.equalsIgnoreCase("false")) {
            return ALWAYS;
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) {
            return null;
        }

        String functionName = resolveOptionToken(parts[0], options);
        int function = switch (functionName.toUpperCase(Locale.ROOT)) {
            case "NEVER", "GL_NEVER" -> NEVER;
            case "LESS", "GL_LESS" -> LESS;
            case "EQUAL", "GL_EQUAL" -> EQUAL;
            case "LEQUAL", "GL_LEQUAL" -> LEQUAL;
            case "GREATER", "GL_GREATER" -> GREATER;
            case "NOTEQUAL", "GL_NOTEQUAL" -> NOTEQUAL;
            case "GEQUAL", "GL_GEQUAL" -> GEQUAL;
            case "ALWAYS", "GL_ALWAYS" -> ALWAYS_FUNCTION;
            default -> -1;
        };
        if (function == -1) {
            return null;
        }

        try {
            return new ShaderAlphaTest(function, Float.parseFloat(resolveOptionToken(parts[1], options)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String resolveOptionToken(String token, ShaderOptions options) {
        ShaderOption option = options.get(token);
        return option == null ? token : option.value();
    }
}
