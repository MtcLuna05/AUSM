package com.luna.ausm.api.pipeline.shader;

public enum ProgramGroup {
    SHADOW("shadow"),
    GBUFFERS("gbuffers"),
    DH("dh"),
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
