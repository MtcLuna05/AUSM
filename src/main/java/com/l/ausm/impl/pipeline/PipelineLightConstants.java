package com.l.ausm.impl.pipeline;

final class PipelineLightConstants {
    static final boolean ENABLE_CPU_LIGHT_INJECTION = true;
    static final boolean ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION = false;
    static final int MAX_SYNTHETIC_LIGHT_CANDIDATES = 2048;
    static final int MAX_SYNTHETIC_LIGHT_RANGE_REFRESH_VOLUME = 4096;
    static final int MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME = 128;
    static final int MAX_CPU_LIGHT_TILE_ENTITY_SCANS_PER_FRAME = 128;
    static final int MAX_CPU_LIGHT_BLOCK_SCANS_PER_FRAME = 384;
    static final int MAX_CPU_LIGHT_BLOCK_SCAN_WIDTH = 48;
    static final int MAX_CPU_LIGHT_BLOCK_SCAN_HEIGHT = 32;
    static final int CPU_LIGHT_TILE_ENTITY_SNAPSHOT_INTERVAL_FRAMES = 20;
    static final int MAX_COLORED_LIGHT_AUDIT_LOGS = 64;

    static final int BIOME_NETHER_WASTES_ID = 100_000;
    static final int BIOME_CRIMSON_FOREST_ID = 100_001;
    static final int BIOME_WARPED_FOREST_ID = 100_002;
    static final int BIOME_BASALT_DELTAS_ID = 100_003;
    static final int BIOME_SOUL_SAND_VALLEY_ID = 100_004;
    static final int BIOME_PALE_GARDEN_ID = 100_005;

    private PipelineLightConstants() {
    }
}
