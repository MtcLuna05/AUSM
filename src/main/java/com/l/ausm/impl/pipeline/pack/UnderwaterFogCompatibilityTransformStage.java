package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ProgramStage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Narrows an OptiFine-style fullscreen underwater fog expression to the depth
 * scale observed on the 1.12.2 backport.
 */
public final class UnderwaterFogCompatibilityTransformStage implements ShaderTransformStage {
    private static final float DEPTH_SCALE = 0.125F;
    private static final Pattern WATER_ABSORPTION = Pattern.compile(
            "pow\\s*\\(\\s*1\\.001\\s*-\\s*linearDepth\\s*,\\s*5\\.0\\s*\\+\\s*\\(\\s*4\\.0\\s*\\*\\s*WATER_ABSORPTION\\s*\\)\\s*\\)");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() == null || !parameters.fragmentShader()) {
            return source;
        }
        if (parameters.pass().stage() != ProgramStage.COMPOSITE) {
            return source;
        }
        if (!looksLikeOptifineUnderwaterFog(source)) {
            return source;
        }

        String replacement = "pow(1.001 - linearDepth * " + DEPTH_SCALE + ", 5.0 + (4.0 * WATER_ABSORPTION))";
        return WATER_ABSORPTION.matcher(source).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static boolean looksLikeOptifineUnderwaterFog(String source) {
        return source.contains("isEyeInWater == 1")
                && source.contains("WATER_ABSORPTION")
                && source.contains("WATER_COLOR")
                && source.contains("mix(blockColor.rgb")
                && WATER_ABSORPTION.matcher(source).find();
    }
}
