package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;

abstract class NothiriumShadowSelection extends NothiriumShadowReflectionBindings {
    public static boolean isAvailable() {
        return NothiriumShadowRenderer.reflection() != null;
    }

    public void resetPipelineProgramState() {
        chunkOffsetUniformLocations.clear();
        mainTerrainCompileAttempts.clear();
        mainTerrainCompileScanCursors.clear();
        mainTerrainCompileScanProvider = null;
        mainTerrainCompileScanSourceChunks = null;
        shadowSelection = null;
        shadowSelectionActive = false;
        self().clearShadowSelectionScratch();
        self().clearCachedShadowSelection();
        shadowChunkOrigins.clear();
        self().endQueuedChunkRefreshBatch();
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
        self().clearShadowSelectionScratch();
        shadowChunkOrigins.clear();
        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
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
            self().resetMainTerrainCompileScanCursors(provider, chunks);
            shadowSelectionActive = true;
            long vboGeneration = NothiriumVisibleTerrainCache.vboGeneration();
            if (self().canReuseShadowSelection(reflection, provider, chunks,
                    cameraX, cameraY, cameraZ, maxDistance)) {
                shadowSelection = new NothiriumShadowRenderer.ShadowSelection(reflection, provider, chunks,
                        new NothiriumShadowRenderer.ShadowSelectionChunkList(cachedShadowSelectionChunks, cachedShadowSelectionCount),
                        cameraX, cameraY, cameraZ, maxDistance);
                return;
            }

            shadowSelectionModelView.clear();
            shadowSelectionProjection.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, shadowSelectionModelView);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, shadowSelectionProjection);
            shadowSelectionModelView.get(shadowSelectionModelViewValues);
            shadowSelectionProjection.get(shadowSelectionProjectionValues);
            self().ensureShadowSelectionScratchCapacity(chunks.length);

            double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
            int selectedCount = 0;
            int nonNull = 0;
            int heightCulled = 0;
            int distanceCulled = 0;
            int frustumCulled = 0;
            int worldHeight = NothiriumShadowRenderer.shadowWorldHeight();
            for (Object chunk : chunks) {
                if (chunk == null) {
                    continue;
                }
                nonNull++;
                NothiriumShadowRenderer.ChunkOrigin origin = self().chunkOrigin(reflection, chunk);
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
                if (!NothiriumShadowRenderer.intersectsShadowFrustum(shadowSelectionModelViewValues, shadowSelectionProjectionValues,
                        chunkX - cameraX, chunkY - cameraY, chunkZ - cameraZ)) {
                    frustumCulled++;
                    continue;
                }
                shadowSelectionChunks[selectedCount] = chunk;
                shadowSelectionDistances[selectedCount] = distanceSquared;
                selectedCount++;
            }
            self().sortShadowSelectionCandidates(0, selectedCount - 1);
            self().cacheShadowSelection(reflection, provider, chunks, cameraX, cameraY, cameraZ,
                    maxDistance, selectedCount);
            self().clearShadowSelectionScratch();
            List<Object> ordered = new NothiriumShadowRenderer.ShadowSelectionChunkList(cachedShadowSelectionChunks, selectedCount);
            shadowSelection = new NothiriumShadowRenderer.ShadowSelection(reflection, provider, chunks, ordered,
                    cameraX, cameraY, cameraZ, maxDistance);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            shadowSelection = null;
            shadowSelectionActive = false;
            self().clearShadowSelectionScratch();
            shadowChunkOrigins.clear();
            self().warnOnce(exception);
        }
    }

    public void endShadowSelection() {
        shadowSelection = null;
        shadowSelectionActive = false;
        self().clearShadowSelectionScratch();
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
        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
        NothiriumShadowRenderer.ShadowSelection selection = shadowSelection;
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
                    NothiriumShadowRenderer.ChunkOrigin origin = self().chunkOrigin(reflection, chunk);
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
                if (count <= 0 || vbo <= 0 || NothiriumShadowRenderer.vertexStride(reflection.getSize(part), count) <= 0) {
                    continue;
                }
                ready++;
                if (ready >= limit) {
                    break;
                }
            }
            return ready;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            self().warnOnce(exception);
            return -1;
        }
    }

    protected boolean canReuseShadowSelection(NothiriumShadowRenderer.Reflection reflection, Object provider, Object[] chunks,
                                              double cameraX, double cameraY, double cameraZ,
                                              double maxDistance) {
        if (cachedShadowSelectionReflection != reflection
                || cachedShadowSelectionProvider != provider
                || cachedShadowSelectionSourceChunks != chunks
                || cachedShadowSelectionMaxDistance != maxDistance
                || cachedShadowSelectionCameraChunkX != NothiriumShadowRenderer.cameraChunkCoordinate(cameraX)
                || cachedShadowSelectionCameraChunkY != NothiriumShadowRenderer.cameraChunkCoordinate(cameraY)
                || cachedShadowSelectionCameraChunkZ != NothiriumShadowRenderer.cameraChunkCoordinate(cameraZ)) {
            return false;
        }
        double dx = cameraX - cachedShadowSelectionCameraX;
        double dy = cameraY - cachedShadowSelectionCameraY;
        double dz = cameraZ - cachedShadowSelectionCameraZ;
        return dx * dx + dy * dy + dz * dz <= SHADOW_SELECTION_REUSE_DISTANCE_SQUARED;
    }

    protected void cacheShadowSelection(NothiriumShadowRenderer.Reflection reflection, Object provider, Object[] chunks,
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
        cachedShadowSelectionCameraChunkX = NothiriumShadowRenderer.cameraChunkCoordinate(cameraX);
        cachedShadowSelectionCameraChunkY = NothiriumShadowRenderer.cameraChunkCoordinate(cameraY);
        cachedShadowSelectionCameraChunkZ = NothiriumShadowRenderer.cameraChunkCoordinate(cameraZ);
        cachedShadowSelectionMaxDistance = maxDistance;
    }

    protected void clearCachedShadowSelection() {
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

    protected static int cameraChunkCoordinate(double coordinate) {
        return (int) Math.floor(coordinate / 16.0D);
    }

    protected static int shadowWorldHeight() {
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

    protected void ensureShadowSelectionScratchCapacity(int capacity) {
        if (shadowSelectionChunks.length >= capacity) {
            return;
        }
        shadowSelectionChunks = new Object[capacity];
        shadowSelectionDistances = new double[capacity];
    }

    protected void clearShadowSelectionScratch() {
        for (int index = 0; index < shadowSelectionScratchCount; index++) {
            shadowSelectionChunks[index] = null;
        }
        shadowSelectionScratchCount = 0;
    }

    /**
     * Sort parallel, allocation-free chunk/distance arrays nearest-first.
     */
    protected void sortShadowSelectionCandidates(int low, int high) {
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
                    self().sortShadowSelectionCandidates(low, right);
                }
                low = left;
            } else {
                if (left < high) {
                    self().sortShadowSelectionCandidates(left, high);
                }
                high = right;
            }
        }
    }

    /**
     * Coalesces one render-thread refresh batch into a single provider scan.
     * Render chunks can move between batches, so the index is never retained.
     */
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
        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
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
            NothiriumShadowRenderer.drainQueuedUploads(reflection, dispatcher);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            self().warnOnce(e);
        }
    }

    /**
     * Nothirium's public update() method runs every queued render-thread task.
     * Its concrete dispatcher exposes the queue, so consume a small bounded
     * prefix when available. Keep the public method as a compatibility
     * fallback for another dispatcher implementation.
     */
    protected static void drainQueuedUploads(NothiriumShadowRenderer.Reflection reflection, Object dispatcher)
            throws ReflectiveOperationException {
        if (reflection.drainDispatcherQueue(dispatcher, MAX_UPLOAD_TASKS_PER_DRAIN) < 0) {
            reflection.dispatcherUpdate.invoke(dispatcher);
        }
    }

    public boolean refreshChunkColumn(int chunkX, int chunkZ) {
        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
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
                    ? self().queuedChunkRefreshColumn(reflection, provider, chunks, chunkX, chunkZ)
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
                if (NothiriumShadowRenderer.futureIsRunning(reflection.lastCompileTaskResult(chunk))) {
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
                NothiriumShadowRenderer.drainQueuedUploads(reflection, dispatcher);
            }
            self().auditChunkRefresh(chunkX, chunkZ, total, nullChunks, matched, alreadyDirty, running,
                    released, marked, canCompile, cannotCompile, noDispatcher, scheduled, deferred);
            return matched > 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            self().warnOnce(e);
            return false;
        }
    }

    protected List<Object> queuedChunkRefreshColumn(NothiriumShadowRenderer.Reflection reflection, Object provider, Object[] chunks,
                                                    int chunkX, int chunkZ) throws ReflectiveOperationException {
        if (queuedChunkRefreshProvider != provider || queuedChunkRefreshSourceChunks != chunks) {
            queuedChunkRefreshColumns.clear();
            queuedChunkRefreshProvider = provider;
            queuedChunkRefreshSourceChunks = chunks;
            for (Object chunk : chunks) {
                if (chunk == null) {
                    continue;
                }
                long key = NothiriumShadowRenderer.chunkColumnKey(reflection.getX(chunk) >> 4, reflection.getZ(chunk) >> 4);
                List<Object> column = queuedChunkRefreshColumns.get(key);
                if (column == null) {
                    column = new ArrayList<>();
                    queuedChunkRefreshColumns.put(key, column);
                }
                column.add(chunk);
            }
        }
        List<Object> column = queuedChunkRefreshColumns.get(NothiriumShadowRenderer.chunkColumnKey(chunkX, chunkZ));
        return column != null ? column : Collections.emptyList();
    }

    protected static long chunkColumnKey(int chunkX, int chunkZ) {
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
        return self().renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, 0, (short) 0);
    }

    public int renderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance,
                           int fallbackBlockEntityId, short fallbackRenderType) {
        return self().renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, false, true, false,
                fallbackBlockEntityId, fallbackRenderType, false);
    }

    public int renderLayerRequiringPipelineStride(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                  double maxDistance) {
        return self().renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, false, true, false, 0, (short) 0, true);
    }

    public int renderLayerSchedulingCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance) {
        return self().renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, true, true, false, 0, (short) 0, false);
    }

    public int renderProviderLayerSchedulingCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                     double maxDistance, int fallbackBlockEntityId,
                                                     short fallbackRenderType, boolean requirePipelineStride) {
        return self().renderProviderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, true,
                fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
    }

    public int renderNearestProviderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                          double maxDistance, int maxChunks, int fallbackBlockEntityId,
                                          short fallbackRenderType, boolean requirePipelineStride) {
        return self().renderNearestProviderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, maxChunks,
                fallbackBlockEntityId, fallbackRenderType, requirePipelineStride, false);
    }

    public int renderNearestProviderLayerSchedulingCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                            double maxDistance, int maxChunks, int fallbackBlockEntityId,
                                                            short fallbackRenderType, boolean requirePipelineStride) {
        return self().renderNearestProviderLayerSchedulingCompiles(layer, cameraX, cameraY, cameraZ, maxDistance, maxChunks,
                fallbackBlockEntityId, fallbackRenderType, requirePipelineStride, true);
    }

    public int renderNearestProviderLayerSchedulingCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                            double maxDistance, int maxChunks, int fallbackBlockEntityId,
                                                            short fallbackRenderType, boolean requirePipelineStride,
                                                            boolean scheduleCompiles) {
        return self().renderNearestProviderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, maxChunks,
                fallbackBlockEntityId, fallbackRenderType, requirePipelineStride, scheduleCompiles);
    }

    protected int renderNearestProviderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                             double maxDistance, int maxChunks, int fallbackBlockEntityId,
                                             short fallbackRenderType, boolean requirePipelineStride,
                                             boolean scheduleCompiles) {
        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
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
                List<?> compileCandidates = self().nearestProviderChunks(
                        reflection,
                        chunks,
                        cameraX,
                        cameraY,
                        cameraZ,
                        maxDistance,
                        Math.max(maxChunks * 2, maxChunks)
                );
                self().scheduleMissingLayerCompiles(layer, reflection, pass, compileCandidates, cameraX, cameraY, cameraZ, maxDistance);
            }
            List<?> candidates = self().nearestRenderableProviderChunks(
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

            NothiriumShadowRenderer.DrawStats stats = self().drawChunksWithLayerState(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ,
                    maxDistance, false, fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
            return stats.drawn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            self().warnOnce(e);
            return 0;
        }
    }

    public int scheduleLayerCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance) {
        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
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
            List<?> candidates = self().selectedShadowChunks(reflection, provider, chunks, cameraX, cameraY, cameraZ, maxDistance);
            if (candidates == null) {
                candidates = self().providerChunksInRange(reflection, chunks, cameraX, cameraY, cameraZ, maxDistance);
            }
            return self().scheduleMissingLayerCompiles(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ, maxDistance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            self().warnOnce(e);
            return 0;
        }
    }

    public int scheduleNearestLayerCompiles(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                            double maxDistance, int maxChunks) {
        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
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
            List<?> candidates = self().nearestProviderChunks(reflection, chunks, cameraX, cameraY, cameraZ, maxDistance, maxChunks);
            return self().scheduleMissingLayerCompiles(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ, maxDistance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            self().warnOnce(e);
            return 0;
        }
    }

    public int renderVisibleLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                  int fallbackBlockEntityId, short fallbackRenderType) {
        return self().renderVisibleLayer(layer, cameraX, cameraY, cameraZ, fallbackBlockEntityId, fallbackRenderType, -1.0D);
    }

    public int renderVisibleLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                  int fallbackBlockEntityId, short fallbackRenderType, double maxDistance) {
        return self().renderVisibleLayer(layer, cameraX, cameraY, cameraZ, fallbackBlockEntityId, fallbackRenderType, maxDistance, true);
    }

    public int renderVisibleLayerAllowingVanillaStride(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                       int fallbackBlockEntityId, short fallbackRenderType) {
        return self().renderVisibleLayer(layer, cameraX, cameraY, cameraZ, fallbackBlockEntityId, fallbackRenderType, -1.0D, false);
    }

    public int renderVisibleLayerAllowingVanillaStride(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                       int fallbackBlockEntityId, short fallbackRenderType,
                                                       double maxDistance) {
        return self().renderVisibleLayer(layer, cameraX, cameraY, cameraZ, fallbackBlockEntityId, fallbackRenderType, maxDistance, false);
    }
}
