package com.luna.ausm.api.pipeline.shader;

/**
 * Iris-style indexed program families.
 */
public enum ProgramArrayId {
    SETUP("setup", 100),
    BEGIN("begin", 100),
    PREPARE("prepare", 100),
    DEFERRED("deferred", 100),
    COMPOSITE("composite", 100),
    SHADOWCOMP("shadowcomp", 100);

    private final String sourcePrefix;
    private final int programCount;

    ProgramArrayId(String sourcePrefix, int programCount) {
        this.sourcePrefix = sourcePrefix;
        this.programCount = programCount;
    }

    public String sourcePrefix() {
        return sourcePrefix;
    }

    public int programCount() {
        return programCount;
    }
}
