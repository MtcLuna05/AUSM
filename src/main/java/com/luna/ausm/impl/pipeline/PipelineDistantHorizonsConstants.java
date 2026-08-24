package com.luna.ausm.impl.pipeline;

final class PipelineDistantHorizonsConstants {
    static final boolean FORCE_DISTANT_HORIZONS_FALLBACK_PROGRAM = false;
    static final boolean ENABLE_DISTANT_HORIZONS_DIRECT_SHADER_RENDER = false;
    static final boolean ENABLE_DIRECT_DISTANT_HORIZONS_SHADER_MRT = Boolean.getBoolean("ausm.dhDirectShaderMrt");

    private PipelineDistantHorizonsConstants() {
    }
}
