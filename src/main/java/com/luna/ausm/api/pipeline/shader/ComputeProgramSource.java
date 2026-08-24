package com.luna.ausm.api.pipeline.shader;

/**
 * Parsed identity for an Iris-style compute shader source.
 */
public record ComputeProgramSource(
        String name,
        int arrayIndex,
        String path,
        String source,
        int[] workGroups,
        float[] workGroupRelative,
        ShaderIndirectPointer indirectPointer
) {
    public boolean hasFixedWorkGroups() {
        return workGroups != null;
    }

    public boolean hasRelativeWorkGroups() {
        return workGroupRelative != null;
    }

    public boolean hasIndirectPointer() {
        return indirectPointer != null;
    }
}
