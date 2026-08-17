package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prevents Complementary/Euphoria water refraction from moving first-person
 * hand pixels when a stale water material tag remains underneath the hand.
 * The pack already reserves depth values at or below 0.56 for hand geometry.
 */
public final class HandWaterRefractionCompatibilityTransformStage implements ShaderTransformStage {
    static final String MARKER = "AUSM_HAND_WATER_REFRACTION_EXCLUSION";
    private static final Pattern REFRACTION_BODY = Pattern.compile(
            "(?m)^(\\s*vec2\\s+DoRefraction\\s*\\([^\\r\\n]*\\)\\s*\\{\\s*\\R)");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader()
                || parameters.pass() != RenderPass.COMPOSITE1
                || source.contains(MARKER)) {
            return source;
        }
        return transformFragment(source);
    }

    static String transformFragment(String source) {
        if (source.contains(MARKER)
                || !source.contains("texelFetch(colortex6")
                || !source.contains("!= 241")) {
            return source;
        }
        Matcher body = REFRACTION_BODY.matcher(source);
        if (!body.find()) {
            return source;
        }
        String indent = leadingWhitespace(body.group(1)) + "    ";
        String guard = indent + "// " + MARKER + "\n"
                + indent + "if (z0 <= 0.56) return texCoord.xy;\n";
        return source.substring(0, body.end()) + guard + source.substring(body.end());
    }

    private static String leadingWhitespace(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))
                && line.charAt(index) != '\n' && line.charAt(index) != '\r') {
            index++;
        }
        return line.substring(0, index);
    }
}
