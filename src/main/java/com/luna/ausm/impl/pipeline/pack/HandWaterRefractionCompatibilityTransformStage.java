package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;
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
        if (parameters.pass() == RenderPass.GBUFFERS_WATER
                && !source.contains("AUSM_CONTINUOUS_WATER_WAVES")) {
            return source.replace(
                    "    #if defined NO_WAVING_INDOORS && !defined WAVE_EVERYTHING\n"
                            + "        wave *= clamp(lmCoord.y - 0.87, 0.0, 0.1);\n"
                            + "    #else\n"
                            + "        wave *= 0.1;\n"
                            + "    #endif\n"
                            + "\n"
                            + "    wave = wave * 0.125 - 0.05;",
                    "    // AUSM_CONTINUOUS_WATER_WAVES: skylight is not a stable\n"
                            + "    // enclosure signal for water vertices, so do not split\n"
                            + "    // the surface where local light changes.\n"
                            + "    wave *= 0.1;\n"
                            + "\n"
                            + "    wave = wave * 0.125 - 0.05;");
        }
        if (!parameters.fragmentShader()) {
            return source;
        }
        if (parameters.pass() != RenderPass.COMPOSITE1 || source.contains(MARKER)) {
            return source;
        }
        return transformFragment(source);
    }

    static String transformFragment(String source) {
        String transformed = source;
        if (!transformed.contains(MARKER)
                && transformed.contains("texelFetch(colortex6")
                && transformed.contains("!= 241")) {
            Matcher body = REFRACTION_BODY.matcher(transformed);
            if (body.find()) {
                String indent = leadingWhitespace(body.group(1)) + "    ";
                String guard = indent + "// " + MARKER + "\n"
                        + indent + "if (z0 <= 0.56) return texCoord.xy;\n";
                transformed = transformed.substring(0, body.end()) + guard + transformed.substring(body.end());
            }
        }
        if (!transformed.contains("AUSM_HAND_LIGHT_SHAFT_EXCLUSION") && transformed.contains("GetVolumetricLight")) {
            Matcher call = Pattern.compile("(?m)^(\\s*)(volumetricEffect\\s*=\\s*GetVolumetricLight\\([^\\r\\n]+\\);)\\s*$")
                    .matcher(transformed);
            if (call.find()) {
                String indent = call.group(1);
                String replacement = indent + "// AUSM_HAND_LIGHT_SHAFT_EXCLUSION\n"
                        + indent + "if (z0 > 0.56) {\n"
                        + indent + "    " + call.group(2) + "\n"
                        + indent + "}";
                transformed = call.replaceFirst(Matcher.quoteReplacement(replacement));
            }
        }
        return transformed;
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
