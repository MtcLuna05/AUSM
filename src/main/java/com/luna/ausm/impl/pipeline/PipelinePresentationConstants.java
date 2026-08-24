package com.luna.ausm.impl.pipeline;

final class PipelinePresentationConstants {
    static final boolean ENABLE_COMPOSITE_INVALID_PRESENTATION_RECOVERY = false;
    static final String COMPOSITE_INVALID_FALLBACK_SOURCE = "PRIVATE_COLOR";
    static final int COMPOSITE_INVALID_FALLBACK_HOLD_FRAMES = 12;
    static final int COMPOSITE_INVALID_FALLBACK_MAX_SNAPSHOT_AGE_FRAMES = 12;
    static final float COMPOSITE_RECOVERY_COLOR_MIN_MAX_CHANNEL = 0.12F;
    static final float COMPOSITE_RECOVERY_COLOR_MIN_LUMA = 0.04F;
    static final boolean ENABLE_SPARSE_STARTUP_PRESENTATION_HOLD = false;
    static final boolean ENABLE_FLAT_COMPOSITE_SKY_ONLY_FINISH = false;
    static final int SPARSE_STARTUP_PRESENTATION_HOLD_FRAMES = 24;
    static final int SPARSE_STARTUP_PRESENTATION_MIN_TERRAIN_DRAWS = 24;
    static final boolean ENABLE_SYNCHRONOUS_CENTER_DEPTH_READBACK = false;

    private PipelinePresentationConstants() {
    }
}
