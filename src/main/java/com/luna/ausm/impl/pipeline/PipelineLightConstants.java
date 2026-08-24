package com.luna.ausm.impl.pipeline;

final class PipelineLightConstants {
    static final boolean ENABLE_CPU_LIGHT_INJECTION = true;
    // Shadow-vertex image writes are not reliable on every driver/render backend.
    // Keep a bounded CPU candidate path active for mapped colored block emitters.
    static final boolean ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION = true;
    static final int MAX_SYNTHETIC_LIGHT_CANDIDATES = 2048;
    static final int MAX_SYNTHETIC_LIGHT_RANGE_REFRESH_VOLUME = 4096;
    static final int MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME = 128;
    static final int MAX_CPU_LIGHT_TILE_ENTITY_SCANS_PER_FRAME = 128;
    static final int MAX_CPU_LIGHT_BLOCK_SCANS_PER_FRAME = 384;
    static final int MAX_CPU_LIGHT_BLOCK_SCAN_WIDTH = 48;
    static final int MAX_CPU_LIGHT_BLOCK_SCAN_HEIGHT = 32;
    static final int CPU_LIGHT_TILE_ENTITY_SNAPSHOT_INTERVAL_FRAMES = 20;
    // Keep the first few discovery/injection decisions visible in production
    // logs so static-light regressions can be diagnosed without stopping the game.

    static final int BIOME_NETHER_WASTES_ID = 100_000;
    static final int BIOME_CRIMSON_FOREST_ID = 100_001;
    static final int BIOME_WARPED_FOREST_ID = 100_002;
    static final int BIOME_BASALT_DELTAS_ID = 100_003;
    static final int BIOME_SOUL_SAND_VALLEY_ID = 100_004;
    static final int BIOME_PALE_GARDEN_ID = 100_005;

    private PipelineLightConstants() {
    }
}
