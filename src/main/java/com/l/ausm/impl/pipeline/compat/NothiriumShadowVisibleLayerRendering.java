package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.BlockRenderLayer;

abstract class NothiriumShadowVisibleLayerRendering extends NothiriumShadowSelection {
    protected int renderVisibleLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                     int fallbackBlockEntityId, short fallbackRenderType, double maxDistance,
                                     boolean requirePipelineStride) {
        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
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

            NothiriumShadowRenderer.DrawStats stats = self().drawChunksWithLayerState(layer, reflection, pass, chunks, cameraX, cameraY, cameraZ, maxDistance, false,
                    fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
            if (stats.unsupportedStride > 0) {
                self().refreshUnsupportedPipelineChunks(reflection, stats.unsupportedPipelineChunks);
            }
            if (PipelineContext.getInstance().isPipelineActive()
                    && stats.total == 0
                    && shaderedProviderStateProbeAttempts < MAX_SHADERED_PROVIDER_STATE_PROBE_LOGS) {
                shaderedProviderStateProbeAttempts++;
                self().auditProviderState(reflection, renderer, chunksByPass, layer, cameraX, cameraY, cameraZ,
                        "shadered-main", shaderedProviderStateProbeAttempts);
            }
            self().auditVisibleTranslucentLayer(layer, stats, fallbackBlockEntityId, fallbackRenderType, "after-draw");
            if (NothiriumShadowRenderer.shouldAuditSparseVisibleBridge(stats)) {
                self().auditVisibleTerrainFailure(layer, stats, fallbackBlockEntityId, fallbackRenderType);
            }
            self().auditNonSolidVisibleTerrainFailure(layer, stats, fallbackBlockEntityId, fallbackRenderType);
            return stats.drawn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            self().warnOnce(e);
            return -1;
        }
    }

    protected void auditProviderState(NothiriumShadowRenderer.Reflection reflection, Object renderer, Object chunksByPass,
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
                                    + " future=" + NothiriumShadowRenderer.DrawStats.futureState(taskResult);
                        }
                        if (pass != null && self().providerChunkHasRenderablePart(reflection, pass, chunk, false)) {
                            providerRenderable++;
                        }
                    }
                }
            }
            String rendererLists = self().rendererPassSizes(reflection, chunksByPass);
            String nearestGate = self().nearestCompileGate(reflection, provider, chunksObject, cameraX, cameraY, cameraZ);
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

    protected String nearestCompileGate(NothiriumShadowRenderer.Reflection reflection, Object provider, Object chunksObject,
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
                + " future=" + NothiriumShadowRenderer.DrawStats.futureState(future)
                + " recorded=" + reflection.lastTimeRecorded(nearest)
                + " enqueued=" + reflection.lastTimeEnqueued(nearest);
    }

    protected String rendererPassSizes(NothiriumShadowRenderer.Reflection reflection, Object chunksByPass) throws ReflectiveOperationException {
        return "solid=" + self().listSize(reflection, chunksByPass, reflection.solid)
                + ",mipped=" + self().listSize(reflection, chunksByPass, reflection.cutoutMipped)
                + ",cutout=" + self().listSize(reflection, chunksByPass, reflection.cutout)
                + ",translucent=" + self().listSize(reflection, chunksByPass, reflection.translucent)
                + ",bloom=" + self().listSize(reflection, chunksByPass, reflection.bloom);
    }

    protected int listSize(NothiriumShadowRenderer.Reflection reflection, Object chunksByPass, Object pass) throws ReflectiveOperationException {
        if (chunksByPass == null || pass == null) {
            return -1;
        }
        Object value = reflection.enumMapGet.invoke(chunksByPass, pass);
        return value instanceof Collection<?> collection ? collection.size() : -1;
    }

    protected int renderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance,
                              boolean scheduleCompiles, boolean audit, boolean visibleOnly,
                              int fallbackBlockEntityId, short fallbackRenderType, boolean requirePipelineStride) {
        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
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
                        List<?> candidates = self().providerChunksInRange(reflection, chunks, cameraX, cameraY, cameraZ, maxDistance);
                        if (scheduleCompiles) {
                            self().scheduleMissingLayerCompiles(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ, maxDistance);
                        }
                        NothiriumShadowRenderer.DrawStats stats = self().drawChunksWithLayerState(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ,
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
                    self().auditEmpty(layer, null, null, null);
                }
                return 0;
            }

            Object chunksByPass = reflection.chunks.get(renderer);
            if (chunksByPass == null) {
                if (audit) {
                    self().auditEmpty(layer, renderer, pass, null);
                }
                return 0;
            }

            Object chunksObject = reflection.enumMapGet.invoke(chunksByPass, pass);
            if (!(chunksObject instanceof List<?> chunks) || chunks.isEmpty()) {
                if (audit) {
                    self().auditEmpty(layer, renderer, pass, chunksObject instanceof List<?> list ? list : null);
                }
                return 0;
            }

            NothiriumShadowRenderer.DrawStats stats = self().drawChunksWithLayerState(layer, reflection, pass, chunks, cameraX, cameraY, cameraZ, maxDistance, false,
                    fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
            if (audit) {
            }
            return stats.drawn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            self().warnOnce(e);
            return 0;
        }
    }

    protected int renderProviderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                      double maxDistance, boolean scheduleCompiles,
                                      int fallbackBlockEntityId, short fallbackRenderType,
                                      boolean requirePipelineStride) {
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
            if (scheduleCompiles) {
                self().scheduleMissingLayerCompiles(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ, maxDistance);
            }
            NothiriumShadowRenderer.DrawStats stats = self().drawChunksWithLayerState(layer, reflection, pass, candidates, cameraX, cameraY, cameraZ,
                    maxDistance, false, fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
            if (stats.unsupportedStride > 0) {
                self().refreshUnsupportedPipelineChunks(reflection, stats.unsupportedPipelineChunks);
            }
            return stats.drawn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            self().warnOnce(e);
            return 0;
        }
    }

    protected List<?> providerChunksInRange(NothiriumShadowRenderer.Reflection reflection, Object[] chunks,
                                            double cameraX, double cameraY, double cameraZ, double maxDistance)
            throws ReflectiveOperationException {
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        List<NothiriumShadowRenderer.ProviderCandidate> filtered = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk == null) {
                continue;
            }

            NothiriumShadowRenderer.ChunkOrigin origin = self().chunkOrigin(reflection, chunk);
            int chunkX = origin.x;
            int chunkY = origin.y;
            int chunkZ = origin.z;
            double dx = chunkX + 8.0D - cameraX;
            double dy = chunkY + 8.0D - cameraY;
            double dz = chunkZ + 8.0D - cameraZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (maxDistanceSquared < 0.0D || distanceSquared <= maxDistanceSquared) {
                filtered.add(new NothiriumShadowRenderer.ProviderCandidate(chunk, distanceSquared));
            }
        }
        filtered.sort(Comparator.comparingDouble(candidate -> candidate.distanceSquared));
        List<Object> ordered = new ArrayList<>(filtered.size());
        for (NothiriumShadowRenderer.ProviderCandidate candidate : filtered) {
            ordered.add(candidate.chunk);
        }
        return ordered;
    }

    protected List<?> selectedShadowChunks(NothiriumShadowRenderer.Reflection reflection, Object provider, Object[] chunks,
                                           double cameraX, double cameraY, double cameraZ, double maxDistance) {
        NothiriumShadowRenderer.ShadowSelection selection = shadowSelection;
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

    protected void resetMainTerrainCompileScanCursors(Object provider, Object[] chunks) {
        if (mainTerrainCompileScanProvider == provider && mainTerrainCompileScanSourceChunks == chunks) {
            return;
        }
        mainTerrainCompileScanCursors.clear();
        mainTerrainCompileScanProvider = provider;
        mainTerrainCompileScanSourceChunks = chunks;
    }

    protected Iterable<?> boundedMainTerrainCompileScan(BlockRenderLayer layer, Iterable<?> chunks) {
        NothiriumShadowRenderer.ShadowSelection selection = shadowSelection;
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

    /**
     * Chunk renderers are repositioned between shadow passes, but their
     * origins are stable throughout one pass. Reuse them across the three
     * terrain layers and compile admission without changing selection or draw
     * coverage.
     */
    protected NothiriumShadowRenderer.ChunkOrigin chunkOrigin(NothiriumShadowRenderer.Reflection reflection, Object chunk) throws ReflectiveOperationException {
        if (chunk instanceof NothiriumShadowChunkAccess access) {
            return new NothiriumShadowRenderer.ChunkOrigin(access.ausm$blockX(), access.ausm$blockY(), access.ausm$blockZ());
        }
        if (!shadowSelectionActive) {
            return new NothiriumShadowRenderer.ChunkOrigin(reflection.getX(chunk), reflection.getY(chunk), reflection.getZ(chunk));
        }
        NothiriumShadowRenderer.ChunkOrigin origin = shadowChunkOrigins.get(chunk);
        if (origin != null) {
            return origin;
        }
        origin = new NothiriumShadowRenderer.ChunkOrigin(reflection.getX(chunk), reflection.getY(chunk), reflection.getZ(chunk));
        shadowChunkOrigins.put(chunk, origin);
        return origin;
    }

    /**
     * Conservative homogeneous clip-space AABB test. The extra block margin
     * covers oversized model geometry while still rejecting sections which
     * cannot affect the current orthographic shadow map.
     */
    protected static boolean intersectsShadowFrustum(float[] modelView, float[] projection,
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

    protected List<?> nearestRenderableProviderChunks(NothiriumShadowRenderer.Reflection reflection, Object pass, Object[] chunks,
                                                      double cameraX, double cameraY, double cameraZ,
                                                      double maxDistance, int maxChunks,
                                                      boolean requirePipelineStride)
            throws ReflectiveOperationException {
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        List<NothiriumShadowRenderer.ProviderCandidate> candidates = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk == null) {
                continue;
            }

            NothiriumShadowRenderer.ChunkOrigin origin = self().chunkOrigin(reflection, chunk);
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
            if (!self().providerChunkHasRenderablePart(reflection, pass, chunk, requirePipelineStride)) {
                continue;
            }
            candidates.add(new NothiriumShadowRenderer.ProviderCandidate(chunk, distanceSquared));
        }

        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distanceSquared));
        int limit = Math.min(maxChunks, candidates.size());
        List<Object> nearest = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            nearest.add(candidates.get(i).chunk);
        }
        return nearest;
    }

    protected List<?> nearestProviderChunks(NothiriumShadowRenderer.Reflection reflection, Object[] chunks,
                                            double cameraX, double cameraY, double cameraZ,
                                            double maxDistance, int maxChunks)
            throws ReflectiveOperationException {
        if (maxChunks <= 0) {
            return List.of();
        }

        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        List<NothiriumShadowRenderer.ProviderCandidate> candidates = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk == null) {
                continue;
            }

            NothiriumShadowRenderer.ChunkOrigin origin = self().chunkOrigin(reflection, chunk);
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
            candidates.add(new NothiriumShadowRenderer.ProviderCandidate(chunk, distanceSquared));
        }

        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distanceSquared));
        int limit = Math.min(maxChunks, candidates.size());
        List<Object> nearest = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            nearest.add(candidates.get(i).chunk);
        }
        return nearest;
    }

    protected boolean providerChunkHasRenderablePart(NothiriumShadowRenderer.Reflection reflection, Object pass, Object chunk, boolean requirePipelineStride)
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
        int stride = NothiriumShadowRenderer.vertexStride(size, count);
        return stride > 0 && (!requirePipelineStride || NothiriumShadowRenderer.isPipelineBlockStride(stride));
    }

    protected void scheduleShadowCompiles(NothiriumShadowRenderer.Reflection reflection, Iterable<?> chunks,
                                          double cameraX, double cameraY, double cameraZ, double maxDistance)
            throws ReflectiveOperationException {
        Object renderer = reflection.getRenderer.invoke(null);
        Object dispatcher = reflection.getTaskDispatcher.invoke(null);
        if (renderer == null || dispatcher == null) {
            return;
        }

        NothiriumShadowRenderer.CompileStats stats = new NothiriumShadowRenderer.CompileStats();
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        for (Object chunk : chunks) {
            stats.total++;
            if (chunk == null) {
                stats.nullChunks++;
                continue;
            }

            NothiriumShadowRenderer.ChunkOrigin origin = self().chunkOrigin(reflection, chunk);
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
            if (NothiriumShadowRenderer.futureIsRunning(future)) {
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
        self().auditCompileStats(stats);
    }

    protected int scheduleMissingLayerCompiles(BlockRenderLayer layer, NothiriumShadowRenderer.Reflection reflection, Object pass, Iterable<?> chunks,
                                               double cameraX, double cameraY, double cameraZ, double maxDistance)
            throws ReflectiveOperationException {
        Object renderer = reflection.getRenderer.invoke(null);
        Object dispatcher = reflection.getTaskDispatcher.invoke(null);
        if (renderer == null || dispatcher == null) {
            return 0;
        }

        NothiriumShadowRenderer.CompileStats stats = new NothiriumShadowRenderer.CompileStats();
        int running = 0;
        long now = System.currentTimeMillis();
        self().pruneMainTerrainCompileAttempts(now);
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        List<NothiriumShadowRenderer.CompileCandidate> candidates = new ArrayList<>();
        for (Object chunk : self().boundedMainTerrainCompileScan(layer, chunks)) {
            stats.total++;
            if (chunk == null) {
                stats.nullChunks++;
                continue;
            }

            NothiriumShadowRenderer.ChunkOrigin origin = self().chunkOrigin(reflection, chunk);
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
            if (NothiriumShadowRenderer.futureIsRunning(future)) {
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
            candidates.add(new NothiriumShadowRenderer.CompileCandidate(chunk, distanceSquared, invalidPart));
        }

        candidates.sort((left, right) -> Double.compare(left.distanceSquared, right.distanceSquared));
        int budget = NothiriumShadowRenderer.mainTerrainCompileBudget(layer);
        for (NothiriumShadowRenderer.CompileCandidate candidate : candidates) {
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

        self().auditMainCompileStats(stats);
        self().auditCompileCandidates(reflection, pass, candidates, cameraX, cameraY, cameraZ, layer, stats);
        return stats.scheduled > 0 ? stats.scheduled : stats.running + stats.throttled + stats.cannotCompile;
    }
}
