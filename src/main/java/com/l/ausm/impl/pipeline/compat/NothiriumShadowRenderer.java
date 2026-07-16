package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Draws Nothirium's prepared chunk VBOs with AUSM's active shader.
 *
 * Nothirium owns the normal terrain visibility lists. Calling its setup from
 * the shadow camera path corrupts that state, while calling its render method
 * binds Nothirium's own shader and normal camera matrix. This bridge only reads
 * the already prepared lists and emits vanilla-layout VBO draws.
 */
public final class NothiriumShadowRenderer {

    // Prefer shader-visible uniform offsets for transformed fixed-function GLSL paths;
    // fall back to matrix translation only when the uniform is unavailable.
    private static final boolean USE_CHUNK_OFFSET_UNIFORM = true;

    private static final String NOTHIRIUM_MOD_ID = "nothirium";
    private static final int VANILLA_BLOCK_STRIDE = 28;
    private static final int POSITION_OFFSET = 0;
    private static final int COLOR_OFFSET = 12;
    private static final int TEX_COORD_OFFSET = 16;
    private static final int LIGHT_COORD_OFFSET = 24;
    private static final int MAX_SHADOW_COMPILES_PER_FRAME = 8;
    private static final int MAX_MAIN_TERRAIN_SOLID_COMPILES_PER_FRAME = 12;
    private static final int MAX_MAIN_TERRAIN_CUTOUT_COMPILES_PER_FRAME = 6;
    private static final int MAX_MAIN_TERRAIN_TRANSLUCENT_COMPILES_PER_FRAME = 4;
    private static final int MAX_PENDING_SHADOW_COMPILES = 64;
    private static final int MAX_CHUNK_REFRESH_COMPILES = 16;
    private static final int MAX_CHUNK_REFRESH_AUDIT_LOGS = 0;
    private static final int MAX_VISIBLE_TRANSLUCENT_DIAG_LOGS = 0;
    private static final int MAX_VISIBLE_TERRAIN_FAILURE_LOGS = 0;
    private static final int MAX_VISIBLE_NON_SOLID_TERRAIN_FAILURE_LOGS = 0;
    private static final int MAX_VISIBLE_TERRAIN_DRAW_PROBE_LOGS = 0;
    private static final int MAX_NATIVE_DRAW_AUDIT_LOGS = 0;
    private static final int MAX_PROVIDER_DRAW_AUDIT_LOGS = 0;
    private static final int MAX_EMPTY_LIST_AUDIT_LOGS = 0;
    private static final int NOTHIRIUM_OFFSET_ATTRIBUTE = 4;
    private static final long MAIN_TERRAIN_COMPILE_RETRY_DELAY_MS = 80L;
    private static final long MAIN_TERRAIN_COMPILE_TRACK_TTL_MS = 2000L;
    private static final long REFLECTION_RETRY_DELAY_MS = 1000L;
    private static Reflection reflection;
    private static long nextReflectionAttemptMillis;

    private boolean disabled;
    private boolean warned;
    private boolean emptyAuditLogged;
    private boolean providerAuditLogged;
    private boolean providerSuccessAuditLogged;
    private boolean fallbackAuditLogged;
    private boolean uploadNonEmptyLogged;
    private int providerZeroAuditAttempts;
    private int uploadAuditAttempts;
    private int compileAuditAttempts;
    private int mainCompileAuditAttempts;
    private int chunkRefreshAuditAttempts;
    private int visibleTranslucentAuditAttempts;
    private int visibleTerrainFailureAttempts;
    private int visibleNonSolidTerrainFailureAttempts;
    private int visibleTerrainDrawProbeAttempts;
    private int nativeDrawAuditAttempts;
    private final ByteBuffer visibleTerrainVertexProbe = BufferUtils.createByteBuffer(128);
    private final FloatBuffer visibleTerrainMatrixProbe = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer visibleTerrainProjectionProbe = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer visibleTerrainUniformProbe = BufferUtils.createFloatBuffer(16);
    private final Map<Object, Long> mainTerrainCompileAttempts = new IdentityHashMap<>();
    private final Map<Integer, Integer> chunkOffsetUniformLocations = new HashMap<>();
    private static int visibleTranslucentStateLogs;

    public static boolean isAvailable() {
        return reflection() != null;
    }

    public void drainUploads() {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return;
        }

        try {
            Object dispatcher = reflection.getTaskDispatcher.invoke(null);
            if (dispatcher == null) {
                return;
            }

            int before = reflection.dispatcherQueueSize(dispatcher);
            reflection.dispatcherUpdate.invoke(dispatcher);
            int after = reflection.dispatcherQueueSize(dispatcher);
            auditUploadDrain(dispatcher, before, after);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
        }
    }

    public boolean refreshChunkColumn(int chunkX, int chunkZ) {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return false;
        }

        try {
            Object provider = reflection.getProvider.invoke(null);
            if (provider == null) {
                return false;
            }

            Object chunksObject = reflection.providerChunks.get(provider);
            if (!(chunksObject instanceof Object[] chunks) || chunks.length == 0) {
                return false;
            }

            Object renderer = reflection.getRenderer.invoke(null);
            Object dispatcher = reflection.getTaskDispatcher.invoke(null);
            int total = 0;
            int nullChunks = 0;
            int matched = 0;
            int alreadyDirty = 0;
            int running = 0;
            int released = 0;
            int marked = 0;
            int canCompile = 0;
            int cannotCompile = 0;
            int noDispatcher = 0;
            int scheduled = 0;
            int deferred = 0;
            for (Object chunk : chunks) {
                total++;
                if (chunk == null) {
                    nullChunks++;
                    continue;
                }

                int sectionX = reflection.getX(chunk) >> 4;
                int sectionZ = reflection.getZ(chunk) >> 4;
                if (sectionX != chunkX || sectionZ != chunkZ) {
                    continue;
                }

                matched++;
                if (reflection.isChunkDirty(chunk)) {
                    alreadyDirty++;
                }
                if (futureIsRunning(reflection.lastCompileTaskResult(chunk))) {
                    running++;
                }

                reflection.releaseBuffers.invoke(chunk);
                released++;
                reflection.markDirty.invoke(chunk);
                marked++;

                if (renderer == null || dispatcher == null) {
                    noDispatcher++;
                    continue;
                }
                if (!Boolean.TRUE.equals(reflection.canCompile(chunk))) {
                    cannotCompile++;
                    continue;
                }
                canCompile++;
                if (scheduled >= MAX_CHUNK_REFRESH_COMPILES) {
                    deferred++;
                    continue;
                }

                reflection.compileAsync.invoke(chunk, renderer, dispatcher);
                scheduled++;
            }

            if (scheduled > 0 && dispatcher != null) {
                reflection.dispatcherUpdate.invoke(dispatcher);
            }
            auditChunkRefresh(chunkX, chunkZ, total, nullChunks, matched, alreadyDirty, running,
                    released, marked, canCompile, cannotCompile, noDispatcher, scheduled, deferred);
            return matched > 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return false;
        }
    }

    public int renderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance) {
        return renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, 0, (short) 0);
    }

    public int renderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance,
                           int fallbackBlockEntityId, short fallbackRenderType) {
        return renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, false, true, false,
                fallbackBlockEntityId, fallbackRenderType);
    }

    public int renderLayerSchedulingCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance) {
        return renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, true, true, false, 0, (short) 0);
    }

    public int renderProviderLayerSchedulingCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                     double maxDistance, int fallbackBlockEntityId,
                                                     short fallbackRenderType, boolean requirePipelineStride) {
        return renderProviderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, true,
                fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
    }

    public int renderNearestProviderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                          double maxDistance, int maxChunks, int fallbackBlockEntityId,
                                          short fallbackRenderType, boolean requirePipelineStride) {
        return renderNearestProviderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, maxChunks,
                fallbackBlockEntityId, fallbackRenderType, requirePipelineStride, false);
    }

    public int renderNearestProviderLayerSchedulingCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                            double maxDistance, int maxChunks, int fallbackBlockEntityId,
                                                            short fallbackRenderType, boolean requirePipelineStride) {
        return renderNearestProviderLayerSchedulingCompiles(layer, cameraX, cameraY, cameraZ, maxDistance, maxChunks,
                fallbackBlockEntityId, fallbackRenderType, requirePipelineStride, true);
    }

    public int renderNearestProviderLayerSchedulingCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                            double maxDistance, int maxChunks, int fallbackBlockEntityId,
                                                            short fallbackRenderType, boolean requirePipelineStride,
                                                            boolean scheduleCompiles) {
        return renderNearestProviderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, maxChunks,
                fallbackBlockEntityId, fallbackRenderType, requirePipelineStride, scheduleCompiles);
    }

    private int renderNearestProviderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                           double maxDistance, int maxChunks, int fallbackBlockEntityId,
                                           short fallbackRenderType, boolean requirePipelineStride,
                                           boolean scheduleCompiles) {
        Reflection reflection = reflection();
        if (disabled || reflection == null || maxChunks <= 0) {
            return 0;
        }

        Object pass = reflection.passFor(layer);
        if (pass == null) {
            return 0;
        }

        try {
            Object provider = reflection.getProvider.invoke(null);
            if (provider == null) {
                return 0;
            }

            Object chunksArray = reflection.providerChunks.get(provider);
            if (!(chunksArray instanceof Object[] chunks) || chunks.length == 0) {
                return 0;
            }

            if (scheduleCompiles) {
                List<?> compileCandidates = nearestProviderChunks(
                        reflection,
                        chunks,
                        cameraX,
                        cameraY,
                        cameraZ,
                        maxDistance,
                        Math.max(maxChunks * 2, maxChunks)
                );
                scheduleMissingLayerCompiles(layer, reflection, pass, compileCandidates, cameraX, cameraY, cameraZ, maxDistance);
            }
            List<?> candidates = nearestRenderableProviderChunks(
                    reflection,
                    pass,
                    chunks,
                    cameraX,
                    cameraY,
                    cameraZ,
                    maxDistance,
                    maxChunks,
                    requirePipelineStride
            );
            if (candidates.isEmpty()) {
                return 0;
            }

            DrawStats stats = drawChunksWithLayerState(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ,
                    maxDistance, false, fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
            auditDrawStats("provider-nearest", layer, stats);
            return stats.drawn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return 0;
        }
    }

    public int scheduleLayerCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance) {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return 0;
        }

        Object pass = reflection.passFor(layer);
        if (pass == null) {
            return 0;
        }

        try {
            Object provider = reflection.getProvider.invoke(null);
            if (provider == null) {
                return 0;
            }
            Object chunksArray = reflection.providerChunks.get(provider);
            if (!(chunksArray instanceof Object[] chunks) || chunks.length == 0) {
                return 0;
            }
            List<?> candidates = providerChunksInRange(reflection, chunks, cameraX, cameraY, cameraZ, maxDistance);
            return scheduleMissingLayerCompiles(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ, maxDistance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return 0;
        }
    }

    public int scheduleNearestLayerCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                            double maxDistance, int maxChunks) {
        Reflection reflection = reflection();
        if (disabled || reflection == null || maxChunks <= 0) {
            return 0;
        }

        Object pass = reflection.passFor(layer);
        if (pass == null) {
            return 0;
        }

        try {
            Object provider = reflection.getProvider.invoke(null);
            if (provider == null) {
                return 0;
            }
            Object chunksArray = reflection.providerChunks.get(provider);
            if (!(chunksArray instanceof Object[] chunks) || chunks.length == 0) {
                return 0;
            }
            List<?> candidates = nearestProviderChunks(reflection, chunks, cameraX, cameraY, cameraZ, maxDistance, maxChunks);
            return scheduleMissingLayerCompiles(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ, maxDistance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return 0;
        }
    }

    public int renderVisibleLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                  int fallbackBlockEntityId, short fallbackRenderType) {
        return renderVisibleLayer(layer, cameraX, cameraY, cameraZ, fallbackBlockEntityId, fallbackRenderType, -1.0D);
    }

    public int renderVisibleLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                  int fallbackBlockEntityId, short fallbackRenderType, double maxDistance) {
        return renderVisibleLayer(layer, cameraX, cameraY, cameraZ, fallbackBlockEntityId, fallbackRenderType, maxDistance, true);
    }

    public int renderVisibleLayerAllowingVanillaStride(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                       int fallbackBlockEntityId, short fallbackRenderType) {
        return renderVisibleLayer(layer, cameraX, cameraY, cameraZ, fallbackBlockEntityId, fallbackRenderType, -1.0D, false);
    }

    public int renderNativeLayer(BlockRenderLayer layer) {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return -1;
        }

        Object pass = reflection.passFor(layer);
        if (pass == null) {
            return -1;
        }

        try {
            Object renderer = reflection.getRenderer.invoke(null);
            if (renderer == null) {
                return -1;
            }
            reflection.render.invoke(renderer, pass);
            int count = (Integer) reflection.renderedChunks.invoke(renderer, pass);
            auditNativeDraw(layer, renderer, count);
            return count;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return -1;
        }
    }

    private int renderVisibleLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                   int fallbackBlockEntityId, short fallbackRenderType, double maxDistance,
                                   boolean requirePipelineStride) {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return -1;
        }

        Object pass = reflection.passFor(layer);
        if (pass == null) {
            return -1;
        }

        try {
            Object renderer = reflection.getRenderer.invoke(null);
            if (renderer == null) {
                return -1;
            }

            Object chunksByPass = reflection.chunks.get(renderer);
            if (chunksByPass == null) {
                return -1;
            }

            Object chunksObject = reflection.enumMapGet.invoke(chunksByPass, pass);
            if (!(chunksObject instanceof List<?> chunks)) {
                return -1;
            }

            DrawStats stats = drawChunksWithLayerState(layer, reflection, pass, chunks, cameraX, cameraY, cameraZ, maxDistance, false,
                    fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
            if (stats.unsupportedStride > 0) {
                refreshUnsupportedPipelineChunks(reflection, stats.unsupportedPipelineChunks);
            }
            auditVisibleTranslucentLayer(layer, stats, fallbackBlockEntityId, fallbackRenderType, "after-draw");
            if (shouldAuditSparseVisibleBridge(stats)) {
                auditVisibleTerrainFailure(layer, stats, fallbackBlockEntityId, fallbackRenderType);
            }
            auditNonSolidVisibleTerrainFailure(layer, stats, fallbackBlockEntityId, fallbackRenderType);
            return stats.drawn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return -1;
        }
    }

    private int renderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance,
                            boolean scheduleCompiles, boolean audit, boolean visibleOnly,
                            int fallbackBlockEntityId, short fallbackRenderType) {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return 0;
        }

        Object pass = reflection.passFor(layer);
        if (pass == null) {
            return 0;
        }

        try {
            if (!visibleOnly) {
                Object provider = reflection.getProvider.invoke(null);
                if (provider != null) {
                    Object chunksArray = reflection.providerChunks.get(provider);
                    if (chunksArray instanceof Object[] chunks && chunks.length > 0) {
                        List<?> candidates = providerChunksInRange(reflection, chunks, cameraX, cameraY, cameraZ, maxDistance);
                        if (scheduleCompiles) {
                            scheduleMissingLayerCompiles(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ, maxDistance);
                        }
                        DrawStats stats = drawChunksWithLayerState(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ,
                                maxDistance, false, fallbackBlockEntityId, fallbackRenderType, false);
                        if (audit) {
                            auditDrawStats("provider", layer, stats);
                        }
                        if (stats.drawn > 0) {
                            return stats.drawn;
                        }
                    }
                }
            }

            Object renderer = reflection.getRenderer.invoke(null);
            if (renderer == null) {
                if (audit) {
                    auditEmpty(layer, null, null, null);
                }
                return 0;
            }

            Object chunksByPass = reflection.chunks.get(renderer);
            if (chunksByPass == null) {
                if (audit) {
                    auditEmpty(layer, renderer, pass, null);
                }
                return 0;
            }

            Object chunksObject = reflection.enumMapGet.invoke(chunksByPass, pass);
            if (!(chunksObject instanceof List<?> chunks) || chunks.isEmpty()) {
                if (audit) {
                    auditEmpty(layer, renderer, pass, chunksObject instanceof List<?> list ? list : null);
                }
                return 0;
            }

            DrawStats stats = drawChunksWithLayerState(layer, reflection, pass, chunks, cameraX, cameraY, cameraZ, maxDistance, false,
                    fallbackBlockEntityId, fallbackRenderType, false);
            if (audit) {
                auditDrawStats("fallback", layer, stats);
            }
            return stats.drawn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return 0;
        }
    }

    private int renderProviderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                    double maxDistance, boolean scheduleCompiles,
                                    int fallbackBlockEntityId, short fallbackRenderType,
                                    boolean requirePipelineStride) {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return 0;
        }

        Object pass = reflection.passFor(layer);
        if (pass == null) {
            return 0;
        }

        try {
            Object provider = reflection.getProvider.invoke(null);
            if (provider == null) {
                return 0;
            }

            Object chunksArray = reflection.providerChunks.get(provider);
            if (!(chunksArray instanceof Object[] chunks) || chunks.length == 0) {
                return 0;
            }

            List<?> candidates = providerChunksInRange(reflection, chunks, cameraX, cameraY, cameraZ, maxDistance);
            if (scheduleCompiles) {
                scheduleMissingLayerCompiles(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ, maxDistance);
            }
            DrawStats stats = drawChunksWithLayerState(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ,
                    maxDistance, false, fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
            auditDrawStats("provider-supplement", layer, stats);
            if (stats.unsupportedStride > 0) {
                refreshUnsupportedPipelineChunks(reflection, stats.unsupportedPipelineChunks);
            }
            return stats.drawn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return 0;
        }
    }

    private List<?> providerChunksInRange(Reflection reflection, Object[] chunks,
                                          double cameraX, double cameraY, double cameraZ, double maxDistance)
            throws ReflectiveOperationException {
        if (maxDistance < 0.0D) {
            return Arrays.asList(chunks);
        }

        double maxDistanceSquared = maxDistance * maxDistance;
        List<Object> filtered = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk == null) {
                continue;
            }

            int chunkX = reflection.getX(chunk);
            int chunkY = reflection.getY(chunk);
            int chunkZ = reflection.getZ(chunk);
            double dx = chunkX + 8.0D - cameraX;
            double dy = chunkY + 8.0D - cameraY;
            double dz = chunkZ + 8.0D - cameraZ;
            if (dx * dx + dy * dy + dz * dz <= maxDistanceSquared) {
                filtered.add(chunk);
            }
        }
        return filtered;
    }

    private List<?> nearestRenderableProviderChunks(Reflection reflection, Object pass, Object[] chunks,
                                                    double cameraX, double cameraY, double cameraZ,
                                                    double maxDistance, int maxChunks,
                                                    boolean requirePipelineStride)
            throws ReflectiveOperationException {
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        List<ProviderCandidate> candidates = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk == null) {
                continue;
            }

            int chunkX = reflection.getX(chunk);
            int chunkY = reflection.getY(chunk);
            int chunkZ = reflection.getZ(chunk);
            double dx = chunkX + 8.0D - cameraX;
            double dy = chunkY + 8.0D - cameraY;
            double dz = chunkZ + 8.0D - cameraZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (maxDistanceSquared >= 0.0D && distanceSquared > maxDistanceSquared) {
                continue;
            }
            if (!providerChunkHasRenderablePart(reflection, pass, chunk, requirePipelineStride)) {
                continue;
            }
            candidates.add(new ProviderCandidate(chunk, distanceSquared));
        }

        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distanceSquared));
        int limit = Math.min(maxChunks, candidates.size());
        List<Object> nearest = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            nearest.add(candidates.get(i).chunk);
        }
        return nearest;
    }

    private List<?> nearestProviderChunks(Reflection reflection, Object[] chunks,
                                          double cameraX, double cameraY, double cameraZ,
                                          double maxDistance, int maxChunks)
            throws ReflectiveOperationException {
        if (maxChunks <= 0) {
            return List.of();
        }

        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        List<ProviderCandidate> candidates = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk == null) {
                continue;
            }

            int chunkX = reflection.getX(chunk);
            int chunkY = reflection.getY(chunk);
            int chunkZ = reflection.getZ(chunk);
            double dx = chunkX + 8.0D - cameraX;
            double dy = chunkY + 8.0D - cameraY;
            double dz = chunkZ + 8.0D - cameraZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (maxDistanceSquared >= 0.0D && distanceSquared > maxDistanceSquared) {
                continue;
            }
            candidates.add(new ProviderCandidate(chunk, distanceSquared));
        }

        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distanceSquared));
        int limit = Math.min(maxChunks, candidates.size());
        List<Object> nearest = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            nearest.add(candidates.get(i).chunk);
        }
        return nearest;
    }

    private boolean providerChunkHasRenderablePart(Reflection reflection, Object pass, Object chunk, boolean requirePipelineStride)
            throws ReflectiveOperationException {
        Object part = reflection.getVboPart.invoke(chunk, pass);
        if (part == null || !(Boolean) reflection.isValid.invoke(part)) {
            return false;
        }
        int count = reflection.getCount(part);
        int vbo = reflection.getVbo(part);
        if (count <= 0 || vbo <= 0) {
            return false;
        }
        int size = reflection.getSize(part);
        int stride = vertexStride(size, count);
        return stride > 0 && (!requirePipelineStride || isPipelineBlockStride(stride));
    }

    private void scheduleShadowCompiles(Reflection reflection, Iterable<?> chunks,
                                        double cameraX, double cameraY, double cameraZ, double maxDistance)
            throws ReflectiveOperationException {
        Object renderer = reflection.getRenderer.invoke(null);
        Object dispatcher = reflection.getTaskDispatcher.invoke(null);
        if (renderer == null || dispatcher == null) {
            return;
        }

        CompileStats stats = new CompileStats();
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        for (Object chunk : chunks) {
            stats.total++;
            if (chunk == null) {
                stats.nullChunks++;
                continue;
            }

            int chunkX = reflection.getX(chunk);
            int chunkY = reflection.getY(chunk);
            int chunkZ = reflection.getZ(chunk);
            stats.captureFirstChunk(chunkX, chunkY, chunkZ);
            if (maxDistanceSquared >= 0.0D) {
                double dx = chunkX + 8.0D - cameraX;
                double dy = chunkY + 8.0D - cameraY;
                double dz = chunkZ + 8.0D - cameraZ;
                if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                    stats.distanceCulled++;
                    continue;
                }
            }
            stats.withinDistance++;

            Object future = reflection.lastCompileTaskResult(chunk);
            if (futureIsRunning(future)) {
                stats.running++;
                if (stats.running >= MAX_PENDING_SHADOW_COMPILES) {
                    break;
                }
                continue;
            }

            if (!reflection.isChunkDirty(chunk)) {
                stats.clean++;
                continue;
            }
            stats.dirty++;

            if (!Boolean.TRUE.equals(reflection.canCompile(chunk))) {
                stats.cannotCompile++;
                continue;
            }
            stats.canCompile++;

            reflection.compileAsync.invoke(chunk, renderer, dispatcher);
            stats.scheduled++;
            if (stats.scheduled >= MAX_SHADOW_COMPILES_PER_FRAME) {
                break;
            }
        }
        auditCompileStats(stats);
    }

    private int scheduleMissingLayerCompiles(BlockRenderLayer layer, Reflection reflection, Object pass, Iterable<?> chunks,
                                              double cameraX, double cameraY, double cameraZ, double maxDistance)
            throws ReflectiveOperationException {
        Object renderer = reflection.getRenderer.invoke(null);
        Object dispatcher = reflection.getTaskDispatcher.invoke(null);
        if (renderer == null || dispatcher == null) {
            return 0;
        }

        CompileStats stats = new CompileStats();
        int running = 0;
        long now = System.currentTimeMillis();
        pruneMainTerrainCompileAttempts(now);
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        List<CompileCandidate> candidates = new ArrayList<>();
        for (Object chunk : chunks) {
            stats.total++;
            if (chunk == null) {
                stats.nullChunks++;
                continue;
            }

            int chunkX = reflection.getX(chunk);
            int chunkY = reflection.getY(chunk);
            int chunkZ = reflection.getZ(chunk);
            stats.captureFirstChunk(chunkX, chunkY, chunkZ);
            if (maxDistanceSquared >= 0.0D) {
                double dx = chunkX + 8.0D - cameraX;
                double dy = chunkY + 8.0D - cameraY;
                double dz = chunkZ + 8.0D - cameraZ;
                if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                    stats.distanceCulled++;
                    continue;
                }
            }
            stats.withinDistance++;

            Object part = reflection.getVboPart.invoke(chunk, pass);
            boolean missingPart = part == null;
            boolean invalidPart = false;
            boolean emptyPart = false;
            if (part != null) {
                invalidPart = !((Boolean) reflection.isValid.invoke(part));
                if (!invalidPart) {
                    emptyPart = reflection.getCount(part) <= 0
                            || reflection.getVbo(part) <= 0;
                }
            }
            if (!missingPart && !invalidPart && !emptyPart) {
                stats.clean++;
                continue;
            }
            stats.dirty++;

            Object future = reflection.lastCompileTaskResult(chunk);
            if (futureIsRunning(future)) {
                running++;
                stats.running++;
                if (running >= MAX_PENDING_SHADOW_COMPILES) {
                    break;
                }
                continue;
            }

            Long lastAttempt = mainTerrainCompileAttempts.get(chunk);
            if (lastAttempt != null && now - lastAttempt < MAIN_TERRAIN_COMPILE_RETRY_DELAY_MS) {
                stats.throttled++;
                continue;
            }

            double distanceSquared = 0.0D;
            if (maxDistanceSquared >= 0.0D) {
                double dx = chunkX + 8.0D - cameraX;
                double dy = chunkY + 8.0D - cameraY;
                double dz = chunkZ + 8.0D - cameraZ;
                distanceSquared = dx * dx + dy * dy + dz * dz;
            }
            candidates.add(new CompileCandidate(chunk, distanceSquared, invalidPart));
        }

        candidates.sort((left, right) -> Double.compare(left.distanceSquared, right.distanceSquared));
        int budget = mainTerrainCompileBudget(layer);
        for (CompileCandidate candidate : candidates) {
            if (stats.scheduled >= budget) {
                break;
            }
            if (candidate.invalidPart) {
                reflection.releaseBuffers.invoke(candidate.chunk);
            }
            reflection.markDirty.invoke(candidate.chunk);
            if (!Boolean.TRUE.equals(reflection.canCompile(candidate.chunk))) {
                stats.cannotCompile++;
                continue;
            }
            stats.canCompile++;

            reflection.compileAsync.invoke(candidate.chunk, renderer, dispatcher);
            mainTerrainCompileAttempts.put(candidate.chunk, now);
            stats.scheduled++;
        }

        if (stats.scheduled > 0) {
            reflection.dispatcherUpdate.invoke(dispatcher);
        }
        auditMainCompileStats(stats);
        return stats.scheduled > 0 ? stats.scheduled : stats.running + stats.throttled + stats.cannotCompile;
    }

    private static int mainTerrainCompileBudget(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.SOLID) {
            return MAX_MAIN_TERRAIN_SOLID_COMPILES_PER_FRAME;
        }
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return MAX_MAIN_TERRAIN_TRANSLUCENT_COMPILES_PER_FRAME;
        }
        return MAX_MAIN_TERRAIN_CUTOUT_COMPILES_PER_FRAME;
    }

    private static final class CompileCandidate {
        private final Object chunk;
        private final double distanceSquared;
        private final boolean invalidPart;

        private CompileCandidate(Object chunk, double distanceSquared, boolean invalidPart) {
            this.chunk = chunk;
            this.distanceSquared = distanceSquared;
            this.invalidPart = invalidPart;
        }
    }

    private static final class ProviderCandidate {
        private final Object chunk;
        private final double distanceSquared;

        private ProviderCandidate(Object chunk, double distanceSquared) {
            this.chunk = chunk;
            this.distanceSquared = distanceSquared;
        }
    }

    private void pruneMainTerrainCompileAttempts(long now) {
        if (mainTerrainCompileAttempts.size() < 256) {
            return;
        }
        Iterator<Map.Entry<Object, Long>> iterator = mainTerrainCompileAttempts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Long> entry = iterator.next();
            if (now - entry.getValue() > MAIN_TERRAIN_COMPILE_TRACK_TTL_MS) {
                iterator.remove();
            }
        }
    }

    private static boolean futureIsRunning(Object futureObject) {
        return futureObject instanceof CompletableFuture<?> future && !future.isDone();
    }

    private DrawStats drawChunksWithLayerState(BlockRenderLayer layer, Reflection reflection, Object pass, Iterable<?> chunks,
                                               double cameraX, double cameraY, double cameraZ, double maxDistance, boolean collectState,
                                               int fallbackBlockEntityId, short fallbackRenderType, boolean requirePipelineStride)
            throws ReflectiveOperationException {
        LayerGlState layerState = LayerGlState.prepare(layer);
        try {
            return drawChunks(layer, reflection, pass, chunks, cameraX, cameraY, cameraZ, maxDistance, collectState,
                    fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
        } finally {
            if (layerState != null) {
                layerState.restore();
            }
        }
    }

    private void auditNativeDraw(BlockRenderLayer layer, Object renderer, int count) {
        if (nativeDrawAuditAttempts >= MAX_NATIVE_DRAW_AUDIT_LOGS) {
            return;
        }
        nativeDrawAuditAttempts++;
        MainMod.LOGGER.info(
                "[AUSMNothiriumNative] call={} layer={} renderer={} chunks={} gl={}",
                nativeDrawAuditAttempts,
                layer,
                renderer.getClass().getName(),
                count,
                glStateSummary()
        );
    }

    private DrawStats drawChunks(BlockRenderLayer layer, Reflection reflection, Object pass, Iterable<?> chunks,
                                 double cameraX, double cameraY, double cameraZ, double maxDistance, boolean collectState,
                                 int fallbackBlockEntityId, short fallbackRenderType, boolean requirePipelineStride)
            throws ReflectiveOperationException {
        DrawStats stats = new DrawStats();
        int previousVbo = -1;
        int previousVboSize = 0;
        int previousStride = -1;
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        PipelineContext context = PipelineContext.getInstance();
        boolean disableCullForMainTerrain = context.shouldDisableNothiriumChunkCulling(layer);
        int shaderlessBloomDimension = context.shaderlessBloomExtractionDimensionId();
        boolean previousCull = false;
        int previousMatrixMode = -1;

        try {
            if (GLContext.getCapabilities().OpenGL30) {
                GL30.glBindVertexArray(0);
            }
            if (disableCullForMainTerrain) {
                previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
            for (Object chunk : chunks) {
                stats.total++;
                if (chunk == null) {
                    stats.nullChunks++;
                    continue;
                }

                int chunkX = reflection.getX(chunk);
                int chunkY = reflection.getY(chunk);
                int chunkZ = reflection.getZ(chunk);
                stats.captureFirstChunk(chunkX, chunkY, chunkZ);
                if (collectState) {
                    stats.captureState(reflection, chunk, chunkX, chunkY, chunkZ);
                }
                if (maxDistanceSquared >= 0.0D) {
                    double dx = chunkX + 8.0D - cameraX;
                    double dy = chunkY + 8.0D - cameraY;
                    double dz = chunkZ + 8.0D - cameraZ;
                    if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                        stats.distanceCulled++;
                        continue;
                    }
                }
                stats.withinDistance++;
                if (!context.shouldRenderShaderlessBloomChunkLayer(
                        layer, chunkX, chunkY, chunkZ, shaderlessBloomDimension)) {
                    continue;
                }

                Object part = reflection.getVboPart.invoke(chunk, pass);
                if (part == null) {
                    stats.missingPart++;
                    continue;
                }
                stats.partPresent++;
                if (!(Boolean) reflection.isValid.invoke(part)) {
                    stats.invalidPart++;
                    continue;
                }
                stats.validPart++;

                int count = reflection.getCount(part);
                if (count <= 0) {
                    stats.emptyCount++;
                    continue;
                }
                stats.positiveCount++;

                int vbo = reflection.getVbo(part);
                if (vbo <= 0) {
                    stats.badVbo++;
                    continue;
                }
                stats.positiveVbo++;

                int offset = reflection.getOffset(part);
                int size = reflection.getSize(part);
                int stride = vertexStride(size, count);
                if (stride <= 0) {
                    stats.badStride++;
                    continue;
                }
                boolean pipelineStride = isPipelineBlockStride(stride);
                if (!pipelineStride) {
                    stats.unsupportedStride++;
                    stats.captureUnsupportedPipelineChunk(chunk);
                    if (requirePipelineStride) {
                        continue;
                    }
                }

                if (vbo != previousVbo || stride != previousStride) {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                    setupArrayPointers(stride, fallbackBlockEntityId, fallbackRenderType);
                    previousVbo = vbo;
                    previousStride = stride;
                    previousVboSize = GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE);
                }

                int first = reflection.getFirst(part);
                stats.captureFirstPart(vbo, first, count, offset, size, stride, previousVboSize);
                if (!validDrawRange(offset, size, previousVboSize)) {
                    stats.invalidRange++;
                    continue;
                }

                context.applyChunkFade(chunkX, chunkY, chunkZ);
                int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
                int chunkOffsetUniform = -1;
                boolean useChunkOffsetUniform = false;
                if (USE_CHUNK_OFFSET_UNIFORM && program > 0) {
                    chunkOffsetUniform = chunkOffsetUniformLocation(program);
                    useChunkOffsetUniform = chunkOffsetUniform >= 0;
                }
                if (useChunkOffsetUniform) {
                    GL20.glUniform3f(chunkOffsetUniform,
                            (float) (chunkX - cameraX),
                            (float) (chunkY - cameraY),
                            (float) (chunkZ - cameraZ));
                    DrawProbe drawProbe = captureVisibleTerrainDrawProbe(layer, chunkX, chunkY, chunkZ, cameraX, cameraY, cameraZ,
                            vbo, first, count, offset, size, stride, previousVboSize, pipelineStride);
                    int query = beginProbeQuery(drawProbe);
                    GL11.glDrawArrays(context.drawModeForActiveProgram(GL11.GL_QUADS), first, count);
                    finishVisibleTerrainDrawProbe(drawProbe, query);
                } else {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    if (previousMatrixMode < 0) {
                        previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
                    }
                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslated(chunkX - cameraX, chunkY - cameraY, chunkZ - cameraZ);
                        DrawProbe drawProbe = captureVisibleTerrainDrawProbe(layer, chunkX, chunkY, chunkZ, cameraX, cameraY, cameraZ,
                                vbo, first, count, offset, size, stride, previousVboSize, pipelineStride);
                        int query = beginProbeQuery(drawProbe);
                        GL11.glDrawArrays(context.drawModeForActiveProgram(GL11.GL_QUADS), first, count);
                        finishVisibleTerrainDrawProbe(drawProbe, query);
                    } finally {
                        GL11.glPopMatrix();
                    }
                }
                stats.drawn++;
            }
        } finally {
            if (previousMatrixMode >= 0) {
                GL11.glMatrixMode(previousMatrixMode);
            }
            if (disableCullForMainTerrain) {
                if (previousCull) {
                    GL11.glEnable(GL11.GL_CULL_FACE);
                } else {
                    GL11.glDisable(GL11.GL_CULL_FACE);
                }
            }
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            if (GLContext.getCapabilities().OpenGL30) {
                GL30.glBindVertexArray(0);
            }
            resetClientArrayState();
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(NOTHIRIUM_OFFSET_ATTRIBUTE);
            PipelineContext.getInstance().resetChunkFadeUniform();
        }

        return stats;
    }

    private int chunkOffsetUniformLocation(int program) {
        if (program <= 0) {
            return -1;
        }
        Integer cached = chunkOffsetUniformLocations.get(program);
        if (cached != null) {
            return cached;
        }
        int location = GL20.glGetUniformLocation(program, "ausm_ChunkOffset");
        chunkOffsetUniformLocations.put(program, location);
        return location;
    }

    private void refreshUnsupportedPipelineChunks(Reflection reflection, List<Object> chunks)
            throws ReflectiveOperationException {
        if (chunks.isEmpty()) {
            return;
        }

        Object renderer = reflection.getRenderer.invoke(null);
        Object dispatcher = reflection.getTaskDispatcher.invoke(null);
        if (renderer == null || dispatcher == null) {
            return;
        }

        int scheduled = 0;
        for (Object chunk : chunks) {
            if (chunk == null || scheduled >= MAX_CHUNK_REFRESH_COMPILES) {
                continue;
            }
            if (futureIsRunning(reflection.lastCompileTaskResult(chunk))) {
                continue;
            }

            reflection.releaseBuffers.invoke(chunk);
            reflection.markDirty.invoke(chunk);
            if (Boolean.TRUE.equals(reflection.canCompile(chunk))) {
                reflection.compileAsync.invoke(chunk, renderer, dispatcher);
                scheduled++;
            }
        }

        if (scheduled > 0) {
            reflection.dispatcherUpdate.invoke(dispatcher);
        }
    }

    private static int vertexStride(int size, int count) {
        if (size <= 0 || count <= 0 || size % count != 0) {
            return VANILLA_BLOCK_STRIDE;
        }
        int stride = size / count;
        return stride >= LIGHT_COORD_OFFSET + 4 ? stride : -1;
    }

    private static boolean validDrawRange(int offset, int size, int bufferSize) {
        if (offset < 0 || size <= 0 || bufferSize <= 0) {
            return false;
        }
        long endByte = (long) offset + size;
        return endByte >= 0L && endByte <= bufferSize;
    }

    private DrawProbe captureVisibleTerrainDrawProbe(BlockRenderLayer layer, int chunkX, int chunkY, int chunkZ,
                                                     double cameraX, double cameraY, double cameraZ,
                                                     int vbo, int first, int count, int offset, int size,
                                                     int stride, int vboSize, boolean pipelineStride) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.shouldDisableNothiriumChunkCulling(layer)
                || visibleTerrainDrawProbeAttempts >= MAX_VISIBLE_TERRAIN_DRAW_PROBE_LOGS
                || layer == BlockRenderLayer.TRANSLUCENT
                || count <= 0
                || stride <= 0
                || offset < 0) {
            return null;
        }

        visibleTerrainDrawProbeAttempts++;
        String vertex = "unread";
        try {
            int readSize = Math.min(stride, visibleTerrainVertexProbe.capacity());
            visibleTerrainVertexProbe.clear();
            visibleTerrainVertexProbe.limit(readSize);
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, offset, visibleTerrainVertexProbe);
            vertex = formatVertexProbe(stride);
        } catch (RuntimeException | LinkageError exception) {
            vertex = "error=" + exception.getClass().getSimpleName();
        } finally {
            visibleTerrainVertexProbe.clear();
        }

        String matrix = "unread";
        String clip = "unread";
        try {
            visibleTerrainMatrixProbe.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, visibleTerrainMatrixProbe);
            visibleTerrainProjectionProbe.clear();
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, visibleTerrainProjectionProbe);
            matrix = "mv=" + matrixSummary(visibleTerrainMatrixProbe)
                    + ",proj=" + matrixSummary(visibleTerrainProjectionProbe);
            clip = clipSummaryForFirstVertex(stride);
        } catch (RuntimeException | LinkageError exception) {
            matrix = "error=" + exception.getClass().getSimpleName();
            clip = "error=" + exception.getClass().getSimpleName();
        }

        return new DrawProbe(
                visibleTerrainDrawProbeAttempts,
                layer,
                chunkX,
                chunkY,
                chunkZ,
                cameraX,
                cameraY,
                cameraZ,
                vbo,
                first,
                count,
                offset,
                size,
                stride,
                vboSize,
                pipelineStride,
                vertex,
                matrix + ",clip=" + clip,
                terrainUniformSummary(),
                glStateSummary()
        );
    }

    private String clipSummaryForFirstVertex(int stride) {
        if (stride < POSITION_OFFSET + 12) {
            return "no-position";
        }
        float x = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET);
        float y = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 4);
        float z = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 8);

        float viewX = multiplyMatrixVector(visibleTerrainMatrixProbe, 0, x, y, z, 1.0F);
        float viewY = multiplyMatrixVector(visibleTerrainMatrixProbe, 1, x, y, z, 1.0F);
        float viewZ = multiplyMatrixVector(visibleTerrainMatrixProbe, 2, x, y, z, 1.0F);
        float viewW = multiplyMatrixVector(visibleTerrainMatrixProbe, 3, x, y, z, 1.0F);

        float clipX = multiplyMatrixVector(visibleTerrainProjectionProbe, 0, viewX, viewY, viewZ, viewW);
        float clipY = multiplyMatrixVector(visibleTerrainProjectionProbe, 1, viewX, viewY, viewZ, viewW);
        float clipZ = multiplyMatrixVector(visibleTerrainProjectionProbe, 2, viewX, viewY, viewZ, viewW);
        float clipW = multiplyMatrixVector(visibleTerrainProjectionProbe, 3, viewX, viewY, viewZ, viewW);
        if (!Float.isFinite(clipW) || Math.abs(clipW) < 1.0E-6F) {
            return "view=" + formatVec4(viewX, viewY, viewZ, viewW)
                    + ",clip=" + formatVec4(clipX, clipY, clipZ, clipW)
                    + ",ndc=invalid-w";
        }

        return "view=" + formatVec4(viewX, viewY, viewZ, viewW)
                + ",clip=" + formatVec4(clipX, clipY, clipZ, clipW)
                + ",ndc=" + formatVec3(clipX / clipW, clipY / clipW, clipZ / clipW);
    }

    private static float multiplyMatrixVector(FloatBuffer matrix, int row, float x, float y, float z, float w) {
        return matrix.get(row) * x
                + matrix.get(4 + row) * y
                + matrix.get(8 + row) * z
                + matrix.get(12 + row) * w;
    }

    private static String matrixSummary(FloatBuffer matrix) {
        return "m00=" + formatFloat(matrix.get(0))
                + ",m11=" + formatFloat(matrix.get(5))
                + ",m22=" + formatFloat(matrix.get(10))
                + ",m23=" + formatFloat(matrix.get(14))
                + ",m32=" + formatFloat(matrix.get(11))
                + ",m33=" + formatFloat(matrix.get(15))
                + ",t=" + formatVec3(matrix.get(12), matrix.get(13), matrix.get(14));
    }

    private static String formatVec4(float x, float y, float z, float w) {
        return formatFloat(x) + '/' + formatFloat(y) + '/' + formatFloat(z) + '/' + formatFloat(w);
    }

    private static String formatVec3(float x, float y, float z) {
        return formatFloat(x) + '/' + formatFloat(y) + '/' + formatFloat(z);
    }

    private static String formatFloat(float value) {
        if (!Float.isFinite(value)) {
            return "nan";
        }
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private int beginProbeQuery(DrawProbe probe) {
        if (probe == null || !GLContext.getCapabilities().OpenGL15) {
            return 0;
        }
        try {
            int query = GL15.glGenQueries();
            GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
            return query;
        } catch (RuntimeException | LinkageError exception) {
            return 0;
        }
    }

    private void finishVisibleTerrainDrawProbe(DrawProbe probe, int query) {
        if (probe == null) {
            return;
        }

        String samples = "unavailable";
        if (query > 0) {
            try {
                GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
                samples = Integer.toString(GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT));
            } catch (RuntimeException | LinkageError exception) {
                samples = "error=" + exception.getClass().getSimpleName();
            } finally {
                try {
                    GL15.glDeleteQueries(query);
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }

        MainMod.LOGGER.warn(
                "[AUSMNothiriumDrawProbe] call={} layer={} chunk={}/{}/{} camera={}/{}/{} translate={}/{}/{} vbo={} first={} count={} offset={} size={} stride={} vboSize={} pipelineStride={} vertex={} modelView={} uniforms={} samples={} gl={}",
                probe.call,
                probe.layer,
                probe.chunkX,
                probe.chunkY,
                probe.chunkZ,
                probe.cameraX,
                probe.cameraY,
                probe.cameraZ,
                probe.chunkX - probe.cameraX,
                probe.chunkY - probe.cameraY,
                probe.chunkZ - probe.cameraZ,
                probe.vbo,
                probe.first,
                probe.count,
                probe.offset,
                probe.size,
                probe.stride,
                probe.vboSize,
                probe.pipelineStride,
                probe.vertex,
                probe.modelView,
                probe.uniforms,
                samples,
                probe.gl
        );
    }

    private String terrainUniformSummary() {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program <= 0) {
            return "program=0";
        }
        return "gbufferModelView=" + matrixUniformTranslation(program, "gbufferModelView")
                + ",gbufferModelViewInverse=" + matrixUniformTranslation(program, "gbufferModelViewInverse")
                + ",gbufferProjection=" + matrixUniformTranslation(program, "gbufferProjection")
                + ",modelViewMatrix=" + matrixUniformTranslation(program, "modelViewMatrix");
    }

    private String matrixUniformTranslation(int program, String name) {
        try {
            int location = GL20.glGetUniformLocation(program, name);
            if (location < 0) {
                return "missing";
            }
            visibleTerrainUniformProbe.clear();
            GL20.glGetUniform(program, location, visibleTerrainUniformProbe);
            return "m03=" + visibleTerrainUniformProbe.get(12)
                    + ",m13=" + visibleTerrainUniformProbe.get(13)
                    + ",m23=" + visibleTerrainUniformProbe.get(14)
                    + ",m33=" + visibleTerrainUniformProbe.get(15);
        } catch (RuntimeException | LinkageError exception) {
            return "error=" + exception.getClass().getSimpleName();
        }
    }

    private String formatVertexProbe(int stride) {
        float x = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET);
        float y = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 4);
        float z = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 8);
        int r = visibleTerrainVertexProbe.get(COLOR_OFFSET) & 0xFF;
        int g = visibleTerrainVertexProbe.get(COLOR_OFFSET + 1) & 0xFF;
        int b = visibleTerrainVertexProbe.get(COLOR_OFFSET + 2) & 0xFF;
        int a = visibleTerrainVertexProbe.get(COLOR_OFFSET + 3) & 0xFF;
        float u = stride >= TEX_COORD_OFFSET + 8 ? visibleTerrainVertexProbe.getFloat(TEX_COORD_OFFSET) : Float.NaN;
        float v = stride >= TEX_COORD_OFFSET + 8 ? visibleTerrainVertexProbe.getFloat(TEX_COORD_OFFSET + 4) : Float.NaN;
        int lightU = stride >= LIGHT_COORD_OFFSET + 4 ? visibleTerrainVertexProbe.getShort(LIGHT_COORD_OFFSET) & 0xFFFF : -1;
        int lightV = stride >= LIGHT_COORD_OFFSET + 4 ? visibleTerrainVertexProbe.getShort(LIGHT_COORD_OFFSET + 2) & 0xFFFF : -1;
        StringBuilder builder = new StringBuilder();
        builder.append("pos=").append(x).append('/').append(y).append('/').append(z)
                .append(",color=").append(r).append('/').append(g).append('/').append(b).append('/').append(a)
                .append(",uv=").append(u).append('/').append(v)
                .append(",light=").append(lightU).append('/').append(lightV);
        if (stride >= ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET + 4) {
            builder.append(",normal=")
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET + 1))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET + 2));
        }
        if (stride >= ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 4) {
            builder.append(",midBlock=")
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 1))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 2))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 3));
        }
        return builder.toString();
    }

    private static void setupArrayPointers(int stride, int fallbackBlockEntityId, short fallbackRenderType) {
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glBindVertexArray(0);
        }
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);

        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_187420_d", "glVertexPointer"},
                new Class<?>[] {int.class, int.class, int.class, int.class}, (3), (GL11.GL_FLOAT), (stride), (POSITION_OFFSET));;
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_187406_e", "glColorPointer"},
                new Class<?>[] {int.class, int.class, int.class, int.class}, (4), (GL11.GL_UNSIGNED_BYTE), (stride), (COLOR_OFFSET));;
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_FLOAT, stride, TEX_COORD_OFFSET);

        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_SHORT, stride, LIGHT_COORD_OFFSET);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());

        if (isPipelineBlockStride(stride)) {
            setupPipelineAttributes(stride);
        } else {
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(NOTHIRIUM_OFFSET_ATTRIBUTE);
            setGenericAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                    fallbackBlockEntityId & 0xFFFF,
                    fallbackRenderType,
                    0.0F,
                    0.0F);
            setGenericAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 1.0F);
            setGenericAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE, 1.0F, 0.0F, 0.0F, 1.0F);
            setGenericAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 0.0F);
        }
        ExtendedVertexFormats.disableAttribute(NOTHIRIUM_OFFSET_ATTRIBUTE);
        setGenericAttribute(NOTHIRIUM_OFFSET_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    private static boolean isPipelineBlockStride(int stride) {
        ensurePipelineBlockFormat();
        return ExtendedVertexFormats.PIPELINE_BLOCK != null
                && stride == ExtendedVertexFormats.size(ExtendedVertexFormats.PIPELINE_BLOCK);
    }

    private static void ensurePipelineBlockFormat() {
        if (ExtendedVertexFormats.PIPELINE_BLOCK == null) {
            ExtendedVertexFormats.initialize();
        }
    }

    private static void setupPipelineAttributes(int stride) {
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL11.GL_BYTE, stride, (long) ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE,
                2,
                GL11.GL_FLOAT,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET
        );

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                true,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET
        );

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                4,
                GL11.GL_SHORT,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET
        );

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET
        );
    }

    private static boolean shouldAuditSparseVisibleBridge(DrawStats stats) {
        if (stats.drawn >= 64) {
            return false;
        }
        return stats.total > 0
                && (stats.partPresent > 0
                || stats.validPart > 0
                || stats.positiveCount > 0
                || stats.positiveVbo > 0
                || stats.missingPart > 0
                || stats.invalidPart > 0
                || stats.emptyCount > 0
                || stats.badVbo > 0
                || stats.badStride > 0
                || stats.unsupportedStride > 0
                || stats.invalidRange > 0);
    }

    private void auditVisibleTerrainFailure(BlockRenderLayer layer, DrawStats stats,
                                            int fallbackBlockEntityId, short fallbackRenderType) {
        if (visibleTerrainFailureAttempts >= MAX_VISIBLE_TERRAIN_FAILURE_LOGS) {
            return;
        }

        visibleTerrainFailureAttempts++;
        MainMod.LOGGER.warn(
                "[AUSMNothiriumVisibleTerrain] call={} layer={} total={} null={} within={} distCull={} missingPart={} part={} invalidPart={} valid={} emptyCount={} count={} badVbo={} vbo={} badStride={} unsupportedStride={} rangeSkip={} drawn={} fallbackBlock={} fallbackRenderType={} firstChunk={} firstPart={} gl={}",
                visibleTerrainFailureAttempts,
                layer,
                stats.total,
                stats.nullChunks,
                stats.withinDistance,
                stats.distanceCulled,
                stats.missingPart,
                stats.partPresent,
                stats.invalidPart,
                stats.validPart,
                stats.emptyCount,
                stats.positiveCount,
                stats.badVbo,
                stats.positiveVbo,
                stats.badStride,
                stats.unsupportedStride,
                stats.invalidRange,
                stats.drawn,
                fallbackBlockEntityId,
                fallbackRenderType,
                stats.firstChunk,
                stats.firstPart,
                glStateSummary()
        );
    }

    private void auditNonSolidVisibleTerrainFailure(BlockRenderLayer layer, DrawStats stats,
                                                   int fallbackBlockEntityId, short fallbackRenderType) {
        if (layer == null
                || layer == BlockRenderLayer.SOLID
                || stats.drawn > 0
                || visibleNonSolidTerrainFailureAttempts >= MAX_VISIBLE_NON_SOLID_TERRAIN_FAILURE_LOGS) {
            return;
        }

        visibleNonSolidTerrainFailureAttempts++;
        MainMod.LOGGER.warn(
                "[AUSMNothiriumNonSolidVisible] call={} layer={} total={} null={} within={} distCull={} missingPart={} part={} invalidPart={} valid={} emptyCount={} count={} badVbo={} vbo={} badStride={} unsupportedStride={} rangeSkip={} drawn={} fallbackBlock={} fallbackRenderType={} firstChunk={} firstPart={} gl={}",
                visibleNonSolidTerrainFailureAttempts,
                layer,
                stats.total,
                stats.nullChunks,
                stats.withinDistance,
                stats.distanceCulled,
                stats.missingPart,
                stats.partPresent,
                stats.invalidPart,
                stats.validPart,
                stats.emptyCount,
                stats.positiveCount,
                stats.badVbo,
                stats.positiveVbo,
                stats.badStride,
                stats.unsupportedStride,
                stats.invalidRange,
                stats.drawn,
                fallbackBlockEntityId,
                fallbackRenderType,
                stats.firstChunk,
                stats.firstPart,
                glStateSummary()
        );
    }

    private void auditVisibleTranslucentLayer(BlockRenderLayer layer, DrawStats stats,
                                             int fallbackBlockEntityId, short fallbackRenderType, String stage) {
        // Probe disabled.
}

    private static void resetClientArrayState() {
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
    }

    private static void setGenericAttribute(int index, float x, float y, float z, float w) {
        if (index >= 0 && index < GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS)) {
            GL20.glVertexAttrib4f(index, x, y, z, w);
        }
    }

    private static final class LayerGlState {
        private final boolean texture2D;
        private final boolean depthTest;
        private final boolean alphaTest;
        private final boolean blend;
        private final boolean depthMask;
        private final int depthFunc;
        private final int alphaFunc;
        private final float alphaRef;
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;

        private LayerGlState() {
            this.texture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            this.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            this.alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            this.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            this.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            this.alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
            this.alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
            this.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            this.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            this.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            this.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        }

        private static LayerGlState prepare(BlockRenderLayer layer) {
            if (layer != BlockRenderLayer.TRANSLUCENT) {
                return null;
            }

            LayerGlState previous = new LayerGlState();
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            FixedFunctionGlState.prepareTranslucentBlockLayer(mc);
            forceTranslucentFixedFunctionState();
            logVisibleTranslucentState("prepare");
            return previous;
        }

        private void restore() {
        // Probe disabled.
}
    }

    private static void forceTranslucentFixedFunctionState() {
        FixedFunctionGlState.forceTranslucentBlockLayer();
    }

    private static void logVisibleTranslucentState(String stage) {
        // Probe disabled.
}

    private static String glStateSummary() {
        StringBuilder builder = new StringBuilder(FixedFunctionGlState.summary())
                .append(",matrixMode=").append(matrixModeName(GL11.glGetInteger(GL11.GL_MATRIX_MODE)))
                .append(",cull=").append(GL11.glIsEnabled(GL11.GL_CULL_FACE))
                .append(",colorMask=").append(colorMaskSummary())
                .append(",drawBuffer=").append(GL11.glGetInteger(GL11.GL_DRAW_BUFFER))
                .append(",readBuffer=").append(GL11.glGetInteger(GL11.GL_READ_BUFFER))
                .append(",arrayBuffer=").append(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING))
                .append(",elementBuffer=").append(GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
        if (GLContext.getCapabilities().OpenGL30) {
            builder.append(",drawFbo=").append(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING))
                    .append(",readFbo=").append(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING))
                    .append(",vao=").append(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        }
        return builder.toString();
    }

    private static String matrixModeName(int mode) {
        return switch (mode) {
            case GL11.GL_MODELVIEW -> "modelview";
            case GL11.GL_PROJECTION -> "projection";
            case GL11.GL_TEXTURE -> "texture";
            default -> Integer.toString(mode);
        };
    }

    private static String colorMaskSummary() {
        ByteBuffer mask = BufferUtils.createByteBuffer(4);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, mask);
        return (mask.get(0) != 0) + "/" + (mask.get(1) != 0) + "/" + (mask.get(2) != 0) + "/" + (mask.get(3) != 0);
    }

    private void auditCompileStats(CompileStats stats) {
        if (compileAuditAttempts >= 8) {
            return;
        }
        if (stats.scheduled <= 0 && stats.running <= 0 && stats.dirty <= 0 && stats.canCompile <= 0) {
            return;
        }
        compileAuditAttempts++;
        MainMod.LOGGER.debug(
                "[NothiriumShadowBridge] scheduledCompiles attempt={} total={} null={} within={} distCull={} dirty={} clean={} canCompile={} cannotCompile={} running={} scheduled={} firstChunk={}",
                compileAuditAttempts,
                stats.total,
                stats.nullChunks,
                stats.withinDistance,
                stats.distanceCulled,
                stats.dirty,
                stats.clean,
                stats.canCompile,
                stats.cannotCompile,
                stats.running,
                stats.scheduled,
                stats.firstChunk
        );
    }

    private void auditMainCompileStats(CompileStats stats) {
        // Disabled: this path runs during terrain recovery and must not log on the render thread.
    }

    private void auditChunkRefresh(int chunkX, int chunkZ, int total, int nullChunks, int matched, int alreadyDirty,
                                   int running, int released, int marked, int canCompile, int cannotCompile,
                                   int noDispatcher, int scheduled, int deferred) {
        if (matched <= 0) {
            return;
        }
        if (chunkRefreshAuditAttempts >= MAX_CHUNK_REFRESH_AUDIT_LOGS) {
            return;
        }
        chunkRefreshAuditAttempts++;
        MainMod.LOGGER.debug(
                "[NothiriumShadowBridge] refreshedChunkColumn attempt={} chunk={},{} total={} null={} matched={} alreadyDirty={} running={} released={} marked={} canCompile={} cannotCompile={} noDispatcher={} scheduled={} deferred={}",
                chunkRefreshAuditAttempts,
                chunkX,
                chunkZ,
                total,
                nullChunks,
                matched,
                alreadyDirty,
                running,
                released,
                marked,
                canCompile,
                cannotCompile,
                noDispatcher,
                scheduled,
                deferred
        );
    }

    private void auditUploadDrain(Object dispatcher, int before, int after) {
        if (before > 0 || after > 0) {
            if (uploadNonEmptyLogged) {
                return;
            }
            uploadNonEmptyLogged = true;
        } else {
            if (uploadAuditAttempts >= 8) {
                return;
            }
            uploadAuditAttempts++;
        }
        MainMod.LOGGER.debug(
                "[NothiriumShadowBridge] drainedUploads attempt={} dispatcher={} queueBefore={} queueAfter={}",
                uploadAuditAttempts,
                dispatcher.getClass().getName(),
                before,
                after
        );
    }

    private void auditDrawStats(String source, BlockRenderLayer layer, DrawStats stats) {
        if (MAX_PROVIDER_DRAW_AUDIT_LOGS <= 0) {
            return;
        }
        if (source.equals("provider")) {
            if (stats.drawn > 0) {
                if (providerSuccessAuditLogged) {
                    return;
                }
                providerSuccessAuditLogged = true;
            } else {
                if (providerZeroAuditAttempts >= 8) {
                    return;
                }
                providerZeroAuditAttempts++;
            }
        } else {
            if (fallbackAuditLogged) {
                return;
            }
            fallbackAuditLogged = true;
        }

        MainMod.LOGGER.info(
                "[NothiriumShadowBridge] source={} layer={} attempt={} total={} null={} within={} distCull={} missingPart={} part={} invalidPart={} valid={} emptyCount={} count={} badVbo={} vbo={} badStride={} unsupportedStride={} rangeSkip={} drawn={} firstChunk={} firstPart={} state={}",
                source,
                layer,
                source.equals("provider") ? providerZeroAuditAttempts : -1,
                stats.total,
                stats.nullChunks,
                stats.withinDistance,
                stats.distanceCulled,
                stats.missingPart,
                stats.partPresent,
                stats.invalidPart,
                stats.validPart,
                stats.emptyCount,
                stats.positiveCount,
                stats.badVbo,
                stats.positiveVbo,
                stats.badStride,
                stats.unsupportedStride,
                stats.invalidRange,
                stats.drawn,
                stats.firstChunk,
                stats.firstPart,
                stats.stateSummary()
        );
    }

    private void warnOnce(Exception e) {
        if (warned) {
            return;
        }
        warned = true;
        MainMod.LOGGER.warn("[NothiriumCompat] Disabled shadow VBO bridge after an error", e);
    }

    private void auditEmpty(BlockRenderLayer layer, Object renderer, Object pass, List<?> chunks) {
        if (MAX_EMPTY_LIST_AUDIT_LOGS <= 0) {
            return;
        }
        if (emptyAuditLogged) {
            return;
        }
        emptyAuditLogged = true;

        Reflection reflection = reflection();
        int renderedChunks = -1;
        int renderedSections = -1;
        int totalRenderedSections = -1;
        try {
            if (reflection != null && renderer != null && pass != null) {
                renderedChunks = (Integer) reflection.renderedChunks.invoke(renderer, pass);
                renderedSections = (Integer) reflection.renderedSections.invoke(null, pass);
                totalRenderedSections = (Integer) reflection.renderedSectionsAll.invoke(null);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            MainMod.LOGGER.debug("[NothiriumShadowBridge] Empty-list audit failed", e);
        }

        MainMod.LOGGER.info(
                "[NothiriumShadowBridge] layer={} renderer={} listSize={} renderedChunks={} renderedSections={} totalRenderedSections={}",
                layer,
                renderer != null ? renderer.getClass().getName() : "null",
                chunks != null ? chunks.size() : -1,
                renderedChunks,
                renderedSections,
                totalRenderedSections
        );
    }

    private static Reflection reflection() {
        Reflection existing = reflection;
        if (existing != null) {
            return existing;
        }
        if (!Loader.isModLoaded(NOTHIRIUM_MOD_ID)) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now < nextReflectionAttemptMillis) {
            return null;
        }
        nextReflectionAttemptMillis = now + REFLECTION_RETRY_DELAY_MS;
        Reflection loaded = Reflection.load();
        if (loaded != null) {
            reflection = loaded;
        }
        return loaded;
    }

    private static final class CompileStats {
        private int total;
        private int nullChunks;
        private int withinDistance;
        private int distanceCulled;
        private int dirty;
        private int clean;
        private int canCompile;
        private int cannotCompile;
        private int running;
        private int throttled;
        private int scheduled;
        private String firstChunk = "n/a";

        private void captureFirstChunk(int x, int y, int z) {
            if (firstChunk.equals("n/a")) {
                firstChunk = x + "," + y + "," + z;
            }
        }
    }

    private record DrawProbe(
            int call,
            BlockRenderLayer layer,
            int chunkX,
            int chunkY,
            int chunkZ,
            double cameraX,
            double cameraY,
            double cameraZ,
            int vbo,
            int first,
            int count,
            int offset,
            int size,
            int stride,
            int vboSize,
            boolean pipelineStride,
            String vertex,
            String modelView,
            String uniforms,
            String gl) {
    }

    private static final class DrawStats {
        private int total;
        private int nullChunks;
        private int withinDistance;
        private int distanceCulled;
        private int missingPart;
        private int partPresent;
        private int invalidPart;
        private int validPart;
        private int emptyCount;
        private int positiveCount;
        private int badVbo;
        private int positiveVbo;
        private int badStride;
        private int unsupportedStride;
        private int invalidRange;
        private int drawn;
        private String firstChunk = "n/a";
        private String firstPart = "n/a";
        private String firstState = "n/a";
        private int dirtyChunks;
        private int cleanChunks;
        private int emptyChunks;
        private int nonEmptyChunks;
        private int canCompileChunks;
        private int cannotCompileChunks;
        private int taskPresent;
        private int futureNull;
        private int futureRunning;
        private int futureDone;
        private int futureCancelled;
        private int futureExceptional;
        private int recordedChunks;
        private int enqueuedChunks;
        private int maxRecorded = -1;
        private int maxEnqueued = -1;
        private int nonemptyMaskChunks;
        private final List<Object> unsupportedPipelineChunks = new ArrayList<>();

        private void captureFirstChunk(int x, int y, int z) {
            if (firstChunk.equals("n/a")) {
                firstChunk = x + "," + y + "," + z;
            }
        }

        private void captureFirstPart(int vbo, int first, int count, int offset, int size, int stride, int vboSize) {
            if (firstPart.equals("n/a")) {
                firstPart = "vbo=" + vbo
                        + " first=" + first
                        + " count=" + count
                        + " offset=" + offset
                        + " size=" + size
                        + " stride=" + stride
                        + " vboSize=" + vboSize;
            }
        }

        private void captureUnsupportedPipelineChunk(Object chunk) {
            if (unsupportedPipelineChunks.size() < MAX_CHUNK_REFRESH_COMPILES) {
                unsupportedPipelineChunks.add(chunk);
            }
        }

        private void captureState(Reflection reflection, Object chunk, int x, int y, int z)
                throws ReflectiveOperationException {
            boolean dirty = reflection.isChunkDirty(chunk);
            boolean empty = reflection.isChunkEmpty(chunk);
            Boolean canCompile = reflection.canCompile(chunk);
            Object task = reflection.lastCompileTask(chunk);
            Object futureObject = reflection.lastCompileTaskResult(chunk);
            int recorded = reflection.lastTimeRecorded(chunk);
            int enqueued = reflection.lastTimeEnqueued(chunk);
            int nonemptyMask = reflection.nonemptyVboParts(chunk);

            if (dirty) {
                dirtyChunks++;
            } else {
                cleanChunks++;
            }
            if (empty) {
                emptyChunks++;
            } else {
                nonEmptyChunks++;
            }
            if (Boolean.TRUE.equals(canCompile)) {
                canCompileChunks++;
            } else if (Boolean.FALSE.equals(canCompile)) {
                cannotCompileChunks++;
            }
            if (task != null) {
                taskPresent++;
            }
            String future = futureState(futureObject);
            switch (future) {
                case "null" -> futureNull++;
                case "running" -> futureRunning++;
                case "done" -> futureDone++;
                case "cancelled" -> futureCancelled++;
                case "exceptional" -> futureExceptional++;
                default -> {
                }
            }
            if (recorded >= 0) {
                recordedChunks++;
                maxRecorded = Math.max(maxRecorded, recorded);
            }
            if (enqueued >= 0) {
                enqueuedChunks++;
                maxEnqueued = Math.max(maxEnqueued, enqueued);
            }
            if (nonemptyMask != 0) {
                nonemptyMaskChunks++;
            }
            if (firstState.equals("n/a")) {
                firstState = "pos=" + x + "," + y + "," + z
                        + " dirty=" + dirty
                        + " empty=" + empty
                        + " canCompile=" + canCompile
                        + " task=" + (task != null)
                        + " future=" + future
                        + " recorded=" + recorded
                        + " enqueued=" + enqueued
                        + " nonemptyMask=" + nonemptyMask;
            }
        }

        private String stateSummary() {
            if (firstState.equals("n/a")) {
                return "n/a";
            }
            return "dirty=" + dirtyChunks
                    + " clean=" + cleanChunks
                    + " empty=" + emptyChunks
                    + " nonEmpty=" + nonEmptyChunks
                    + " canCompile=" + canCompileChunks
                    + " cannotCompile=" + cannotCompileChunks
                    + " task=" + taskPresent
                    + " future=null/" + futureNull
                    + ",running/" + futureRunning
                    + ",done/" + futureDone
                    + ",cancelled/" + futureCancelled
                    + ",exceptional/" + futureExceptional
                    + " recorded=" + recordedChunks + "(max=" + maxRecorded + ")"
                    + " enqueued=" + enqueuedChunks + "(max=" + maxEnqueued + ")"
                    + " nonemptyMask=" + nonemptyMaskChunks
                    + " first={" + firstState + "}";
        }

        private static String futureState(Object futureObject) {
            if (!(futureObject instanceof CompletableFuture<?> future)) {
                return futureObject == null ? "null" : futureObject.getClass().getSimpleName();
            }
            if (future.isCancelled()) {
                return "cancelled";
            }
            if (future.isCompletedExceptionally()) {
                return "exceptional";
            }
            return future.isDone() ? "done" : "running";
        }
    }

    private static final class Reflection {
        private final Method getRenderer;
        private final Method getProvider;
        private final Method getTaskDispatcher;
        private final Method dispatcherUpdate;
        private final Method render;
        private final Method enumMapGet;
        private final Method renderedChunks;
        private final Method renderedSections;
        private final Method renderedSectionsAll;
        private final Method getVboPart;
        private final MethodHandle getVbo;
        private final MethodHandle getFirst;
        private final MethodHandle getCount;
        private final MethodHandle getOffset;
        private final MethodHandle getSize;
        private final Method isValid;
        private final Method isDirty;
        private final Method isEmpty;
        private final Method markDirty;
        private final Method releaseBuffers;
        private final Method canCompile;
        private final Method compileAsync;
        private final MethodHandle getX;
        private final MethodHandle getY;
        private final MethodHandle getZ;
        private final Field chunks;
        private final Field providerChunks;
        private final Field dispatcherQueue;
        private final Field lastCompileTask;
        private final Field lastCompileTaskResult;
        private final Field lastTimeRecorded;
        private final Field lastTimeEnqueued;
        private final Field nonemptyVboParts;
        private final Object solid;
        private final Object cutout;
        private final Object cutoutMipped;
        private final Object translucent;

        private Reflection(Method getRenderer, Method getProvider, Method getTaskDispatcher, Method dispatcherUpdate,
                           Method render, Method enumMapGet, Method renderedChunks, Method renderedSections,
                           Method renderedSectionsAll, Method getVboPart, MethodHandle getVbo, MethodHandle getFirst,
                           MethodHandle getCount, MethodHandle getOffset, MethodHandle getSize, Method isValid, Method isDirty,
                           Method isEmpty, Method markDirty, Method releaseBuffers, Method canCompile,
                           Method compileAsync, MethodHandle getX, MethodHandle getY, MethodHandle getZ, Field chunks,
                           Field providerChunks, Field dispatcherQueue, Field lastCompileTask,
                           Field lastCompileTaskResult, Field lastTimeRecorded, Field lastTimeEnqueued, Field nonemptyVboParts,
                           Object solid, Object cutout, Object cutoutMipped, Object translucent) {
            this.getRenderer = getRenderer;
            this.getProvider = getProvider;
            this.getTaskDispatcher = getTaskDispatcher;
            this.dispatcherUpdate = dispatcherUpdate;
            this.render = render;
            this.enumMapGet = enumMapGet;
            this.renderedChunks = renderedChunks;
            this.renderedSections = renderedSections;
            this.renderedSectionsAll = renderedSectionsAll;
            this.getVboPart = getVboPart;
            this.getVbo = getVbo;
            this.getFirst = getFirst;
            this.getCount = getCount;
            this.getOffset = getOffset;
            this.getSize = getSize;
            this.isValid = isValid;
            this.isDirty = isDirty;
            this.isEmpty = isEmpty;
            this.markDirty = markDirty;
            this.releaseBuffers = releaseBuffers;
            this.canCompile = canCompile;
            this.compileAsync = compileAsync;
            this.getX = getX;
            this.getY = getY;
            this.getZ = getZ;
            this.chunks = chunks;
            this.providerChunks = providerChunks;
            this.dispatcherQueue = dispatcherQueue;
            this.lastCompileTask = lastCompileTask;
            this.lastCompileTaskResult = lastCompileTaskResult;
            this.lastTimeRecorded = lastTimeRecorded;
            this.lastTimeEnqueued = lastTimeEnqueued;
            this.nonemptyVboParts = nonemptyVboParts;
            this.solid = solid;
            this.cutout = cutout;
            this.cutoutMipped = cutoutMipped;
            this.translucent = translucent;
        }

        private int getX(Object chunk) throws ReflectiveOperationException {
            return invokeInt(getX, chunk);
        }

        private int getY(Object chunk) throws ReflectiveOperationException {
            return invokeInt(getY, chunk);
        }

        private int getZ(Object chunk) throws ReflectiveOperationException {
            return invokeInt(getZ, chunk);
        }

        private int getVbo(Object part) throws ReflectiveOperationException {
            return invokeInt(getVbo, part);
        }

        private int getFirst(Object part) throws ReflectiveOperationException {
            return invokeInt(getFirst, part);
        }

        private int getCount(Object part) throws ReflectiveOperationException {
            return invokeInt(getCount, part);
        }

        private int getOffset(Object part) throws ReflectiveOperationException {
            return invokeInt(getOffset, part);
        }

        private int getSize(Object part) throws ReflectiveOperationException {
            return invokeInt(getSize, part);
        }

        private static int invokeInt(MethodHandle getter, Object target) throws ReflectiveOperationException {
            try {
                return (int) getter.invokeExact(target);
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Throwable throwable) {
                throw new ReflectiveOperationException(throwable);
            }
        }

        private static MethodHandle intGetter(Method method) throws IllegalAccessException {
            return MethodHandles.publicLookup()
                    .unreflect(method)
                    .asType(MethodType.methodType(int.class, Object.class));
        }

        private static Reflection load() {
            try {
                Class<?> managerClass = Class.forName("meldexun.nothirium.mc.renderer.ChunkRenderManager");
                Class<?> rendererBaseClass = Class.forName("meldexun.nothirium.renderer.chunk.AbstractChunkRenderer");
                Class<?> providerBaseClass = Class.forName("meldexun.nothirium.renderer.chunk.AbstractRenderChunkProvider");
                Class<?> abstractChunkClass = Class.forName("meldexun.nothirium.renderer.chunk.AbstractRenderChunk");
                Class<?> chunkRendererClass = Class.forName("meldexun.nothirium.api.renderer.chunk.IChunkRenderer");
                Class<?> dispatcherClass = Class.forName("meldexun.nothirium.api.renderer.chunk.IRenderChunkDispatcher");
                Class<?> renderChunkClass = Class.forName("meldexun.nothirium.api.renderer.chunk.IRenderChunk");
                Class<?> vboPartClass = Class.forName("meldexun.nothirium.api.renderer.IVBOPart");
                Class<?> passClass = Class.forName("meldexun.nothirium.api.renderer.chunk.ChunkRenderPass");
                Class<?> enumMapClass = Class.forName("meldexun.nothirium.util.collection.Enum2ObjMap");

                Method getRenderer = managerClass.getMethod("getRenderer");
                Method getProvider = managerClass.getMethod("getProvider");
                Method getTaskDispatcher = managerClass.getMethod("getTaskDispatcher");
                Method dispatcherUpdate = dispatcherClass.getMethod("update");
                Method render = chunkRendererClass.getMethod("render", passClass);
                Method renderedSections = managerClass.getMethod("renderedSections", passClass);
                Method renderedSectionsAll = managerClass.getMethod("renderedSections");
                Method enumMapGet = enumMapClass.getMethod("get", Enum.class);
                Method renderedChunks = rendererBaseClass.getMethod("renderedChunks", passClass);
                Method getVboPart = renderChunkClass.getMethod("getVBOPart", passClass);
                Method getVbo = vboPartClass.getMethod("getVBO");
                Method getFirst = vboPartClass.getMethod("getFirst");
                Method getCount = vboPartClass.getMethod("getCount");
                Method getOffset = vboPartClass.getMethod("getOffset");
                Method getSize = vboPartClass.getMethod("getSize");
                Method isValid = vboPartClass.getMethod("isValid");
                Method isDirty = abstractChunkClass.getMethod("isDirty");
                Method isEmpty = renderChunkClass.getMethod("isEmpty");
                Method markDirty = abstractChunkClass.getMethod("markDirty");
                Method releaseBuffers = abstractChunkClass.getMethod("releaseBuffers");
                Method canCompile = abstractChunkClass.getDeclaredMethod("canCompile");
                canCompile.setAccessible(true);
                Method compileAsync = abstractChunkClass.getMethod("compileAsync", chunkRendererClass, dispatcherClass);
                Method getX = renderChunkClass.getMethod("getX");
                Method getY = renderChunkClass.getMethod("getY");
                Method getZ = renderChunkClass.getMethod("getZ");
                Field chunks = findField(rendererBaseClass, "chunks");
                chunks.setAccessible(true);
                Field providerChunks = findField(providerBaseClass, "chunks");
                providerChunks.setAccessible(true);
                Field dispatcherQueue = findOptionalDispatcherQueueField();
                Field lastCompileTask = findField(abstractChunkClass, "lastCompileTask");
                lastCompileTask.setAccessible(true);
                Field lastCompileTaskResult = findField(abstractChunkClass, "lastCompileTaskResult");
                lastCompileTaskResult.setAccessible(true);
                Field lastTimeRecorded = findField(abstractChunkClass, "lastTimeRecorded");
                lastTimeRecorded.setAccessible(true);
                Field lastTimeEnqueued = findField(abstractChunkClass, "lastTimeEnqueued");
                lastTimeEnqueued.setAccessible(true);
                Field nonemptyVboParts = findField(abstractChunkClass, "nonemptyVboParts");
                nonemptyVboParts.setAccessible(true);

                Object solid = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "SOLID");
                Object cutout = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "CUTOUT");
                Object cutoutMipped = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "CUTOUT_MIPPED");
                Object translucent = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "TRANSLUCENT");
                return new Reflection(
                        getRenderer,
                        getProvider,
                        getTaskDispatcher,
                        dispatcherUpdate,
                        render,
                        enumMapGet,
                        renderedChunks,
                        renderedSections,
                        renderedSectionsAll,
                        getVboPart,
                        intGetter(getVbo),
                        intGetter(getFirst),
                        intGetter(getCount),
                        intGetter(getOffset),
                        intGetter(getSize),
                        isValid,
                        isDirty,
                        isEmpty,
                        markDirty,
                        releaseBuffers,
                        canCompile,
                        compileAsync,
                        intGetter(getX),
                        intGetter(getY),
                        intGetter(getZ),
                        chunks,
                        providerChunks,
                        dispatcherQueue,
                        lastCompileTask,
                        lastCompileTaskResult,
                        lastTimeRecorded,
                        lastTimeEnqueued,
                        nonemptyVboParts,
                        solid,
                        cutout,
                        cutoutMipped,
                        translucent
                );
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        private boolean isChunkDirty(Object chunk) throws ReflectiveOperationException {
            return (Boolean) isDirty.invoke(chunk);
        }

        private boolean isChunkEmpty(Object chunk) throws ReflectiveOperationException {
            return (Boolean) isEmpty.invoke(chunk);
        }

        private Boolean canCompile(Object chunk) throws ReflectiveOperationException {
            return (Boolean) canCompile.invoke(chunk);
        }

        private Object lastCompileTask(Object chunk) throws IllegalAccessException {
            return lastCompileTask.get(chunk);
        }

        private Object lastCompileTaskResult(Object chunk) throws IllegalAccessException {
            return lastCompileTaskResult.get(chunk);
        }

        private int lastTimeRecorded(Object chunk) throws IllegalAccessException {
            return (Integer) lastTimeRecorded.get(chunk);
        }

        private int lastTimeEnqueued(Object chunk) throws IllegalAccessException {
            return (Integer) lastTimeEnqueued.get(chunk);
        }

        private int nonemptyVboParts(Object chunk) throws IllegalAccessException {
            return (Integer) nonemptyVboParts.get(chunk);
        }

        private int dispatcherQueueSize(Object dispatcher) throws IllegalAccessException {
            if (dispatcherQueue == null || !dispatcherQueue.getDeclaringClass().isInstance(dispatcher)) {
                return -1;
            }

            Object queue = dispatcherQueue.get(dispatcher);
            return queue instanceof Collection<?> collection ? collection.size() : -1;
        }

        private Object passFor(BlockRenderLayer layer) {
            return switch (layer) {
                case SOLID -> solid;
                case CUTOUT -> cutout;
                case CUTOUT_MIPPED -> cutoutMipped;
                case TRANSLUCENT -> translucent;
                default -> null;
            };
        }

        private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
            Class<?> current = type;
            while (current != null) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }

        private static Field findOptionalDispatcherQueueField() {
            try {
                Class<?> dispatcherImplClass = Class.forName("meldexun.nothirium.mc.renderer.chunk.RenderChunkDispatcher");
                Field field = findField(dispatcherImplClass, "taskQueue");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
    }
}
