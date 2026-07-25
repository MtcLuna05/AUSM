package com.l.ausm.impl.pipeline.compat;

public final class TerrainRenderProbeState {
    private static final ThreadLocal<Integer> TERRAIN_DISPATCH_START = new ThreadLocal<>();

    private TerrainRenderProbeState() {
    }

    public static void setTerrainDispatchStart(int startVertex) {
        TERRAIN_DISPATCH_START.set(startVertex);
    }

    public static Integer terrainDispatchStart() {
        return TERRAIN_DISPATCH_START.get();
    }

    public static void clearTerrainDispatchStart() {
        TERRAIN_DISPATCH_START.remove();
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
