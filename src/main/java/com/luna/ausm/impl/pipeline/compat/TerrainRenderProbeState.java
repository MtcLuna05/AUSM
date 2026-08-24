package com.luna.ausm.impl.pipeline.compat;

public final class TerrainRenderProbeState {
    private TerrainRenderProbeState() {
    }

    public static void setTerrainDispatchStart(int startVertex) {
        // The corresponding probe is disabled in production. Keep the call-site ABI stable
        // without allocating a boxed integer and mutating a ThreadLocal for every rendered block.
    }

    public static Integer terrainDispatchStart() {
        return null;
    }

    public static void clearTerrainDispatchStart() {
    }

    public static int nextTerrainDispatchProbe() {
        return -1;
    }

    public static int nextVertexExpandProbe(boolean usefulContext) {
        return -1;
    }

    public static int nextTerrainCompileBufferProbe() {
        return -1;
    }

    public static int nextTerrainCompileBlockProbe() {
        return -1;
    }
}
