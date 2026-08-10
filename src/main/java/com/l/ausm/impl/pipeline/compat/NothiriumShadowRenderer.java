package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
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
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
    private static final String[] WORLD_HEIGHT_METHODS = {"func_72800_K", "getHeight"};
    private static final int VANILLA_BLOCK_STRIDE = 28;
    private static final int POSITION_OFFSET = 0;
    private static final int COLOR_OFFSET = 12;
    private static final int TEX_COORD_OFFSET = 16;
    private static final int LIGHT_COORD_OFFSET = 24;
    private static final int MAX_SHADOW_COMPILES_PER_FRAME = 8;
    // The old 48/24/12 burst could enqueue 84 terrain rebuilds in a single
    // render frame. Keep the near solid terrain responsive, but leave enough
    // render-thread time for the shader pipeline and Bloom composite.
    private static final int MAX_MAIN_TERRAIN_SOLID_COMPILES_PER_FRAME = 3;
    private static final int MAX_MAIN_TERRAIN_CUTOUT_COMPILES_PER_FRAME = 1;
    private static final int MAX_MAIN_TERRAIN_TRANSLUCENT_COMPILES_PER_FRAME = 1;
    /** Shadow selection can contain every loaded section (7k+ in large
     * worlds). Scan a bounded rotating window per layer instead of doing the
     * full provider walk every frame; all sections remain eligible. */
    private static final int MAX_MAIN_TERRAIN_COMPILE_SCAN_PER_LAYER = 192;
    /** A Nothirium dispatcher update drains its entire render-thread queue.
     * Keep each frame's VBO work bounded instead of converting a completed
     * worker burst into a multi-millisecond render-thread hitch. */
    private static final int MAX_UPLOAD_TASKS_PER_DRAIN = 2;
    private static final int MAX_PENDING_SHADOW_COMPILES = 64;
    private static final int MAX_CHUNK_REFRESH_COMPILES = 16;
    /** A queued shader refresh can contain eight columns. Limit its direct
     * Nothirium submissions as one batch so it cannot flood every worker at
     * once; marked sections remain eligible for the normal dirty-chunk path. */
    private static final int MAX_QUEUED_CHUNK_REFRESH_COMPILES_PER_BATCH = 32;
    private static final int MAX_CHUNK_REFRESH_AUDIT_LOGS = 0;
    private static final int MAX_VISIBLE_TRANSLUCENT_DIAG_LOGS = 0;
    private static final int MAX_VISIBLE_TERRAIN_FAILURE_LOGS = 0;
    private static final int MAX_VISIBLE_NON_SOLID_TERRAIN_FAILURE_LOGS = 0;
    private static final int MAX_VISIBLE_TERRAIN_DRAW_PROBE_LOGS = 0;
    private static final int MAX_LILY_SHADOW_VERTEX_SCAN_CALLS = 0;
    private static final int MAX_LILY_SHADOW_VERTEX_PROBE_LOGS = 0;
    private static final int LILY_SHADOW_VERTEX_SCAN_BYTES = 32 * 1024;
    private static final int MAX_EMPTY_LIST_AUDIT_LOGS = 0;
    private static final int MAX_SHADERED_MAIN_LIST_PROBE_LOGS = 0;
    private static final int MAX_SHADERED_PROVIDER_STATE_PROBE_LOGS = 0;
    private static final int MAX_SHADERED_COMPILE_GATE_PROBE_LOGS = 0;
    private static final int MAX_SHADERED_COMPILE_CANDIDATE_PROBE_LOGS = 0;
    /* Several world/Bloom paths request upload draining in one render frame.
       Nothirium's dispatcher update is expensive enough to pace explicitly. */
    private static final long MIN_UPLOAD_DRAIN_INTERVAL_NANOS = 2_000_000L;
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
    private boolean uploadNonEmptyLogged;
    private int uploadAuditAttempts;
    private int compileAuditAttempts;
    private int mainCompileAuditAttempts;
    private int chunkRefreshAuditAttempts;
    private int visibleTranslucentAuditAttempts;
    private int visibleTerrainFailureAttempts;
    private int visibleNonSolidTerrainFailureAttempts;
    private int visibleTerrainDrawProbeAttempts;
    private int lilyShadowVertexScanCalls;
    private int lilyShadowVertexProbeLogs;
    private int lilyShadowGateProbeLogs;
    private int shaderedMainListProbeAttempts;
    private int shaderedProviderStateProbeAttempts;
    private int shaderedCompileGateProbeAttempts;
    private int shaderedCompileCandidateProbeAttempts;
    private long lastUploadDrainNanos;
    private final ByteBuffer visibleTerrainVertexProbe = BufferUtils.createByteBuffer(128);
    private final ByteBuffer lilyShadowVertexProbe = BufferUtils.createByteBuffer(LILY_SHADOW_VERTEX_SCAN_BYTES);
    private final FloatBuffer visibleTerrainMatrixProbe = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer visibleTerrainProjectionProbe = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer visibleTerrainUniformProbe = BufferUtils.createFloatBuffer(16);
    private final Map<Object, Long> mainTerrainCompileAttempts = new IdentityHashMap<>();
    private final Map<BlockRenderLayer, Integer> mainTerrainCompileScanCursors = new EnumMap<>(BlockRenderLayer.class);
    private final Map<Integer, Integer> chunkOffsetUniformLocations = new HashMap<>();
    private final Map<Long, List<Object>> queuedChunkRefreshColumns = new HashMap<>();
    private final FloatBuffer shadowSelectionModelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer shadowSelectionProjection = BufferUtils.createFloatBuffer(16);
    private final float[] shadowSelectionModelViewValues = new float[16];
    private final float[] shadowSelectionProjectionValues = new float[16];
    /** Reused only within one shadow pass. This removes one candidate object
     * for every selected section and the two matrix-array allocations from the
     * render thread's per-frame selection path. */
    private Object[] shadowSelectionChunks = new Object[0];
    private double[] shadowSelectionDistances = new double[0];
    private int shadowSelectionScratchCount;
    /**
     * The provider grid and the sun matrix change much more slowly than the
     * shadow pass itself. Keep the last conservative selection separately from
     * the per-pass scratch arrays so a small camera movement can reuse it.
     */
    private static final double SHADOW_SELECTION_REUSE_DISTANCE_SQUARED = 0.25D;
    private Object[] cachedShadowSelectionChunks = new Object[0];
    private int cachedShadowSelectionCount;
    private Reflection cachedShadowSelectionReflection;
    private Object cachedShadowSelectionProvider;
    private Object[] cachedShadowSelectionSourceChunks;
    private double cachedShadowSelectionCameraX;
    private double cachedShadowSelectionCameraY;
    private double cachedShadowSelectionCameraZ;
    private int cachedShadowSelectionCameraChunkX = Integer.MIN_VALUE;
    private int cachedShadowSelectionCameraChunkY = Integer.MIN_VALUE;
    private int cachedShadowSelectionCameraChunkZ = Integer.MIN_VALUE;
    private double cachedShadowSelectionMaxDistance;
    private final Map<Object, ChunkOrigin> shadowChunkOrigins = new IdentityHashMap<>();
    private ShadowSelection shadowSelection;
    private boolean shadowSelectionActive;
    private boolean queuedChunkRefreshBatchActive;
    private Object queuedChunkRefreshProvider;
    private Object[] queuedChunkRefreshSourceChunks;
    private int queuedChunkRefreshCompileCount;
    private Object mainTerrainCompileScanProvider;
    private Object[] mainTerrainCompileScanSourceChunks;
    private static int visibleTranslucentStateLogs;

    public static boolean isAvailable() {
        return reflection() != null;
    }

    public void resetPipelineProgramState() {
        chunkOffsetUniformLocations.clear();
        mainTerrainCompileAttempts.clear();
        mainTerrainCompileScanCursors.clear();
        mainTerrainCompileScanProvider = null;
        mainTerrainCompileScanSourceChunks = null;
        shadowSelection = null;
        shadowSelectionActive = false;
        clearShadowSelectionScratch();
        clearCachedShadowSelection();
        shadowChunkOrigins.clear();
        endQueuedChunkRefreshBatch();
    }

    /**
     * Builds one light-space provider selection for the entire shadow map.
     * Nothirium has no shadow visibility list, so the old bridge sorted the
     * complete provider array once per terrain layer.  Reuse this exact
     * selection for solid/cutout/translucent and reject chunks outside the
     * current orthographic shadow frustum before any VBO work.
     */
    public void beginShadowSelection(double cameraX, double cameraY, double cameraZ, double maxDistance) {
        shadowSelection = null;
        shadowSelectionActive = false;
        clearShadowSelectionScratch();
        shadowChunkOrigins.clear();
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return;
        }

        try {
            Object provider = reflection.getProvider.invoke(null);
            if (provider == null) {
                return;
            }
            Object chunksObject = reflection.providerChunks.get(provider);
            if (!(chunksObject instanceof Object[] chunks) || chunks.length == 0) {
                return;
            }
            resetMainTerrainCompileScanCursors(provider, chunks);
            shadowSelectionActive = true;
            long vboGeneration = NothiriumVisibleTerrainCache.vboGeneration();
            if (canReuseShadowSelection(reflection, provider, chunks,
                    cameraX, cameraY, cameraZ, maxDistance)) {
                shadowSelection = new ShadowSelection(reflection, provider, chunks,
                        new ShadowSelectionChunkList(cachedShadowSelectionChunks, cachedShadowSelectionCount),
                        cameraX, cameraY, cameraZ, maxDistance);
                return;
            }

            shadowSelectionModelView.clear();
            shadowSelectionProjection.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, shadowSelectionModelView);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, shadowSelectionProjection);
            shadowSelectionModelView.get(shadowSelectionModelViewValues);
            shadowSelectionProjection.get(shadowSelectionProjectionValues);
            ensureShadowSelectionScratchCapacity(chunks.length);

            double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
            int selectedCount = 0;
            int nonNull = 0;
            int heightCulled = 0;
            int distanceCulled = 0;
            int frustumCulled = 0;
            int worldHeight = shadowWorldHeight();
            for (Object chunk : chunks) {
                if (chunk == null) {
                    continue;
                }
                nonNull++;
                ChunkOrigin origin = chunkOrigin(reflection, chunk);
                int chunkX = origin.x;
                int chunkY = origin.y;
                int chunkZ = origin.z;
                if (chunkY < 0 || chunkY >= worldHeight) {
                    heightCulled++;
                    continue;
                }
                double dx = chunkX + 8.0D - cameraX;
                double dy = chunkY + 8.0D - cameraY;
                double dz = chunkZ + 8.0D - cameraZ;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (maxDistanceSquared >= 0.0D && distanceSquared > maxDistanceSquared) {
                    distanceCulled++;
                    continue;
                }
                if (!intersectsShadowFrustum(shadowSelectionModelViewValues, shadowSelectionProjectionValues,
                        chunkX - cameraX, chunkY - cameraY, chunkZ - cameraZ)) {
                    frustumCulled++;
                    continue;
                }
                shadowSelectionChunks[selectedCount] = chunk;
                shadowSelectionDistances[selectedCount] = distanceSquared;
                selectedCount++;
            }
            sortShadowSelectionCandidates(0, selectedCount - 1);
            cacheShadowSelection(reflection, provider, chunks, cameraX, cameraY, cameraZ,
                    maxDistance, selectedCount);
            clearShadowSelectionScratch();
            List<Object> ordered = new ShadowSelectionChunkList(cachedShadowSelectionChunks, selectedCount);
            shadowSelection = new ShadowSelection(reflection, provider, chunks, ordered,
                    cameraX, cameraY, cameraZ, maxDistance);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            shadowSelection = null;
            shadowSelectionActive = false;
            clearShadowSelectionScratch();
            shadowChunkOrigins.clear();
            warnOnce(exception);
        }
    }

    public void endShadowSelection() {
        shadowSelection = null;
        shadowSelectionActive = false;
        clearShadowSelectionScratch();
        shadowChunkOrigins.clear();
    }

    /**
     * Cheap pre-clear coverage gate for a replacement shadow map. The selected
     * list is nearest-first, so healthy frames normally stop after a few dozen
     * VBO checks; a sparse publication frame is rejected without destroying
     * the last complete shadow textures.
     */
    public int countRenderableShadowLayer(BlockRenderLayer layer,
                                          double cameraX, double cameraY, double cameraZ,
                                          double maxDistance, int limit) {
        if (limit <= 0) {
            return 0;
        }
        Reflection reflection = reflection();
        ShadowSelection selection = shadowSelection;
        if (disabled || reflection == null || selection == null
                || selection.cameraX != cameraX
                || selection.cameraY != cameraY
                || selection.cameraZ != cameraZ
                || maxDistance > selection.maxDistance) {
            return -1;
        }
        Object pass = reflection.passFor(layer);
        if (pass == null) {
            return 0;
        }

        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        int ready = 0;
        try {
            for (Object chunk : selection.chunks) {
                if (chunk == null) {
                    continue;
                }
                if (maxDistanceSquared >= 0.0D && maxDistance < selection.maxDistance) {
                    ChunkOrigin origin = chunkOrigin(reflection, chunk);
                    double dx = origin.x + 8.0D - cameraX;
                    double dy = origin.y + 8.0D - cameraY;
                    double dz = origin.z + 8.0D - cameraZ;
                    if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                        continue;
                    }
                }
                Object part = reflection.getVboPart(chunk, pass);
                if (part == null || !reflection.isValid(part)) {
                    continue;
                }
                int count = reflection.getCount(part);
                int vbo = reflection.getVbo(part);
                if (count <= 0 || vbo <= 0 || vertexStride(reflection.getSize(part), count) <= 0) {
                    continue;
                }
                ready++;
                if (ready >= limit) {
                    break;
                }
            }
            return ready;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce(exception);
            return -1;
        }
    }

    private boolean canReuseShadowSelection(Reflection reflection, Object provider, Object[] chunks,
                                            double cameraX, double cameraY, double cameraZ,
                                            double maxDistance) {
        if (cachedShadowSelectionReflection != reflection
                || cachedShadowSelectionProvider != provider
                || cachedShadowSelectionSourceChunks != chunks
                || cachedShadowSelectionMaxDistance != maxDistance
                || cachedShadowSelectionCameraChunkX != cameraChunkCoordinate(cameraX)
                || cachedShadowSelectionCameraChunkY != cameraChunkCoordinate(cameraY)
                || cachedShadowSelectionCameraChunkZ != cameraChunkCoordinate(cameraZ)) {
            return false;
        }
        double dx = cameraX - cachedShadowSelectionCameraX;
        double dy = cameraY - cachedShadowSelectionCameraY;
        double dz = cameraZ - cachedShadowSelectionCameraZ;
        return dx * dx + dy * dy + dz * dz <= SHADOW_SELECTION_REUSE_DISTANCE_SQUARED;
    }

    private void cacheShadowSelection(Reflection reflection, Object provider, Object[] chunks,
                                      double cameraX, double cameraY, double cameraZ,
                                      double maxDistance, int selectedCount) {
        if (cachedShadowSelectionChunks.length < selectedCount) {
            cachedShadowSelectionChunks = new Object[selectedCount];
        }
        System.arraycopy(shadowSelectionChunks, 0, cachedShadowSelectionChunks, 0, selectedCount);
        for (int index = selectedCount; index < cachedShadowSelectionCount; index++) {
            cachedShadowSelectionChunks[index] = null;
        }
        cachedShadowSelectionCount = selectedCount;
        cachedShadowSelectionReflection = reflection;
        cachedShadowSelectionProvider = provider;
        cachedShadowSelectionSourceChunks = chunks;
        cachedShadowSelectionCameraX = cameraX;
        cachedShadowSelectionCameraY = cameraY;
        cachedShadowSelectionCameraZ = cameraZ;
        cachedShadowSelectionCameraChunkX = cameraChunkCoordinate(cameraX);
        cachedShadowSelectionCameraChunkY = cameraChunkCoordinate(cameraY);
        cachedShadowSelectionCameraChunkZ = cameraChunkCoordinate(cameraZ);
        cachedShadowSelectionMaxDistance = maxDistance;
    }

    private void clearCachedShadowSelection() {
        for (int index = 0; index < cachedShadowSelectionCount; index++) {
            cachedShadowSelectionChunks[index] = null;
        }
        cachedShadowSelectionCount = 0;
        cachedShadowSelectionReflection = null;
        cachedShadowSelectionProvider = null;
        cachedShadowSelectionSourceChunks = null;
        cachedShadowSelectionCameraChunkX = Integer.MIN_VALUE;
        cachedShadowSelectionCameraChunkY = Integer.MIN_VALUE;
        cachedShadowSelectionCameraChunkZ = Integer.MIN_VALUE;
    }

    private static int cameraChunkCoordinate(double coordinate) {
        return (int) Math.floor(coordinate / 16.0D);
    }

    private static int shadowWorldHeight() {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        WorldClient world = minecraft != null ? MinecraftReflectionCompat.world(minecraft) : null;
        if (world == null) {
            return 256;
        }
        return Math.max(16, MinecraftReflectionCompat.callInt(
                world,
                WORLD_HEIGHT_METHODS,
                MinecraftReflectionCompat.NO_PARAMETERS,
                256));
    }

    private void ensureShadowSelectionScratchCapacity(int capacity) {
        if (shadowSelectionChunks.length >= capacity) {
            return;
        }
        shadowSelectionChunks = new Object[capacity];
        shadowSelectionDistances = new double[capacity];
    }

    private void clearShadowSelectionScratch() {
        for (int index = 0; index < shadowSelectionScratchCount; index++) {
            shadowSelectionChunks[index] = null;
        }
        shadowSelectionScratchCount = 0;
    }

    /** Sort parallel, allocation-free chunk/distance arrays nearest-first. */
    private void sortShadowSelectionCandidates(int low, int high) {
        while (low < high) {
            int left = low;
            int right = high;
            double pivot = shadowSelectionDistances[(low + high) >>> 1];
            while (left <= right) {
                while (shadowSelectionDistances[left] < pivot) {
                    left++;
                }
                while (shadowSelectionDistances[right] > pivot) {
                    right--;
                }
                if (left <= right) {
                    double distance = shadowSelectionDistances[left];
                    shadowSelectionDistances[left] = shadowSelectionDistances[right];
                    shadowSelectionDistances[right] = distance;
                    Object chunk = shadowSelectionChunks[left];
                    shadowSelectionChunks[left] = shadowSelectionChunks[right];
                    shadowSelectionChunks[right] = chunk;
                    left++;
                    right--;
                }
            }
            // Recurse into the smaller partition so even strongly ordered
            // distance sets keep stack depth logarithmic.
            if (right - low < high - left) {
                if (low < right) {
                    sortShadowSelectionCandidates(low, right);
                }
                low = left;
            } else {
                if (left < high) {
                    sortShadowSelectionCandidates(left, high);
                }
                high = right;
            }
        }
    }

    /** Coalesces one render-thread refresh batch into a single provider scan.
     * Render chunks can move between batches, so the index is never retained. */
    public void beginQueuedChunkRefreshBatch() {
        queuedChunkRefreshBatchActive = true;
        queuedChunkRefreshCompileCount = 0;
        queuedChunkRefreshProvider = null;
        queuedChunkRefreshSourceChunks = null;
        queuedChunkRefreshColumns.clear();
    }

    public void endQueuedChunkRefreshBatch() {
        queuedChunkRefreshBatchActive = false;
        queuedChunkRefreshCompileCount = 0;
        queuedChunkRefreshProvider = null;
        queuedChunkRefreshSourceChunks = null;
        queuedChunkRefreshColumns.clear();
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

            long now = System.nanoTime();
            if (now - lastUploadDrainNanos < MIN_UPLOAD_DRAIN_INTERVAL_NANOS) {
                return;
            }
            lastUploadDrainNanos = now;
            drainQueuedUploads(reflection, dispatcher);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
        }
    }

    /**
     * Nothirium's public update() method runs every queued render-thread task.
     * Its concrete dispatcher exposes the queue, so consume a small bounded
     * prefix when available. Keep the public method as a compatibility
     * fallback for another dispatcher implementation.
     */
    private static void drainQueuedUploads(Reflection reflection, Object dispatcher)
            throws ReflectiveOperationException {
        if (reflection.drainDispatcherQueue(dispatcher, MAX_UPLOAD_TASKS_PER_DRAIN) < 0) {
            reflection.dispatcherUpdate.invoke(dispatcher);
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

            Iterable<?> refreshChunks = queuedChunkRefreshBatchActive
                    ? queuedChunkRefreshColumn(reflection, provider, chunks, chunkX, chunkZ)
                    : Arrays.asList(chunks);

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
            for (Object chunk : refreshChunks) {
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
                    // Releasing buffers cancels this task. Its existing VBO
                    // remains the only valid terrain until that task uploads,
                    // so never invalidate it merely because another refresh
                    // request arrived in the same movement burst.
                    continue;
                }

                if (renderer == null || dispatcher == null) {
                    noDispatcher++;
                    continue;
                }
                if (!Boolean.TRUE.equals(reflection.canCompile(chunk))) {
                    cannotCompile++;
                    continue;
                }
                canCompile++;
                if (scheduled >= MAX_CHUNK_REFRESH_COMPILES
                        || (queuedChunkRefreshBatchActive
                        && queuedChunkRefreshCompileCount >= MAX_QUEUED_CHUNK_REFRESH_COMPILES_PER_BATCH)) {
                    deferred++;
                    continue;
                }

                // Nothirium's compileAsync keeps the previous VBO valid until
                // the accepted task publishes its replacement. Do the mutable
                // transition only after all admission checks; P39 previously
                // released every matching VBO before the shared batch budget,
                // leaving deferred movement columns invisible.
                reflection.markDirty.invoke(chunk);
                marked++;
                reflection.compileAsync.invoke(chunk, renderer, dispatcher);
                scheduled++;
                if (queuedChunkRefreshBatchActive) {
                    queuedChunkRefreshCompileCount++;
                }
            }

            if (scheduled > 0 && dispatcher != null) {
                drainQueuedUploads(reflection, dispatcher);
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

    private List<Object> queuedChunkRefreshColumn(Reflection reflection, Object provider, Object[] chunks,
                                                   int chunkX, int chunkZ) throws ReflectiveOperationException {
        if (queuedChunkRefreshProvider != provider || queuedChunkRefreshSourceChunks != chunks) {
            queuedChunkRefreshColumns.clear();
            queuedChunkRefreshProvider = provider;
            queuedChunkRefreshSourceChunks = chunks;
            for (Object chunk : chunks) {
                if (chunk == null) {
                    continue;
                }
                long key = chunkColumnKey(reflection.getX(chunk) >> 4, reflection.getZ(chunk) >> 4);
                List<Object> column = queuedChunkRefreshColumns.get(key);
                if (column == null) {
                    column = new ArrayList<>();
                    queuedChunkRefreshColumns.put(key, column);
                }
                column.add(chunk);
            }
        }
        List<Object> column = queuedChunkRefreshColumns.get(chunkColumnKey(chunkX, chunkZ));
        return column != null ? column : java.util.Collections.emptyList();
    }

    private static long chunkColumnKey(int chunkX, int chunkZ) {
        // Long.hashCode uses upper ^ lower bits. A plain packed X/Z key thus
        // collapses every diagonal (x ^ z) into the same HashMap bucket and
        // turns a moving refresh batch into tree lookups. Mix the packed key
        // before boxing it; this remains a one-to-one key transformation.
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
        key ^= key >>> 33;
        key *= 0xff51afd7ed558ccdL;
        key ^= key >>> 33;
        key *= 0xc4ceb9fe1a85ec53L;
        return key ^ (key >>> 33);
    }

    public int renderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance) {
        return renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, 0, (short) 0);
    }

    public int renderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance,
                           int fallbackBlockEntityId, short fallbackRenderType) {
        return renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, false, true, false,
                fallbackBlockEntityId, fallbackRenderType, false);
    }

    public int renderLayerRequiringPipelineStride(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                  double maxDistance) {
        return renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, false, true, false, 0, (short) 0, true);
    }

    public int renderLayerSchedulingCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance) {
        return renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, true, true, false, 0, (short) 0, false);
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
            List<?> candidates = selectedShadowChunks(reflection, provider, chunks, cameraX, cameraY, cameraZ, maxDistance);
            if (candidates == null) {
                candidates = providerChunksInRange(reflection, chunks, cameraX, cameraY, cameraZ, maxDistance);
            }
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

    public int renderVisibleLayerAllowingVanillaStride(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                       int fallbackBlockEntityId, short fallbackRenderType,
                                                       double maxDistance) {
        return renderVisibleLayer(layer, cameraX, cameraY, cameraZ, fallbackBlockEntityId, fallbackRenderType, maxDistance, false);
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
            if (PipelineContext.getInstance().isPipelineActive()
                    && stats.total == 0
                    && shaderedProviderStateProbeAttempts < MAX_SHADERED_PROVIDER_STATE_PROBE_LOGS) {
                shaderedProviderStateProbeAttempts++;
                auditProviderState(reflection, renderer, chunksByPass, layer, cameraX, cameraY, cameraZ,
                        "shadered-main", shaderedProviderStateProbeAttempts);
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

    private void auditProviderState(Reflection reflection, Object renderer, Object chunksByPass,
                                    BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                    String route, int call) {
        try {
            Object provider = reflection.getProvider.invoke(null);
            Object chunksObject = provider == null ? null : reflection.providerChunks.get(provider);
            int providerTotal = chunksObject instanceof Object[] array ? array.length : -1;
            int providerNonNull = 0;
            int providerEmpty = 0;
            int providerNonEmpty = 0;
            int providerDirty = 0;
            int providerNonemptyMask = 0;
            int providerRenderable = 0;
            int providerSampled = 0;
            String firstProvider = "n/a";
            String firstProviderState = "n/a";
            Object pass = reflection.passFor(layer);
            if (chunksObject instanceof Object[] array) {
                for (Object chunk : array) {
                    if (chunk == null) {
                        continue;
                    }
                    providerNonNull++;
                    if (providerSampled < 512) {
                        providerSampled++;
                        boolean empty = reflection.isChunkEmpty(chunk);
                        boolean dirty = reflection.isChunkDirty(chunk);
                        int nonemptyMask = reflection.nonemptyVboParts(chunk);
                        if (empty) {
                            providerEmpty++;
                        } else {
                            providerNonEmpty++;
                        }
                        if (dirty) {
                            providerDirty++;
                        }
                        if (nonemptyMask != 0) {
                            providerNonemptyMask++;
                        }
                        if (firstProvider.equals("n/a")) {
                            firstProvider = reflection.getX(chunk) + "," + reflection.getY(chunk) + "," + reflection.getZ(chunk);
                            Object taskResult = reflection.lastCompileTaskResult(chunk);
                            firstProviderState = "empty=" + empty
                                    + " dirty=" + dirty
                                    + " mask=" + nonemptyMask
                                    + " future=" + DrawStats.futureState(taskResult);
                        }
                        if (pass != null && providerChunkHasRenderablePart(reflection, pass, chunk, false)) {
                            providerRenderable++;
                        }
                    }
                }
            }
            String rendererLists = rendererPassSizes(reflection, chunksByPass);
            String nearestGate = nearestCompileGate(reflection, provider, chunksObject, cameraX, cameraY, cameraZ);
            Object dispatcher = reflection.getTaskDispatcher.invoke(null);
            int dispatcherQueue = dispatcher == null ? -1 : reflection.dispatcherQueueSize(dispatcher);
            MainMod.LOGGER.info(
                    "[AUSMNothiriumProviderStateProbe] route={} call={} layer={} camera={}/{}/{} providerTotal={} providerNonNull={} sampled={} empty={} nonEmpty={} dirty={} nonemptyMask={} renderable={} rendererLists={} dispatcherQueue={} nearestGate={} firstProvider={} firstState={}",
                    route,
                    call,
                    layer,
                    cameraX,
                    cameraY,
                    cameraZ,
                    providerTotal,
                    providerNonNull,
                    providerSampled,
                    providerEmpty,
                    providerNonEmpty,
                    providerDirty,
                    providerNonemptyMask,
                    providerRenderable,
                    rendererLists,
                    dispatcherQueue,
                    nearestGate,
                    firstProvider,
                    firstProviderState);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MainMod.LOGGER.info("[AUSMNothiriumProviderStateProbe] route={} call={} failed={}",
                    route, call, exception.getClass().getName());
        }
    }

    private String nearestCompileGate(Reflection reflection, Object provider, Object chunksObject,
                                      double cameraX, double cameraY, double cameraZ)
            throws ReflectiveOperationException {
        if (shaderedCompileGateProbeAttempts >= MAX_SHADERED_COMPILE_GATE_PROBE_LOGS
                || provider == null || !(chunksObject instanceof Object[] chunks)) {
            return "disabled";
        }

        Object nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Object chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            int x = reflection.getX(chunk);
            int y = reflection.getY(chunk);
            int z = reflection.getZ(chunk);
            double dx = x + 8.0D - cameraX;
            double dy = y + 8.0D - cameraY;
            double dz = z + 8.0D - cameraZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = chunk;
            }
        }
        if (nearest == null) {
            return "none";
        }

        shaderedCompileGateProbeAttempts++;
        int sectionX = reflection.getSectionX(nearest);
        int sectionY = reflection.getSectionY(nearest);
        int sectionZ = reflection.getSectionZ(nearest);
        int loaded = 0;
        StringBuilder neighborhood = new StringBuilder();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                boolean isLoaded = reflection.worldUtilIsChunkLoaded.invoke(null, sectionX + dx, sectionZ + dz) instanceof Boolean value && value;
                if (isLoaded) {
                    loaded++;
                }
                if (neighborhood.length() > 0) {
                    neighborhood.append('/');
                }
                neighborhood.append(isLoaded ? '1' : '0');
            }
        }
        Object future = reflection.lastCompileTaskResult(nearest);
        return "chunk=" + reflection.getX(nearest) + "," + reflection.getY(nearest) + "," + reflection.getZ(nearest)
                + " section=" + sectionX + "," + sectionY + "," + sectionZ
                + " loaded3x3=" + loaded + "/9(" + neighborhood + ")"
                + " dirty=" + reflection.isChunkDirty(nearest)
                + " empty=" + reflection.isChunkEmpty(nearest)
                + " canCompile=" + reflection.canCompile(nearest)
                + " task=" + (reflection.lastCompileTask(nearest) != null)
                + " future=" + DrawStats.futureState(future)
                + " recorded=" + reflection.lastTimeRecorded(nearest)
                + " enqueued=" + reflection.lastTimeEnqueued(nearest);
    }

    private String rendererPassSizes(Reflection reflection, Object chunksByPass) throws ReflectiveOperationException {
        return "solid=" + listSize(reflection, chunksByPass, reflection.solid)
                + ",mipped=" + listSize(reflection, chunksByPass, reflection.cutoutMipped)
                + ",cutout=" + listSize(reflection, chunksByPass, reflection.cutout)
                + ",translucent=" + listSize(reflection, chunksByPass, reflection.translucent)
                + ",bloom=" + listSize(reflection, chunksByPass, reflection.bloom);
    }

    private int listSize(Reflection reflection, Object chunksByPass, Object pass) throws ReflectiveOperationException {
        if (chunksByPass == null || pass == null) {
            return -1;
        }
        Object value = reflection.enumMapGet.invoke(chunksByPass, pass);
        return value instanceof Collection<?> collection ? collection.size() : -1;
    }

    private int renderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance,
                            boolean scheduleCompiles, boolean audit, boolean visibleOnly,
                            int fallbackBlockEntityId, short fallbackRenderType, boolean requirePipelineStride) {
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
                                maxDistance, false, fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
                        if (audit) {
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
                    fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
            if (audit) {
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

            List<?> candidates = selectedShadowChunks(reflection, provider, chunks, cameraX, cameraY, cameraZ, maxDistance);
            if (candidates == null) {
                candidates = providerChunksInRange(reflection, chunks, cameraX, cameraY, cameraZ, maxDistance);
            }
            if (scheduleCompiles) {
                scheduleMissingLayerCompiles(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ, maxDistance);
            }
            DrawStats stats = drawChunksWithLayerState(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ,
                    maxDistance, false, fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
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
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        List<ProviderCandidate> filtered = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk == null) {
                continue;
            }

            ChunkOrigin origin = chunkOrigin(reflection, chunk);
            int chunkX = origin.x;
            int chunkY = origin.y;
            int chunkZ = origin.z;
            double dx = chunkX + 8.0D - cameraX;
            double dy = chunkY + 8.0D - cameraY;
            double dz = chunkZ + 8.0D - cameraZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (maxDistanceSquared < 0.0D || distanceSquared <= maxDistanceSquared) {
                filtered.add(new ProviderCandidate(chunk, distanceSquared));
            }
        }
        filtered.sort(Comparator.comparingDouble(candidate -> candidate.distanceSquared));
        List<Object> ordered = new ArrayList<>(filtered.size());
        for (ProviderCandidate candidate : filtered) {
            ordered.add(candidate.chunk);
        }
        return ordered;
    }

    private List<?> selectedShadowChunks(Reflection reflection, Object provider, Object[] chunks,
                                         double cameraX, double cameraY, double cameraZ, double maxDistance) {
        ShadowSelection selection = shadowSelection;
        if (selection == null
                || selection.reflection != reflection
                || selection.provider != provider
                || selection.sourceChunks != chunks
                || selection.cameraX != cameraX
                || selection.cameraY != cameraY
                || selection.cameraZ != cameraZ
                || maxDistance > selection.maxDistance) {
            return null;
        }
        return selection.chunks;
    }

    private void resetMainTerrainCompileScanCursors(Object provider, Object[] chunks) {
        if (mainTerrainCompileScanProvider == provider && mainTerrainCompileScanSourceChunks == chunks) {
            return;
        }
        mainTerrainCompileScanCursors.clear();
        mainTerrainCompileScanProvider = provider;
        mainTerrainCompileScanSourceChunks = chunks;
    }

    private Iterable<?> boundedMainTerrainCompileScan(BlockRenderLayer layer, Iterable<?> chunks) {
        ShadowSelection selection = shadowSelection;
        if (!shadowSelectionActive || selection == null || chunks != selection.chunks) {
            return chunks;
        }
        List<Object> source = selection.chunks;
        int size = source.size();
        if (size <= MAX_MAIN_TERRAIN_COMPILE_SCAN_PER_LAYER) {
            return source;
        }

        int count = Math.min(MAX_MAIN_TERRAIN_COMPILE_SCAN_PER_LAYER, size);
        int start = mainTerrainCompileScanCursors.getOrDefault(layer, 0);
        if (start < 0 || start >= size) {
            start = 0;
        }
        List<Object> window = new ArrayList<>(count);
        for (int offset = 0; offset < count; offset++) {
            window.add(source.get((start + offset) % size));
        }
        mainTerrainCompileScanCursors.put(layer, (start + count) % size);
        return window;
    }

    /** Chunk renderers are repositioned between shadow passes, but their
     * origins are stable throughout one pass. Reuse them across the three
     * terrain layers and compile admission without changing selection or draw
     * coverage. */
    private ChunkOrigin chunkOrigin(Reflection reflection, Object chunk) throws ReflectiveOperationException {
        if (chunk instanceof NothiriumShadowChunkAccess access) {
            return new ChunkOrigin(access.ausm$blockX(), access.ausm$blockY(), access.ausm$blockZ());
        }
        if (!shadowSelectionActive) {
            return new ChunkOrigin(reflection.getX(chunk), reflection.getY(chunk), reflection.getZ(chunk));
        }
        ChunkOrigin origin = shadowChunkOrigins.get(chunk);
        if (origin != null) {
            return origin;
        }
        origin = new ChunkOrigin(reflection.getX(chunk), reflection.getY(chunk), reflection.getZ(chunk));
        shadowChunkOrigins.put(chunk, origin);
        return origin;
    }

    /** Conservative homogeneous clip-space AABB test. The extra block margin
     * covers oversized model geometry while still rejecting sections which
     * cannot affect the current orthographic shadow map. */
    private static boolean intersectsShadowFrustum(float[] modelView, float[] projection,
                                                   double relativeX, double relativeY, double relativeZ) {
        boolean outsideLeft = true;
        boolean outsideRight = true;
        boolean outsideBottom = true;
        boolean outsideTop = true;
        boolean outsideNear = true;
        boolean outsideFar = true;
        final double margin = 1.125D;
        for (int corner = 0; corner < 8; corner++) {
            double x = relativeX + ((corner & 1) == 0 ? -margin : 16.0D + margin);
            double y = relativeY + ((corner & 2) == 0 ? -margin : 16.0D + margin);
            double z = relativeZ + ((corner & 4) == 0 ? -margin : 16.0D + margin);
            double eyeX = modelView[0] * x + modelView[4] * y + modelView[8] * z + modelView[12];
            double eyeY = modelView[1] * x + modelView[5] * y + modelView[9] * z + modelView[13];
            double eyeZ = modelView[2] * x + modelView[6] * y + modelView[10] * z + modelView[14];
            double eyeW = modelView[3] * x + modelView[7] * y + modelView[11] * z + modelView[15];
            double clipX = projection[0] * eyeX + projection[4] * eyeY + projection[8] * eyeZ + projection[12] * eyeW;
            double clipY = projection[1] * eyeX + projection[5] * eyeY + projection[9] * eyeZ + projection[13] * eyeW;
            double clipZ = projection[2] * eyeX + projection[6] * eyeY + projection[10] * eyeZ + projection[14] * eyeW;
            double clipW = projection[3] * eyeX + projection[7] * eyeY + projection[11] * eyeZ + projection[15] * eyeW;
            outsideLeft &= clipX < -clipW;
            outsideRight &= clipX > clipW;
            outsideBottom &= clipY < -clipW;
            outsideTop &= clipY > clipW;
            outsideNear &= clipZ < -clipW;
            outsideFar &= clipZ > clipW;
        }
        return !(outsideLeft || outsideRight || outsideBottom || outsideTop || outsideNear || outsideFar);
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

            ChunkOrigin origin = chunkOrigin(reflection, chunk);
            int chunkX = origin.x;
            int chunkY = origin.y;
            int chunkZ = origin.z;
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

            ChunkOrigin origin = chunkOrigin(reflection, chunk);
            int chunkX = origin.x;
            int chunkY = origin.y;
            int chunkZ = origin.z;
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
        Object part = reflection.getVboPart(chunk, pass);
        if (part == null || !reflection.isValid(part)) {
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

            ChunkOrigin origin = chunkOrigin(reflection, chunk);
            int chunkX = origin.x;
            int chunkY = origin.y;
            int chunkZ = origin.z;
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
        for (Object chunk : boundedMainTerrainCompileScan(layer, chunks)) {
            stats.total++;
            if (chunk == null) {
                stats.nullChunks++;
                continue;
            }

            ChunkOrigin origin = chunkOrigin(reflection, chunk);
            int chunkX = origin.x;
            int chunkY = origin.y;
            int chunkZ = origin.z;
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

            Object part = reflection.getVboPart(chunk, pass);
            boolean missingPart = part == null;
            boolean invalidPart = false;
            boolean emptyPart = false;
            if (part != null) {
                invalidPart = !reflection.isValid(part);
                if (!invalidPart) {
                    emptyPart = reflection.getCount(part) <= 0
                            || reflection.getVbo(part) <= 0;
                }
            }
            // A clean section with no VBO for this pass is an intentionally
            // empty layer, not work Nothirium needs rebuilt. Re-submitting it
            // once for each shadow layer was the movement-time compile storm.
            boolean dirty = reflection.isChunkDirty(chunk);
            if ((!dirty && !invalidPart) || (!missingPart && !invalidPart && !emptyPart)) {
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

            double dx = chunkX + 8.0D - cameraX;
            double dy = chunkY + 8.0D - cameraY;
            double dz = chunkZ + 8.0D - cameraZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            candidates.add(new CompileCandidate(chunk, distanceSquared, invalidPart));
        }

        candidates.sort((left, right) -> Double.compare(left.distanceSquared, right.distanceSquared));
        int budget = mainTerrainCompileBudget(layer);
        for (CompileCandidate candidate : candidates) {
            if (stats.scheduled >= budget) {
                break;
            }
            if (!Boolean.TRUE.equals(reflection.canCompile(candidate.chunk))) {
                stats.cannotCompile++;
                continue;
            }
            stats.canCompile++;

            // Preserve the last usable VBO until the admitted task replaces
            // it. An invalid layer will be replaced by compileAsync; clearing
            // a valid layer first only turns a throttled refresh into missing
            // terrain.
            reflection.markDirty.invoke(candidate.chunk);
            reflection.compileAsync.invoke(candidate.chunk, renderer, dispatcher);
            mainTerrainCompileAttempts.put(candidate.chunk, now);
            stats.scheduled++;
        }

        auditMainCompileStats(stats);
        auditCompileCandidates(reflection, pass, candidates, cameraX, cameraY, cameraZ, layer, stats);
        return stats.scheduled > 0 ? stats.scheduled : stats.running + stats.throttled + stats.cannotCompile;
    }

    private void auditCompileCandidates(Reflection reflection, Object pass, List<CompileCandidate> candidates,
                                        double cameraX, double cameraY, double cameraZ,
                                        BlockRenderLayer layer, CompileStats stats) throws ReflectiveOperationException {
        if (shaderedCompileCandidateProbeAttempts >= MAX_SHADERED_COMPILE_CANDIDATE_PROBE_LOGS
                || candidates.isEmpty()
                || (stats.scheduled > 0 && stats.cannotCompile == 0 && stats.running == 0)) {
            return;
        }

        shaderedCompileCandidateProbeAttempts++;
        StringBuilder details = new StringBuilder();
        int limit = Math.min(6, candidates.size());
        for (int index = 0; index < limit; index++) {
            Object chunk = candidates.get(index).chunk;
            int sectionX = reflection.getSectionX(chunk);
            int sectionZ = reflection.getSectionZ(chunk);
            int loaded = 0;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (reflection.worldUtilIsChunkLoaded.invoke(null, sectionX + dx, sectionZ + dz) instanceof Boolean value && value) {
                        loaded++;
                    }
                }
            }
            Object part = reflection.getVboPart(chunk, pass);
            String partState = part == null
                    ? "missing"
                    : "valid=" + reflection.isValid(part)
                    + ",count=" + reflection.getCount(part)
                    + ",vbo=" + reflection.getVbo(part);
            Object future = reflection.lastCompileTaskResult(chunk);
            if (details.length() > 0) {
                details.append(';');
            }
            details.append(index)
                    .append("@")
                    .append(reflection.getX(chunk)).append(',')
                    .append(reflection.getY(chunk)).append(',')
                    .append(reflection.getZ(chunk))
                    .append(" dist=").append(String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(candidates.get(index).distanceSquared)))
                    .append(" loaded3x3=").append(loaded).append("/9")
                    .append(" dirty=").append(reflection.isChunkDirty(chunk))
                    .append(" empty=").append(reflection.isChunkEmpty(chunk))
                    .append(" canCompile=").append(reflection.canCompile(chunk))
                    .append(" task=").append(reflection.lastCompileTask(chunk) != null)
                    .append(" future=").append(DrawStats.futureState(future))
                    .append(" part=").append(partState);
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumCompileCandidateProbe] call={} layer={} candidates={} scheduled={} running={} throttled={} cannotCompile={} camera={}/{}/{} details={}",
                shaderedCompileCandidateProbeAttempts,
                layer,
                candidates.size(),
                stats.scheduled,
                stats.running,
                stats.throttled,
                stats.cannotCompile,
                cameraX,
                cameraY,
                cameraZ,
                details);
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

    private static final class ChunkOrigin {
        private final int x;
        private final int y;
        private final int z;

        private ChunkOrigin(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
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

    /** Read-only view over the cached selection. It is replaced only after a
     * provider/grid change or a larger camera move. VBO uploads do not alter
     * section positions, and each draw resolves the current VBO part. */
    private static final class ShadowSelectionChunkList extends AbstractList<Object> {
        private final Object[] chunks;
        private final int size;

        private ShadowSelectionChunkList(Object[] chunks, int size) {
            this.chunks = chunks;
            this.size = size;
        }

        @Override
        public Object get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
            }
            return chunks[index];
        }

        @Override
        public int size() {
            return size;
        }
    }

    private static final class ShadowSelection {
        private final Reflection reflection;
        private final Object provider;
        private final Object[] sourceChunks;
        private final List<Object> chunks;
        private final double cameraX;
        private final double cameraY;
        private final double cameraZ;
        private final double maxDistance;

        private ShadowSelection(Reflection reflection, Object provider, Object[] sourceChunks, List<Object> chunks,
                                double cameraX, double cameraY, double cameraZ, double maxDistance) {
            this.reflection = reflection;
            this.provider = provider;
            this.sourceChunks = sourceChunks;
            this.chunks = chunks;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
            this.maxDistance = maxDistance;
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
        boolean externalTranslucentState = layer == BlockRenderLayer.TRANSLUCENT
                && PipelineContext.getInstance().isBloomTranslucentAttenuationPass();
        LayerGlState layerState = externalTranslucentState ? null : LayerGlState.prepare(layer);
        try {
            if (layer == BlockRenderLayer.TRANSLUCENT && !externalTranslucentState) {
                PipelineContext.getInstance().restoreActiveGbufferRenderState();
            }
            return drawChunks(layer, reflection, pass, chunks, cameraX, cameraY, cameraZ, maxDistance, collectState,
                    fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
        } finally {
            if (layerState != null) {
                layerState.restore();
            }
        }
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
        ShadowSelection activeSelection = shadowSelection;
        // beginShadowSelection has already applied this exact distance test to
        // its list. Avoid repeating three double-vector calculations for each
        // chunk across the solid and two cutout layers.
        boolean selectionAlreadyDistanceCulled = shadowSelectionActive
                && activeSelection != null
                && chunks == activeSelection.chunks
                && maxDistance == activeSelection.maxDistance;
        PipelineContext context = PipelineContext.getInstance();
        boolean disableCullForMainTerrain = context.shouldDisableNothiriumChunkCulling(layer);
        int shaderlessBloomDimension = context.shaderlessBloomExtractionDimensionId();
        boolean previousCull = false;
        int previousMatrixMode = -1;
        // A shadow terrain layer keeps one program bound for its complete
        // chunk loop. Querying it and resolving the same uniform for every
        // section was visible client-thread overhead in movement captures.
        int activeProgram = USE_CHUNK_OFFSET_UNIFORM
                ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                : 0;
        int activeChunkOffsetUniform = activeProgram > 0
                ? chunkOffsetUniformLocation(activeProgram)
                : -1;
        boolean useChunkOffsetUniform = activeChunkOffsetUniform >= 0;
        int activeDrawMode = context.drawModeForActiveProgram(GL11.GL_QUADS);

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

                ChunkOrigin origin = chunkOrigin(reflection, chunk);
                int chunkX = origin.x;
                int chunkY = origin.y;
                int chunkZ = origin.z;
                stats.captureFirstChunk(chunkX, chunkY, chunkZ);
                if (collectState) {
                    stats.captureState(reflection, chunk, chunkX, chunkY, chunkZ);
                }
                if (!selectionAlreadyDistanceCulled && maxDistanceSquared >= 0.0D) {
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

                Object part = reflection.getVboPart(chunk, pass);
                if (part == null) {
                    stats.missingPart++;
                    continue;
                }
                stats.partPresent++;
                if (!reflection.isValid(part)) {
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
                context.prepareShaderlessOptimizedBloomDraw();
                probeLilyShadowMaterial(context, layer, chunkX, chunkY, chunkZ, offset, size, stride, pipelineStride);
                if (useChunkOffsetUniform) {
                    GL20.glUniform3f(activeChunkOffsetUniform,
                            (float) (chunkX - cameraX),
                            (float) (chunkY - cameraY),
                            (float) (chunkZ - cameraZ));
                    DrawProbe drawProbe = captureVisibleTerrainDrawProbe(layer, chunkX, chunkY, chunkZ, cameraX, cameraY, cameraZ,
                            vbo, first, count, offset, size, stride, previousVboSize, pipelineStride);
                    int query = beginProbeQuery(drawProbe);
                    GL11.glDrawArrays(activeDrawMode, first, count);
                    finishVisibleTerrainDrawProbe(drawProbe, query);
                } else {
                    if (previousMatrixMode < 0) {
                        previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
                    }
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslated(chunkX - cameraX, chunkY - cameraY, chunkZ - cameraZ);
                        DrawProbe drawProbe = captureVisibleTerrainDrawProbe(layer, chunkX, chunkY, chunkZ, cameraX, cameraY, cameraZ,
                                vbo, first, count, offset, size, stride, previousVboSize, pipelineStride);
                        int query = beginProbeQuery(drawProbe);
                        GL11.glDrawArrays(activeDrawMode, first, count);
                        finishVisibleTerrainDrawProbe(drawProbe, query);
                    } finally {
                        GL11.glPopMatrix();
                    }
                }
                stats.drawn++;
            }
        } finally {
            // Shadow programs can be reused immediately for entities and
            // block entities. Never let the final terrain section's offset
            // leak into those draws.
            if (useChunkOffsetUniform) {
                GL20.glUniform3f(activeChunkOffsetUniform, 0.0F, 0.0F, 0.0F);
            }
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

            if (!Boolean.TRUE.equals(reflection.canCompile(chunk))) {
                continue;
            }

            // This is a forced stride repair, but still must retain a valid
            // old VBO until the replacement task has been accepted.
            reflection.markDirty.invoke(chunk);
            reflection.compileAsync.invoke(chunk, renderer, dispatcher);
            scheduled++;
        }

        if (scheduled > 0) {
            drainQueuedUploads(reflection, dispatcher);
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

    /**
     * The fertile lily pad shadow discard did not change the reported square.
     * Read only bounded shadow VBO spans to prove whether the Nothirium bridge
     * actually supplies its mapped material id to shadow.glsl.
     */
    private void probeLilyShadowMaterial(PipelineContext context, BlockRenderLayer layer,
                                         int chunkX, int chunkY, int chunkZ,
                                         int offset, int size, int stride, boolean pipelineStride) {
        boolean shadowActive = context != null && context.isShadowPassActive();
        boolean knownLilySection = context != null && context.isKnownLilyPadShadowProbeChunk(chunkX, chunkY, chunkZ);
        // The section key remains false even beside material-10489 terrain:
        // Nothirium's actual draw origins do not correspond to the compiled
        // BlockPos section key. Record every gate and scan the complete active
        // shadow CUTOUT route for the material itself; this is the final
        // evidence needed before touching lily geometry or lighting again.
        if (shadowActive && layer == BlockRenderLayer.CUTOUT && lilyShadowGateProbeLogs < MAX_LILY_SHADOW_VERTEX_PROBE_LOGS) {
            lilyShadowGateProbeLogs++;
            MainMod.LOGGER.info(
                    "[AUSMLilyShadowGateProbe] call={} known={} pipelineStride={} offset={} size={} stride={} chunk={}/{}/{} program={}",
                    lilyShadowGateProbeLogs,
                    knownLilySection,
                    pipelineStride,
                    offset,
                    size,
                    stride,
                    chunkX,
                    chunkY,
                    chunkZ,
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
            );
            context.forensicGlTrace("lily-shadow-vbo-gate", "known=" + knownLilySection
                    + ", pipelineStride=" + pipelineStride + ", chunk=" + chunkX + "/" + chunkY + "/" + chunkZ
                    + ", offset=" + offset + ", size=" + size + ", stride=" + stride);
        }
        if (context == null
                || !shadowActive
                || !pipelineStride
                || layer != BlockRenderLayer.CUTOUT
                || lilyShadowVertexScanCalls >= MAX_LILY_SHADOW_VERTEX_SCAN_CALLS
                || lilyShadowVertexProbeLogs >= MAX_LILY_SHADOW_VERTEX_PROBE_LOGS
                || stride < ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 2
                || offset < 0
                || size <= 0) {
            return;
        }

        lilyShadowVertexScanCalls++;
        int bytes = Math.min(size, lilyShadowVertexProbe.capacity());
        bytes -= bytes % stride;
        if (bytes <= 0) {
            return;
        }

        try {
            lilyShadowVertexProbe.clear();
            lilyShadowVertexProbe.limit(bytes);
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, offset, lilyShadowVertexProbe);
            for (int vertexOffset = 0; vertexOffset < bytes; vertexOffset += stride) {
                int material = lilyShadowVertexProbe.getShort(
                        vertexOffset + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET) & 0xFFFF;
                if (material != 10489) {
                    continue;
                }
                lilyShadowVertexProbeLogs++;
                MainMod.LOGGER.info(
                        "[AUSMLilyShadowMaterialProbe] hit={} scan={} layer={} chunk={}/{}/{} material={} renderType={} stride={} program={}",
                        lilyShadowVertexProbeLogs,
                        lilyShadowVertexScanCalls,
                        layer,
                        chunkX,
                        chunkY,
                        chunkZ,
                        material,
                        lilyShadowVertexProbe.getShort(vertexOffset + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 2) & 0xFFFF,
                        stride,
                        GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                );
                context.forensicGlTrace("lily-shadow-vbo-hit", "scan=" + lilyShadowVertexScanCalls + ", chunk=" + chunkX + "/" + chunkY + "/" + chunkZ + ", material=" + material + ", stride=" + stride);
                return;
            }
            if (lilyShadowVertexProbeLogs++ < MAX_LILY_SHADOW_VERTEX_PROBE_LOGS) {
                MainMod.LOGGER.info(
                        "[AUSMLilyShadowMaterialProbe] miss={} scan={} layer={} chunk={}/{}/{} stride={} program={}",
                        lilyShadowVertexProbeLogs,
                        lilyShadowVertexScanCalls,
                        layer,
                        chunkX,
                        chunkY,
                        chunkZ,
                        stride,
                        GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                );
                context.forensicGlTrace("lily-shadow-vbo-miss", "scan=" + lilyShadowVertexScanCalls + ", chunk=" + chunkX + "/" + chunkY + "/" + chunkZ + ", stride=" + stride);
            }
        } catch (RuntimeException | LinkageError exception) {
            if (lilyShadowVertexProbeLogs++ < MAX_LILY_SHADOW_VERTEX_PROBE_LOGS) {
                MainMod.LOGGER.info("[AUSMLilyShadowMaterialProbe] scan={} failed={}",
                        lilyShadowVertexScanCalls, exception.getClass().getSimpleName());
            }
        } finally {
            lilyShadowVertexProbe.clear();
        }
    }

    private DrawProbe captureVisibleTerrainDrawProbe(BlockRenderLayer layer, int chunkX, int chunkY, int chunkZ,
                                                     double cameraX, double cameraY, double cameraZ,
                                                     int vbo, int first, int count, int offset, int size,
                                                     int stride, int vboSize, boolean pipelineStride) {
        if (!PipelineContext.getInstance().isPipelineActive()
                || visibleTerrainDrawProbeAttempts >= MAX_VISIBLE_TERRAIN_DRAW_PROBE_LOGS
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
        if (stride >= ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 8) {
            builder.append(",mcEntity=")
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET) & 0xFFFF)
                    .append('/')
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 2) & 0xFFFF)
                    .append('/')
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 4) & 0xFFFF)
                    .append('/')
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 6) & 0xFFFF);
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
        if (layer != BlockRenderLayer.TRANSLUCENT
                || visibleTranslucentAuditAttempts >= MAX_VISIBLE_TRANSLUCENT_DIAG_LOGS) {
            return;
        }

        visibleTranslucentAuditAttempts++;
        MainMod.LOGGER.info(
                "[AUSMNothiriumTranslucent] call={} stage={} total={} null={} within={} distCull={} missingPart={} part={} invalidPart={} valid={} emptyCount={} count={} badVbo={} vbo={} badStride={} unsupportedStride={} rangeSkip={} drawn={} fallbackBlock={} fallbackRenderType={} firstChunk={} firstPart={} gl={}",
                visibleTranslucentAuditAttempts,
                stage,
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
        if (mainCompileAuditAttempts >= 8) {
            return;
        }
        if (stats.total <= 0 || (stats.scheduled <= 0 && stats.running <= 0 && stats.dirty <= 0)) {
            return;
        }
        mainCompileAuditAttempts++;
        MainMod.LOGGER.info(
                "[AUSMNothiriumMainCompileProbe] attempt={} total={} within={} distCull={} dirty={} clean={} canCompile={} cannotCompile={} running={} scheduled={} throttled={} firstChunk={}",
                mainCompileAuditAttempts,
                stats.total,
                stats.withinDistance,
                stats.distanceCulled,
                stats.dirty,
                stats.clean,
                stats.canCompile,
                stats.cannotCompile,
                stats.running,
                stats.scheduled,
                stats.throttled,
                stats.firstChunk
        );
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
        private final Method enumMapGet;
        private final Method renderedChunks;
        private final Method renderedSections;
        private final Method renderedSectionsAll;
        private final MethodHandle getVboPart;
        private final MethodHandle getVbo;
        private final MethodHandle getFirst;
        private final MethodHandle getCount;
        private final MethodHandle getOffset;
        private final MethodHandle getSize;
        private final MethodHandle isValid;
        private final Method isDirty;
        private final Method isEmpty;
        private final Method markDirty;
        private final Method releaseBuffers;
        private final Method canCompile;
        private final Method compileAsync;
        private final Method worldUtilIsChunkLoaded;
        private final MethodHandle getX;
        private final MethodHandle getY;
        private final MethodHandle getZ;
        private final MethodHandle getSectionX;
        private final MethodHandle getSectionY;
        private final MethodHandle getSectionZ;
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
        private final Object bloom;

        private Reflection(Method getRenderer, Method getProvider, Method getTaskDispatcher, Method dispatcherUpdate,
                           Method enumMapGet, Method renderedChunks, Method renderedSections,
                           Method renderedSectionsAll, MethodHandle getVboPart, MethodHandle getVbo, MethodHandle getFirst,
                           MethodHandle getCount, MethodHandle getOffset, MethodHandle getSize, MethodHandle isValid, Method isDirty,
                           Method isEmpty, Method markDirty, Method releaseBuffers, Method canCompile,
                           Method compileAsync, Method worldUtilIsChunkLoaded, MethodHandle getX, MethodHandle getY, MethodHandle getZ,
                           MethodHandle getSectionX, MethodHandle getSectionY, MethodHandle getSectionZ, Field chunks,
                           Field providerChunks, Field dispatcherQueue, Field lastCompileTask,
                           Field lastCompileTaskResult, Field lastTimeRecorded, Field lastTimeEnqueued, Field nonemptyVboParts,
                           Object solid, Object cutout, Object cutoutMipped, Object translucent, Object bloom) {
            this.getRenderer = getRenderer;
            this.getProvider = getProvider;
            this.getTaskDispatcher = getTaskDispatcher;
            this.dispatcherUpdate = dispatcherUpdate;
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
            this.worldUtilIsChunkLoaded = worldUtilIsChunkLoaded;
            this.getX = getX;
            this.getY = getY;
            this.getZ = getZ;
            this.getSectionX = getSectionX;
            this.getSectionY = getSectionY;
            this.getSectionZ = getSectionZ;
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
            this.bloom = bloom;
        }

        private int getX(Object chunk) throws ReflectiveOperationException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$blockX();
            }
            return invokeInt(getX, chunk);
        }

        private Object getVboPart(Object chunk, Object pass) throws ReflectiveOperationException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$vboPart(pass);
            }
            try {
                return (Object) getVboPart.invokeExact(chunk, pass);
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Throwable throwable) {
                throw new ReflectiveOperationException(throwable);
            }
        }

        private boolean isValid(Object part) throws ReflectiveOperationException {
            try {
                return (boolean) isValid.invokeExact(part);
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Throwable throwable) {
                throw new ReflectiveOperationException(throwable);
            }
        }

        private int getY(Object chunk) throws ReflectiveOperationException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$blockY();
            }
            return invokeInt(getY, chunk);
        }

        private int getZ(Object chunk) throws ReflectiveOperationException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$blockZ();
            }
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

        private static MethodHandle objectMethod(Method method) throws IllegalAccessException {
            return MethodHandles.publicLookup()
                    .unreflect(method)
                    .asType(MethodType.methodType(Object.class, Object.class, Object.class));
        }

        private static MethodHandle booleanGetter(Method method) throws IllegalAccessException {
            return MethodHandles.publicLookup()
                    .unreflect(method)
                    .asType(MethodType.methodType(boolean.class, Object.class));
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
                Class<?> worldUtilClass = Class.forName("meldexun.nothirium.mc.util.WorldUtil");
                Method worldUtilIsChunkLoaded = worldUtilClass.getMethod("isChunkLoaded", int.class, int.class);
                Method getX = renderChunkClass.getMethod("getX");
                Method getY = renderChunkClass.getMethod("getY");
                Method getZ = renderChunkClass.getMethod("getZ");
                Method getSectionX = renderChunkClass.getMethod("getSectionX");
                Method getSectionY = renderChunkClass.getMethod("getSectionY");
                Method getSectionZ = renderChunkClass.getMethod("getSectionZ");
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
                Object bloom = enumValueOrNull(passClass, "BLOOM");
                return new Reflection(
                        getRenderer,
                        getProvider,
                        getTaskDispatcher,
                        dispatcherUpdate,
                        enumMapGet,
                        renderedChunks,
                        renderedSections,
                        renderedSectionsAll,
                        objectMethod(getVboPart),
                        intGetter(getVbo),
                        intGetter(getFirst),
                        intGetter(getCount),
                        intGetter(getOffset),
                        intGetter(getSize),
                        booleanGetter(isValid),
                        isDirty,
                        isEmpty,
                        markDirty,
                        releaseBuffers,
                        canCompile,
                        compileAsync,
                        worldUtilIsChunkLoaded,
                        intGetter(getX),
                        intGetter(getY),
                        intGetter(getZ),
                        intGetter(getSectionX),
                        intGetter(getSectionY),
                        intGetter(getSectionZ),
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
                        translucent,
                        bloom
                );
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        private boolean isChunkDirty(Object chunk) throws ReflectiveOperationException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$isDirty();
            }
            return (Boolean) isDirty.invoke(chunk);
        }

        private boolean isChunkEmpty(Object chunk) throws ReflectiveOperationException {
            return (Boolean) isEmpty.invoke(chunk);
        }

        private Boolean canCompile(Object chunk) throws ReflectiveOperationException {
            return (Boolean) canCompile.invoke(chunk);
        }

        private int getSectionX(Object chunk) throws ReflectiveOperationException {
            return invokeInt(getSectionX, chunk);
        }

        private int getSectionY(Object chunk) throws ReflectiveOperationException {
            return invokeInt(getSectionY, chunk);
        }

        private int getSectionZ(Object chunk) throws ReflectiveOperationException {
            return invokeInt(getSectionZ, chunk);
        }

        private Object lastCompileTask(Object chunk) throws IllegalAccessException {
            return lastCompileTask.get(chunk);
        }

        private Object lastCompileTaskResult(Object chunk) throws IllegalAccessException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$lastCompileTaskResult();
            }
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

        private int drainDispatcherQueue(Object dispatcher, int maximumTasks) throws IllegalAccessException {
            if (maximumTasks <= 0
                    || dispatcherQueue == null
                    || !dispatcherQueue.getDeclaringClass().isInstance(dispatcher)) {
                return -1;
            }
            Object queuedTasks = dispatcherQueue.get(dispatcher);
            if (!(queuedTasks instanceof Queue<?> queue)) {
                return -1;
            }
            int drained = 0;
            while (drained < maximumTasks) {
                Object task = queue.poll();
                if (!(task instanceof Runnable runnable)) {
                    break;
                }
                runnable.run();
                drained++;
            }
            return drained;
        }

        private Object passFor(BlockRenderLayer layer) {
            return switch (layer) {
                case SOLID -> solid;
                case CUTOUT -> cutout;
                case CUTOUT_MIPPED -> cutoutMipped;
                case TRANSLUCENT -> translucent;
                default -> "BLOOM".equals(layer.name()) ? bloom : null;
            };
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Object enumValueOrNull(Class<?> enumClass, String name) {
            try {
                return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
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
