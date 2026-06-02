package com.l.ausm.api.pipeline.shader;

/**
 * High-level OptiFine shader program stage.
 */
public enum ProgramStage {
    NONE,
    PREPARE,
    GBUFFERS,
    SHADOW,
    DEFERRED,
    COMPOSITE,
    FINAL;

    public boolean readsDeferredTextures() {
        return this == DEFERRED || this == COMPOSITE || this == FINAL;
    }

    public boolean isFullscreenStage() {
        return this == PREPARE || this == DEFERRED || this == COMPOSITE || this == FINAL;
    }
}
