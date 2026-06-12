package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

/**
 * Complementary's 1.12 water branch assumes the old packed water texture path.
 * AUSM still supplies biome water tint through vertex color, so the modern
 * branch matches the data we actually provide and avoids white water surfaces.
 */
public final class ComplementaryWaterVersionTransformStage implements ShaderTransformStage {
    private static final String MC_VERSION_11202 = "#define MC_VERSION 11202";
    private static final String WATER_BRANCH_VERSION = "#define MC_VERSION 11300";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() != RenderPass.GBUFFERS_WATER || !parameters.fragmentShader()) {
            return source;
        }
        if (!source.contains("Complementary Shaders") || !source.contains("WATERCOLOR_MODE")) {
            return source;
        }
        return source.replace(MC_VERSION_11202, WATER_BRANCH_VERSION);
    }
}
