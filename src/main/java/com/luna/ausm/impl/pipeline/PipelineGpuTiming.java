package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import java.util.Locale;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

/**
 * Asynchronous GPU timestamp probes for the shader pipeline.
 *
 * <p>Timestamp pairs are deliberately used instead of nested
 * {@code GL_TIME_ELAPSED} queries: shadow, G-buffer, fullscreen, and frame
 * scopes overlap. Results are consumed only after the end timestamp reports
 * available, so profiling never introduces a GPU/CPU synchronization point.</p>
 */
final class PipelineGpuTiming {
    static final int FRAME = 0;
    static final int SHADOW = 1;
    static final int SHADOW_TERRAIN = 2;
    static final int SHADOW_ENTITIES = 3;
    static final int SHADOW_BLOCK_ENTITIES = 4;
    static final int SHADOW_POST = 5;
    static final int GBUFFER_TERRAIN = 6;
    static final int GBUFFER_SKY = 7;
    static final int GBUFFER_ENTITIES = 8;
    static final int GBUFFER_BLOCK_ENTITIES = 9;
    static final int GBUFFER_PARTICLES_WEATHER = 10;
    static final int GBUFFER_HAND = 11;
    static final int GBUFFER_OTHER = 12;
    static final int PREPARE = 13;
    static final int DEFERRED = 14;
    static final int COMPOSITE = 15;
    static final int FINAL = 16;
    static final int BLOOM = 17;

    private static final String[] SCOPE_NAMES = {
            "frame", "shadow", "shadowTerrain", "shadowEntities", "shadowBlockEntities", "shadowPost",
            "gbufferTerrain", "gbufferSky", "gbufferEntities", "gbufferBlockEntities",
            "gbufferParticlesWeather", "gbufferHand", "gbufferOther", "prepare", "deferred", "composite",
            "final", "bloom"
    };
    private static final int SCOPE_COUNT = SCOPE_NAMES.length;
    private static final int SLOT_COUNT = 256;
    private static final int PASS_STACK_CAPACITY = 128;
    private static final long SUMMARY_FRAMES = 90L;
    private static final int INVALID_TOKEN = -1;

    private static final int[] START_QUERIES = new int[SLOT_COUNT];
    private static final int[] END_QUERIES = new int[SLOT_COUNT];
    private static final int[] SLOT_SCOPES = new int[SLOT_COUNT];
    private static final boolean[] SLOT_IN_USE = new boolean[SLOT_COUNT];
    private static final int[] FREE_SLOTS = new int[SLOT_COUNT];
    private static final int[] PENDING_SLOTS = new int[SLOT_COUNT];
    private static final int[] PASS_TOKENS = new int[PASS_STACK_CAPACITY];
    private static final long[] TOTAL_NANOS = new long[SCOPE_COUNT];
    private static final long[] MAX_NANOS = new long[SCOPE_COUNT];
    private static final long[] SAMPLE_COUNTS = new long[SCOPE_COUNT];

    private static boolean initialized;
    private static boolean disabled;
    private static int freeCount;
    private static int pendingHead;
    private static int pendingTail;
    private static int pendingCount;
    private static int passDepth;
    private static int frameToken = INVALID_TOKEN;
    private static int width;
    private static int height;
    private static long skippedSamples;
    private static String lastSummary = "not-ready";

    private PipelineGpuTiming() {
    }

    static void beginFrame(long frameId, int frameWidth, int frameHeight) {
        if (!ensureInitialized()) {
            return;
        }
        collectReadySamples();
        if (frameToken != INVALID_TOKEN) {
            end(frameToken);
        }
        resetPassScopes();
        width = frameWidth;
        height = frameHeight;
        TerrainShaderGpuProbe.beginFrame();
        frameToken = begin(FRAME);
    }

    static void finishFrame() {
        resetPassScopes();
        if (frameToken != INVALID_TOKEN) {
            end(frameToken);
            frameToken = INVALID_TOKEN;
        }
    }

    static int beginShadow() {
        return begin(SHADOW);
    }

    static int beginBloom() {
        return begin(BLOOM);
    }

    static int beginProgram(RenderPass pass) {
        return begin(scopeForProgram(pass));
    }

    static int beginProgramArray(ProgramArrayId arrayId) {
        return begin(scopeForProgramArray(arrayId));
    }

    static void beginPass(RenderPass pass, WorldRenderingPhase phase, boolean shadow) {
        int scope = scopeForPass(pass, phase, shadow);
        int token = begin(scope);
        if (passDepth >= PASS_TOKENS.length) {
            end(token);
            skippedSamples++;
            return;
        }
        PASS_TOKENS[passDepth++] = token;
    }

    static void endPass() {
        if (passDepth <= 0) {
            return;
        }
        end(PASS_TOKENS[--passDepth]);
    }

    static void resetPassScopes() {
        while (passDepth > 0) {
            end(PASS_TOKENS[--passDepth]);
        }
    }

    static int begin(int scope) {
        if (!ensureInitialized() || scope < 0 || scope >= SCOPE_COUNT) {
            return INVALID_TOKEN;
        }
        if (freeCount <= 0) {
            collectReadySamples();
        }
        if (freeCount <= 0) {
            skippedSamples++;
            return INVALID_TOKEN;
        }

        int slot = FREE_SLOTS[--freeCount];
        try {
            ensureQueryObjects(slot);
            SLOT_SCOPES[slot] = scope;
            SLOT_IN_USE[slot] = true;
            GL33.glQueryCounter(START_QUERIES[slot], GL33.GL_TIMESTAMP);
            return slot;
        } catch (RuntimeException | LinkageError error) {
            SLOT_IN_USE[slot] = false;
            FREE_SLOTS[freeCount++] = slot;
            disable("begin", error);
            return INVALID_TOKEN;
        }
    }

    static void end(int token) {
        if (!initialized || disabled || token < 0 || token >= SLOT_COUNT || !SLOT_IN_USE[token]) {
            return;
        }
        try {
            GL33.glQueryCounter(END_QUERIES[token], GL33.GL_TIMESTAMP);
            PENDING_SLOTS[pendingTail] = token;
            pendingTail = (pendingTail + 1) % SLOT_COUNT;
            pendingCount++;
        } catch (RuntimeException | LinkageError error) {
            SLOT_IN_USE[token] = false;
            FREE_SLOTS[freeCount++] = token;
            disable("end", error);
        }
    }

    static void release() {
        if (!initialized) {
            return;
        }
        try {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                if (START_QUERIES[slot] != 0) {
                    GL15.glDeleteQueries(START_QUERIES[slot]);
                    START_QUERIES[slot] = 0;
                }
                if (END_QUERIES[slot] != 0) {
                    GL15.glDeleteQueries(END_QUERIES[slot]);
                    END_QUERIES[slot] = 0;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        initialized = false;
        disabled = false;
        pendingHead = 0;
        pendingTail = 0;
        pendingCount = 0;
        passDepth = 0;
        frameToken = INVALID_TOKEN;
        clearStatistics();
        TerrainShaderGpuProbe.release();
    }

    static int scopeForPass(RenderPass pass, WorldRenderingPhase phase, boolean shadow) {
        if (shadow) {
            if (phase == WorldRenderingPhase.TERRAIN_SOLID
                    || phase == WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED
                    || phase == WorldRenderingPhase.TERRAIN_CUTOUT
                    || phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT
                    || phase == WorldRenderingPhase.TRIPWIRE) {
                return SHADOW_TERRAIN;
            }
            if (phase == WorldRenderingPhase.BLOCK_ENTITIES
                    || phase == WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT) {
                return SHADOW_BLOCK_ENTITIES;
            }
            if (phase == WorldRenderingPhase.ENTITIES
                    || phase == WorldRenderingPhase.ENTITIES_TRANSLUCENT
                    || phase == WorldRenderingPhase.ITEM
                    || phase == WorldRenderingPhase.HAND_SOLID
                    || phase == WorldRenderingPhase.HAND_TRANSLUCENT
                    || phase == WorldRenderingPhase.BEACON_BEAM
                    || phase == WorldRenderingPhase.ARMOR_GLINT
                    || phase == WorldRenderingPhase.SPIDER_EYES
                    || phase == WorldRenderingPhase.LIGHTNING) {
                return SHADOW_ENTITIES;
            }
            return SHADOW_POST;
        }

        if (phase == WorldRenderingPhase.TERRAIN_SOLID
                || phase == WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED
                || phase == WorldRenderingPhase.TERRAIN_CUTOUT
                || phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT
                || phase == WorldRenderingPhase.TRIPWIRE
                || phase == WorldRenderingPhase.DESTROY) {
            return GBUFFER_TERRAIN;
        }
        if (phase == WorldRenderingPhase.SKY
                || phase == WorldRenderingPhase.SUNSET
                || phase == WorldRenderingPhase.CUSTOM_SKY
                || phase == WorldRenderingPhase.SUN
                || phase == WorldRenderingPhase.MOON
                || phase == WorldRenderingPhase.STARS
                || phase == WorldRenderingPhase.VOID
                || phase == WorldRenderingPhase.SKY_TEXTURED
                || phase == WorldRenderingPhase.ASTRAL_STARS
                || phase == WorldRenderingPhase.ASTRAL_SOLAR_ECLIPSE
                || phase == WorldRenderingPhase.SKY_GROUND) {
            return GBUFFER_SKY;
        }
        if (phase == WorldRenderingPhase.BLOCK_ENTITIES
                || phase == WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT) {
            return GBUFFER_BLOCK_ENTITIES;
        }
        if (phase == WorldRenderingPhase.ENTITIES
                || phase == WorldRenderingPhase.ENTITIES_TRANSLUCENT
                || phase == WorldRenderingPhase.ITEM
                || phase == WorldRenderingPhase.BEACON_BEAM
                || phase == WorldRenderingPhase.ARMOR_GLINT
                || phase == WorldRenderingPhase.SPIDER_EYES) {
            return GBUFFER_ENTITIES;
        }
        if (phase == WorldRenderingPhase.HAND_SOLID || phase == WorldRenderingPhase.HAND_TRANSLUCENT) {
            return GBUFFER_HAND;
        }
        if (phase == WorldRenderingPhase.PARTICLES
                || phase == WorldRenderingPhase.PARTICLES_TRANSLUCENT
                || phase == WorldRenderingPhase.CLOUDS
                || phase == WorldRenderingPhase.RAIN_SNOW
                || phase == WorldRenderingPhase.LIGHTNING) {
            return GBUFFER_PARTICLES_WEATHER;
        }
        return scopeForProgram(pass);
    }

    static int scopeForProgram(RenderPass pass) {
        if (pass == null) {
            return GBUFFER_OTHER;
        }
        ProgramStage stage = pass.stage();
        if (stage == ProgramStage.PREPARE) {
            return PREPARE;
        }
        if (stage == ProgramStage.SHADOW) {
            return SHADOW_POST;
        }
        if (stage == ProgramStage.DEFERRED) {
            return DEFERRED;
        }
        if (stage == ProgramStage.COMPOSITE) {
            return COMPOSITE;
        }
        if (stage == ProgramStage.FINAL) {
            return FINAL;
        }
        return GBUFFER_OTHER;
    }

    static int scopeForProgramArray(ProgramArrayId arrayId) {
        if (arrayId == ProgramArrayId.PREPARE || arrayId == ProgramArrayId.SETUP || arrayId == ProgramArrayId.BEGIN) {
            return PREPARE;
        }
        if (arrayId == ProgramArrayId.DEFERRED) {
            return DEFERRED;
        }
        if (arrayId == ProgramArrayId.COMPOSITE) {
            return COMPOSITE;
        }
        if (arrayId == ProgramArrayId.SHADOWCOMP) {
            return SHADOW_POST;
        }
        return GBUFFER_OTHER;
    }

    static String scopeName(int scope) {
        return scope >= 0 && scope < SCOPE_NAMES.length ? SCOPE_NAMES[scope] : "unknown";
    }

    static String lastSummary() {
        return lastSummary;
    }

    private static boolean ensureInitialized() {
        if (disabled) {
            return false;
        }
        if (initialized) {
            return true;
        }
        try {
            if (!GLContext.getCapabilities().OpenGL33) {
                disabled = true;
                MainMod.LOGGER.info("[AUSMGpuTiming] active=false reason=OpenGL33-unavailable");
                return false;
            }
            freeCount = SLOT_COUNT;
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                FREE_SLOTS[slot] = SLOT_COUNT - slot - 1;
            }
            initialized = true;
            MainMod.LOGGER.info("[AUSMGpuTiming] active=true mode=asynchronous-timestamps slots={}", SLOT_COUNT);
            return true;
        } catch (RuntimeException | LinkageError error) {
            disable("initialize", error);
            return false;
        }
    }

    private static void ensureQueryObjects(int slot) {
        if (START_QUERIES[slot] == 0) {
            START_QUERIES[slot] = GL15.glGenQueries();
        }
        if (END_QUERIES[slot] == 0) {
            END_QUERIES[slot] = GL15.glGenQueries();
        }
    }

    private static void collectReadySamples() {
        if (!initialized || disabled) {
            return;
        }
        try {
            while (pendingCount > 0) {
                int slot = PENDING_SLOTS[pendingHead];
                if (GL15.glGetQueryObjecti(END_QUERIES[slot], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                    break;
                }
                pendingHead = (pendingHead + 1) % SLOT_COUNT;
                pendingCount--;

                long start = GL33.glGetQueryObjectui64(START_QUERIES[slot], GL15.GL_QUERY_RESULT);
                long end = GL33.glGetQueryObjectui64(END_QUERIES[slot], GL15.GL_QUERY_RESULT);
                long elapsed = Math.max(0L, end - start);
                int scope = SLOT_SCOPES[slot];
                TOTAL_NANOS[scope] += elapsed;
                MAX_NANOS[scope] = Math.max(MAX_NANOS[scope], elapsed);
                SAMPLE_COUNTS[scope]++;

                SLOT_IN_USE[slot] = false;
                FREE_SLOTS[freeCount++] = slot;
            }
            if (SAMPLE_COUNTS[FRAME] >= SUMMARY_FRAMES) {
                logSummary();
            }
        } catch (RuntimeException | LinkageError error) {
            disable("collect", error);
        }
    }

    private static void logSummary() {
        long frames = SAMPLE_COUNTS[FRAME];
        if (frames <= 0L) {
            return;
        }
        StringBuilder summary = new StringBuilder(512);
        summary.append("frames=").append(frames)
                .append(" resolution=").append(width).append('x').append(height)
                .append(" pending=").append(pendingCount)
                .append(" skipped=").append(skippedSamples);
        for (int scope = 0; scope < SCOPE_COUNT; scope++) {
            long samples = SAMPLE_COUNTS[scope];
            if (samples <= 0L) {
                continue;
            }
            double perFrameMs = TOTAL_NANOS[scope] / (double) frames / 1_000_000.0D;
            double maxInvocationMs = MAX_NANOS[scope] / 1_000_000.0D;
            summary.append(' ').append(SCOPE_NAMES[scope]).append('=')
                    .append(formatMillis(perFrameMs)).append("ms/frame")
                    .append(',').append(formatMillis(maxInvocationMs)).append("ms-max")
                    .append(',').append(samples).append(" samples");
        }
        lastSummary = summary.toString();
        MainMod.LOGGER.info("[AUSMGpuTiming] {}", lastSummary);
        clearStatistics();
    }

    private static String formatMillis(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static void clearStatistics() {
        for (int scope = 0; scope < SCOPE_COUNT; scope++) {
            TOTAL_NANOS[scope] = 0L;
            MAX_NANOS[scope] = 0L;
            SAMPLE_COUNTS[scope] = 0L;
        }
        skippedSamples = 0L;
        lastSummary = "not-ready";
    }

    private static void disable(String stage, Throwable error) {
        if (!disabled) {
            MainMod.LOGGER.warn("[AUSMGpuTiming] active=false stage={} reason={}: {}",
                    stage, error.getClass().getSimpleName(), error.getMessage());
        }
        disabled = true;
    }
}
