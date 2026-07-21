package com.l.ausm.impl.pipeline.compat;

import java.util.concurrent.atomic.AtomicInteger;

public final class TerrainRenderProbeState {
    private static final int TERRAIN_DISPATCH_PROBE_LIMIT = 96;
    private static final int VERTEX_EXPAND_PROBE_LIMIT = 96;
    private static final int TERRAIN_COMPILE_BUFFER_PROBE_LIMIT = 64;
    private static final int TERRAIN_COMPILE_BLOCK_PROBE_LIMIT = 128;

    private static final AtomicInteger TERRAIN_DISPATCH_PROBES = new AtomicInteger();
    private static final AtomicInteger VERTEX_EXPAND_PROBES = new AtomicInteger();
    private static final AtomicInteger TERRAIN_COMPILE_BUFFER_PROBES = new AtomicInteger();
    private static final AtomicInteger TERRAIN_COMPILE_BLOCK_PROBES = new AtomicInteger();
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
        int call = TERRAIN_DISPATCH_PROBES.incrementAndGet();
        return call <= TERRAIN_DISPATCH_PROBE_LIMIT ? call : -1;
    }

    public static int nextVertexExpandProbe(boolean usefulContext) {
        if (!usefulContext) {
            return -1;
        }
        int call = VERTEX_EXPAND_PROBES.incrementAndGet();
        return call <= VERTEX_EXPAND_PROBE_LIMIT ? call : -1;
    }

    public static int nextTerrainCompileBufferProbe() {
        int call = TERRAIN_COMPILE_BUFFER_PROBES.incrementAndGet();
        return call <= TERRAIN_COMPILE_BUFFER_PROBE_LIMIT ? call : -1;
    }

    public static int nextTerrainCompileBlockProbe() {
        int call = TERRAIN_COMPILE_BLOCK_PROBES.incrementAndGet();
        return call <= TERRAIN_COMPILE_BLOCK_PROBE_LIMIT ? call : -1;
    }
}
