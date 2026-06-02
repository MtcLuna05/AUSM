package com.l.ausm.api.pipeline.shader;

public enum ProgramGroup {
    SHADOW("shadow"),
    GBUFFERS("gbuffers"),
    PREPARE("prepare"),
    DEFERRED("deferred"),
    COMPOSITE("composite"),
    FINAL("final");

    private final String baseName;

    ProgramGroup(String baseName) {
        this.baseName = baseName;
    }

    public String baseName() {
        return baseName;
    }
}
