package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;

/**
 * Complementary's high detail profile enables a manual ACT corner leak filter
 * that relies on voxel-neighbor texelFetch behavior matching Iris closely.
 * On AUSM this path produces flickering black/color checker patches, while the
 * normal filtered 3D texture path is stable.
 */
public final class ComplementaryActDetailCompatibilityTransformStage implements ShaderTransformStage {
    private static final String ACT_CORNER_LEAK_FIX_DEFINE = "#define ACT_CORNER_LEAK_FIX";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!source.contains(ACT_CORNER_LEAK_FIX_DEFINE)
                || !source.contains("Manual light filtering - Optimized by Gemini")
                || !source.contains("GetComplexLightVolume")) {
            return source;
        }

        String transformed = source.replace(
                ACT_CORNER_LEAK_FIX_DEFINE,
                "// AUSM disabled Complementary ACT manual corner leak filter: " + ACT_CORNER_LEAK_FIX_DEFINE
        );
        MainMod.LOGGER.debug("[ShaderTransform] Disabled Complementary ACT corner leak filter");
        return transformed;
    }
}
