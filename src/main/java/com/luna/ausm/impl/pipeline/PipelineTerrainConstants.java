package com.luna.ausm.impl.pipeline;

final class PipelineTerrainConstants {
    static final int FORCE_LIGHT_RECALC_MIN_RADIUS = 16;
    static final int FORCE_LIGHT_RECALC_MAX_RADIUS = 32;
    static final int WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS = 2;
    static final int WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES = 8;
    static final int WORLD_LOAD_LIGHT_REFRESH_RADIUS = 16;
    static final int WORLD_LOAD_TERRAIN_REFRESH_ATTEMPTS = 1;
    static final int WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES = 4;
    static final int WORLD_LOAD_TERRAIN_REFRESH_REPEAT_DELAY_FRAMES = 6;
    static final double CLIENT_TELEPORT_TERRAIN_REFRESH_DISTANCE_SQ = 64.0 * 64.0;
    static final int PARTICLE_DIMENSION_RECOVERY_FRAMES = 80;

    static final int MAX_PENDING_SHADER_CHUNK_REFRESHES = 2048;
    static final int MAX_PENDING_CLIENT_CHUNK_RENDER_REFRESHES = 1024;
    static final int MAX_CLIENT_CHUNK_RENDER_REFRESHES_PER_FRAME = 8;
    static final int MAX_CLIENT_CHUNK_RENDER_REFRESH_SECTIONS_PER_FRAME = 32;
    static final int CLIENT_CHUNK_RENDER_REFRESH_RECENT_TTL_FRAMES = 12;
    static final int CLIENT_CHUNK_RENDER_REFRESH_RECENT_PRUNE_INTERVAL_FRAMES = 4;
    static final int MAX_RECENT_CLIENT_CHUNK_RENDER_REFRESHES_PER_WORLD = 2048;
    static final int MAX_STALE_CLIENT_CHUNK_REFRESHES_AGED_PER_FRAME = 32;
    static final int CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS = 8;
    static final int CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES = 1;
    static final int CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES = 1;
    static final int MAX_SHADERLESS_BLOOM_LOCAL_CHUNK_REFRESHES_PER_UPDATE = 16;
    static final String CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE = "block-update";
    static final String CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM = "shaderless-bloom";

    static final int BETTER_PORTALS_VANILLA_RENDER_DISTANCE_CAP = 4;
    static final int MAX_CHUNK_FADE_STATES = 8192;
    static final int CHUNK_FADE_STALE_FRAMES = 600;
    static final int CHUNK_FADE_WARMUP_FRAMES = 20;
    static final float CHUNK_FADE_DURATION_SECONDS = 0.45f;
    static final int MAX_SHADER_CHUNK_REFRESHES_PER_FRAME = 8;
    static final int COMPILED_PIPELINE_CACHE_LIMIT = 4;

    // Keep the provider set small enough to refresh at normal walking speed.
    // Reusing a one-block-old map made the shadow visibly detach from the
    // player, so responsiveness takes precedence over a wide cached set.
    static final int SPARSE_SHADOW_MIN_TERRAIN_DRAWS = 40;
    static final int SPARSE_SHADOW_MIN_NON_CLEAR_SAMPLES = 1;
    static final int SPARSE_SHADOW_STABLE_FRAMES = 2;
    // Provider-backed shadows are expensive enough that rebuilding them on
    // every sub-centimetre camera movement dominates shadered frame time.
    // The shadow projection is already stabilised on a much coarser texel
    // grid, so reuse it briefly across small movement instead.
    // Reuse only within the same game tick. Longer reuse makes the sun-space
    // rotation advance in visible steps even when camera-delta rebasing keeps
    // the cached map spatially aligned.
    static final int SHADOW_STABLE_UPDATE_INTERVAL_TICKS = 1;
    static final double SHADOW_STABLE_UPDATE_MOVEMENT_SQ = 0.0625D;
    // At interactive frame rates, refresh nearby detailed casters every
    // rendered frame instead of quantizing them to Minecraft's 20 Hz tick.
    // If the client is already below 30 FPS, retain the one-tick reuse gate so
    // shadow work cannot deepen an existing frame-time spiral.
    static final float SHADOW_REALTIME_MAX_FRAME_SECONDS = 1.0F / 30.0F;
    static final double SHADOW_REALTIME_CUTOUT_DISTANCE = 96.0D;
    static final double SHADOW_REALTIME_TRANSLUCENT_DISTANCE = 64.0D;
    static final double SHADOW_REALTIME_ENTITY_DISTANCE = 96.0D;
    static final double SHADOW_REALTIME_BLOCK_ENTITY_DISTANCE = 64.0D;
    static final float SHADOW_UPWARD_CAMERA_DELTA_SUPPRESSION = 0.003F;
    static final int NOTHIRIUM_SHADOW_SUPPRESS_AFTER_INVALID_FRAMES = 1;
    static final int NOTHIRIUM_SHADOW_SUPPRESS_FRAMES = 160;

    static final int HARDWARE_TERRAIN_FALLBACK_ZERO_FRAMES = 5;
    static final int HARDWARE_TERRAIN_FALLBACK_SPARSE_FRAMES = 3;
    static final int HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS = 96;
    static final int NOTHIRIUM_PROVIDER_SUPPLEMENT_SPARSE_OPAQUE_DRAWS = 48;
    static final int NOTHIRIUM_PROVIDER_SUPPLEMENT_SPARSE_TRANSLUCENT_DRAWS = 8;
    static final boolean ENABLE_NOTHIRIUM_PROVIDER_SUPPLEMENT = true;
    static final int HARDWARE_TERRAIN_FALLBACK_REFRESH_COOLDOWN_FRAMES = 12;
    static final boolean ENABLE_SAFE_TERRAIN_FALLBACKS = false;
    static final int NOTHIRIUM_NON_SOLID_REPAIR_COOLDOWN_FRAMES = 8;
    static final int NOTHIRIUM_SPARSE_MAIN_REPAIR_COOLDOWN_FRAMES = 8;
    static final int NOTHIRIUM_NON_SOLID_PROVIDER_DRAW_FRAMES = 16;
    static final int NOTHIRIUM_SPARSE_MAIN_PROVIDER_DRAW_FRAMES = 120;
    static final int NOTHIRIUM_SPARSE_MAIN_PROVIDER_SOLID_MAX_CHUNKS = 128;
    static final int NOTHIRIUM_SPARSE_MAIN_PROVIDER_CUTOUT_MAX_CHUNKS = 96;
    static final double NOTHIRIUM_SPARSE_MAIN_PROVIDER_SOLID_DISTANCE = 160.0D;
    static final double NOTHIRIUM_SPARSE_MAIN_PROVIDER_CUTOUT_DISTANCE = 128.0D;
    static final int NOTHIRIUM_HYBRID_VANILLA_MAINTENANCE_FRAMES = 240;
    static final int NOTHIRIUM_MAIN_VANILLA_DRAW_PATH_FRAMES = 240;

    static final boolean ENABLE_CHUNK_FADE = false;
    static final long WORLD_TERRAIN_TRANSITION_DEBOUNCE_MS = 750L;
    static final long BETTER_PORTALS_PORTAL_BLOCK_REFRESH_DEBOUNCE_MS = 1000L;

    private PipelineTerrainConstants() {
    }
}
