package com.l.ausm.impl.pipeline;

import com.l.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.l.ausm.impl.mixin.pipeline.ViewFrustumAccessor;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.compat.NothiriumBypass;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_RECENT_PRUNE_INTERVAL_FRAMES;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_RECENT_TTL_FRAMES;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.MAX_CLIENT_CHUNK_RENDER_REFRESHES_PER_FRAME;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.MAX_CLIENT_CHUNK_RENDER_REFRESH_SECTIONS_PER_FRAME;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.MAX_RECENT_CLIENT_CHUNK_RENDER_REFRESHES_PER_WORLD;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.MAX_SHADER_CHUNK_REFRESHES_PER_FRAME;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.MAX_STALE_CLIENT_CHUNK_REFRESHES_AGED_PER_FRAME;

abstract class PipelineDeferredPassOrchestration3 extends PipelineDeferredPassOrchestration2 {
    public void runPendingShaderChunkRefreshes() {
        if (!isPipelineActive) {
            self().clearPendingShaderChunkRefreshes();
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        boolean refreshNothirium = !NothiriumBypass.shouldBypass();
        if (refreshNothirium) {
            nothiriumShadowRenderer.beginQueuedChunkRefreshBatch();
        }
        try {
            for (int i = 0; i < MAX_SHADER_CHUNK_REFRESHES_PER_FRAME; i++) {
                ShaderChunkRefresh refresh;
                synchronized (pendingShaderChunkRefreshes) {
                    if (pendingShaderChunkRefreshes.isEmpty()) {
                        return;
                    }
                    refresh = pendingShaderChunkRefreshes.iterator().next();
                    pendingShaderChunkRefreshes.remove(refresh);
                }

                self().refreshShaderChunk(mc, refresh);
            }
        } finally {
            if (refreshNothirium) {
                nothiriumShadowRenderer.endQueuedChunkRefreshBatch();
            }
        }
    }

    public void runPendingClientChunkRenderRefreshes() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }
        self().runPendingClientChunkRenderRefreshesForWorld(mc, MinecraftReflectionCompat.world(mc), true);
    }

    public void runPendingClientChunkRenderRefreshesForCurrentRenderPass() {
        if (!BetterPortalsCompat.isInstalled() || !BetterPortalsCompat.isRenderingRenderPass()) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (mc == null || MinecraftReflectionCompat.renderGlobal(mc) == null || renderPassWorld == null) {
            return;
        }

        self().runPendingClientChunkRenderRefreshesForWorld(mc, renderPassWorld, false);
    }

    protected void runPendingClientChunkRenderRefreshesForWorld(Minecraft mc, WorldClient targetWorld,
                                                                boolean advanceDelays) {
        if (targetWorld == null) {
            return;
        }
        if (advanceDelays) {
            self().ageStaleClientChunkRenderRefreshes(targetWorld);
        }
        // A normal client refresh can process eight columns in one frame. The
        // Nothirium shadow bridge used to linearly scan its complete provider
        // array for every one of those columns, while the equivalent shader
        // refresh path already coalesces that lookup. Build one short-lived
        // column index for this batch instead; renderer chunks may be moved
        // between batches, hence it must not be retained across frames.
        boolean refreshNothirium = !NothiriumBypass.shouldBypass();
        if (refreshNothirium) {
            nothiriumShadowRenderer.beginQueuedChunkRefreshBatch();
        }
        try {
            for (int i = 0; i < MAX_CLIENT_CHUNK_RENDER_REFRESHES_PER_FRAME; i++) {
                ClientChunkRenderRefresh refresh = self().pollDueClientChunkRenderRefresh(targetWorld, advanceDelays);
                if (refresh == null) {
                    return;
                }

                boolean retryNeeded = self().refreshClientChunkRender(mc, refresh, targetWorld);
                refresh.attemptsRemaining--;
                if (retryNeeded && refresh.attemptsRemaining > 0 && refresh.world == targetWorld) {
                    refresh.delayFrames = CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES;
                    synchronized (pendingClientChunkRenderRefreshes) {
                        self().addPendingClientChunkRenderRefreshLocked(refresh);
                    }
                }
            }
        } finally {
            if (refreshNothirium) {
                nothiriumShadowRenderer.endQueuedChunkRefreshBatch();
            }
        }
    }

    protected ClientChunkRenderRefresh pollDueClientChunkRenderRefresh(WorldClient targetWorld, boolean advanceDelays) {
        synchronized (pendingClientChunkRenderRefreshes) {
            LinkedHashSet<ClientChunkRenderRefresh> worldRefreshes = pendingClientChunkRenderRefreshesByWorld.get(targetWorld);
            if (worldRefreshes == null || worldRefreshes.isEmpty()) {
                return null;
            }
            Iterator<ClientChunkRenderRefresh> iterator = worldRefreshes.iterator();
            while (iterator.hasNext()) {
                ClientChunkRenderRefresh refresh = iterator.next();
                if (refresh.delayFrames > 0) {
                    if (advanceDelays) {
                        refresh.delayFrames--;
                    }
                    continue;
                }
                iterator.remove();
                if (worldRefreshes.isEmpty()) {
                    pendingClientChunkRenderRefreshesByWorld.remove(targetWorld);
                }
                pendingClientChunkRenderRefreshes.remove(refresh);
                self().removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
                return refresh;
            }
        }
        return null;
    }

    protected void ageStaleClientChunkRenderRefreshes(WorldClient activeWorld) {
        if (activeWorld == null) {
            return;
        }

        int aged = 0;
        synchronized (pendingClientChunkRenderRefreshes) {
            Iterator<Map.Entry<WorldClient, LinkedHashSet<ClientChunkRenderRefresh>>> worldIterator =
                    pendingClientChunkRenderRefreshesByWorld.entrySet().iterator();
            while (worldIterator.hasNext() && aged < MAX_STALE_CLIENT_CHUNK_REFRESHES_AGED_PER_FRAME) {
                Map.Entry<WorldClient, LinkedHashSet<ClientChunkRenderRefresh>> entry = worldIterator.next();
                WorldClient refreshWorld = entry.getKey();
                LinkedHashSet<ClientChunkRenderRefresh> worldRefreshes = entry.getValue();
                if (refreshWorld == activeWorld || worldRefreshes == null || worldRefreshes.isEmpty()) {
                    if (worldRefreshes == null || worldRefreshes.isEmpty()) {
                        worldIterator.remove();
                    }
                    continue;
                }

                Iterator<ClientChunkRenderRefresh> refreshIterator = worldRefreshes.iterator();
                while (refreshIterator.hasNext() && aged < MAX_STALE_CLIENT_CHUNK_REFRESHES_AGED_PER_FRAME) {
                    ClientChunkRenderRefresh refresh = refreshIterator.next();
                    if (refresh == null || refresh.world == null) {
                        refreshIterator.remove();
                        pendingClientChunkRenderRefreshes.remove(refresh);
                        self().removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
                        aged++;
                        continue;
                    }

                    aged++;
                    if (refresh.delayFrames > 0) {
                        refresh.delayFrames--;
                        continue;
                    }

                    refresh.attemptsRemaining--;
                    if (refresh.attemptsRemaining <= 0 || !self().shouldRetainOffWorldClientChunkRefresh(refresh)) {
                        refreshIterator.remove();
                        pendingClientChunkRenderRefreshes.remove(refresh);
                        self().removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
                    } else {
                        refresh.delayFrames = CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES;
                    }
                }
                if (worldRefreshes.isEmpty()) {
                    worldIterator.remove();
                }
            }
            self().pruneRecentlyCompletedClientChunkRenderRefreshesLocked();
        }
    }

    protected void addPendingClientChunkRenderRefreshLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null || !pendingClientChunkRenderRefreshes.add(refresh)) {
            return;
        }
        pendingClientChunkRenderRefreshLookupByWorld
                .computeIfAbsent(refresh.world, ignored -> new HashMap<>())
                .put(PipelineContext.clientChunkRenderRefreshChunkKey(refresh.chunkX, refresh.chunkZ), refresh);
        pendingClientChunkRenderRefreshesByWorld
                .computeIfAbsent(refresh.world, ignored -> new LinkedHashSet<>())
                .add(refresh);
    }

    protected void removePendingClientChunkRenderRefreshLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null) {
            return;
        }
        pendingClientChunkRenderRefreshes.remove(refresh);
        self().removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
        self().removePendingClientChunkRenderRefreshFromWorldBucketLocked(refresh);
    }

    protected void removePendingClientChunkRenderRefreshFromLookupLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null) {
            return;
        }
        Map<Long, ClientChunkRenderRefresh> worldLookup = pendingClientChunkRenderRefreshLookupByWorld.get(refresh.world);
        if (worldLookup == null) {
            return;
        }
        worldLookup.remove(PipelineContext.clientChunkRenderRefreshChunkKey(refresh.chunkX, refresh.chunkZ));
        if (worldLookup.isEmpty()) {
            pendingClientChunkRenderRefreshLookupByWorld.remove(refresh.world);
        }
    }

    protected void removePendingClientChunkRenderRefreshFromWorldBucketLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null) {
            return;
        }
        LinkedHashSet<ClientChunkRenderRefresh> worldRefreshes = pendingClientChunkRenderRefreshesByWorld.get(refresh.world);
        if (worldRefreshes == null) {
            return;
        }
        worldRefreshes.remove(refresh);
        if (worldRefreshes.isEmpty()) {
            pendingClientChunkRenderRefreshesByWorld.remove(refresh.world);
        }
    }

    protected boolean shouldRetainOffWorldClientChunkRefresh(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null || !BetterPortalsCompat.isInstalled()) {
            return false;
        }
        return BetterPortalsCompat.isMainViewSwapRecoveryActive()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView();
    }

    protected boolean isRecentlyCompletedClientChunkRenderRefreshLocked(WorldClient world, long chunkKey) {
        Map<Long, Long> worldRefreshes = recentlyCompletedClientChunkRenderRefreshes.get(world);
        Long expiresAt = worldRefreshes != null ? worldRefreshes.get(chunkKey) : null;
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < pipelineFrameId) {
            worldRefreshes.remove(chunkKey);
            if (worldRefreshes.isEmpty()) {
                recentlyCompletedClientChunkRenderRefreshes.remove(world);
            }
            return false;
        }
        return true;
    }

    protected void rememberCompletedClientChunkRenderRefresh(WorldClient world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        synchronized (pendingClientChunkRenderRefreshes) {
            Map<Long, Long> worldRefreshes = recentlyCompletedClientChunkRenderRefreshes
                    .computeIfAbsent(world, ignored -> new LinkedHashMap<>());
            worldRefreshes.put(
                    PipelineContext.clientChunkRenderRefreshChunkKey(chunkX, chunkZ),
                    pipelineFrameId + CLIENT_CHUNK_RENDER_REFRESH_RECENT_TTL_FRAMES
            );
            self().trimRecentlyCompletedClientChunkRenderRefreshesLocked(worldRefreshes);
        }
    }

    protected void forgetRecentlyCompletedClientChunkRenderRefreshLocked(WorldClient world, long chunkKey) {
        Map<Long, Long> worldRefreshes = recentlyCompletedClientChunkRenderRefreshes.get(world);
        if (worldRefreshes == null) {
            return;
        }
        worldRefreshes.remove(chunkKey);
        if (worldRefreshes.isEmpty()) {
            recentlyCompletedClientChunkRenderRefreshes.remove(world);
        }
    }

    protected void pruneRecentlyCompletedClientChunkRenderRefreshesLocked() {
        if (recentlyCompletedClientChunkRenderRefreshLastPruneFrame != Long.MIN_VALUE
                && pipelineFrameId - recentlyCompletedClientChunkRenderRefreshLastPruneFrame < CLIENT_CHUNK_RENDER_REFRESH_RECENT_PRUNE_INTERVAL_FRAMES) {
            return;
        }
        recentlyCompletedClientChunkRenderRefreshLastPruneFrame = pipelineFrameId;
        Iterator<Map.Entry<WorldClient, Map<Long, Long>>> worldIterator =
                recentlyCompletedClientChunkRenderRefreshes.entrySet().iterator();
        while (worldIterator.hasNext()) {
            Map<Long, Long> worldRefreshes = worldIterator.next().getValue();
            if (worldRefreshes == null || worldRefreshes.isEmpty()) {
                worldIterator.remove();
                continue;
            }
            worldRefreshes.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < pipelineFrameId);
            if (worldRefreshes.isEmpty()) {
                worldIterator.remove();
            }
        }
    }

    protected void trimRecentlyCompletedClientChunkRenderRefreshesLocked(Map<Long, Long> worldRefreshes) {
        if (worldRefreshes == null || worldRefreshes.size() <= MAX_RECENT_CLIENT_CHUNK_RENDER_REFRESHES_PER_WORLD) {
            return;
        }
        Iterator<Long> iterator = worldRefreshes.keySet().iterator();
        while (worldRefreshes.size() > MAX_RECENT_CLIENT_CHUNK_RENDER_REFRESHES_PER_WORLD && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    protected boolean shouldQueueClientChunkRenderRefresh(WorldClient world, String reason) {
        if ("chunk-data".equals(reason)) {
            return true;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            return false;
        }
        if (CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE.equals(reason)) {
            return world == MinecraftReflectionCompat.world(mc)
                    && (isPipelineActive || pendingWorldTerrainRefreshAttempts > 0 || NothiriumBypass.shouldBypass());
        }
        if (CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM.equals(reason)) {
            return world == MinecraftReflectionCompat.world(mc);
        }
        if (world == MinecraftReflectionCompat.world(mc) && ("pre-chunk".equals(reason)
                || pendingWorldTerrainRefreshAttempts > 0
                || isPipelineActive
                || NothiriumBypass.shouldBypass())) {
            return true;
        }
        if (!BetterPortalsCompat.isInstalled()) {
            return false;
        }
        return world != MinecraftReflectionCompat.world(mc)
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView();
    }

    protected boolean refreshClientChunkRender(Minecraft mc, ClientChunkRenderRefresh refresh, WorldClient targetWorld) {
        if (refresh == null || refresh.world == null || targetWorld == null || refresh.world != targetWorld || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return false;
        }

        ChunkProviderClient chunkProvider = MinecraftReflectionCompat.call(targetWorld, ChunkProviderClient.class, null, new String[]{"func_72863_F", "getChunkProvider"}, MinecraftReflectionCompat.NO_PARAMETERS);
        Chunk chunk = chunkProvider != null ? MinecraftReflectionCompat.call(chunkProvider, Chunk.class, null, new String[]{"func_186026_b", "getLoadedChunk"},
                new Class<?>[]{int.class, int.class}, refresh.chunkX, refresh.chunkZ) : null;
        boolean loaded = chunk != null;
        ClientChunkRenderScheduleResult scheduleResult = ClientChunkRenderScheduleResult.empty();
        if (loaded) {
            ensureVanillaTerrainRenderer(targetWorld, true);
            if (MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor accessor) {
                ViewFrustum viewFrustum = accessor.ausm$viewFrustum();
                updateVanillaViewFrustumChunkPositions(
                        viewFrustum,
                        MinecraftReflectionCompat.renderViewEntity(mc)
                );
                scheduleResult = self().scheduleLoadedClientChunkRenderChunks(
                        accessor,
                        viewFrustum,
                        targetWorld,
                        chunk,
                        refresh.chunkX,
                        refresh.chunkZ,
                        refresh.nextSectionY,
                        MAX_CLIENT_CHUNK_RENDER_REFRESH_SECTIONS_PER_FRAME
                );
                refresh.nextSectionY = scheduleResult.nextSectionY;
                refresh.coveredSections += scheduleResult.coveredSections;
                if (scheduleResult.completed && refresh.coveredSections < scheduleResult.requiredSections) {
                    refresh.nextSectionY = 0;
                    refresh.coveredSections = 0;
                }
                if (scheduleResult.scheduledChunks > 0) {
                    accessor.ausm$setDisplayListEntitiesDirty(true);
                }
            }

            if ((isPipelineActive || CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM.equals(refresh.reason))
                    && !refresh.shadowRefreshed
                    && !NothiriumBypass.shouldBypass()) {
                nothiriumShadowRenderer.refreshChunkColumn(refresh.chunkX, refresh.chunkZ);
                refresh.shadowRefreshed = true;
            }
        }

        self().logClientChunkRenderRefresh(refresh, loaded, scheduleResult.scheduledChunks);
        if (loaded && scheduleResult.completed && scheduleResult.coveredSections >= scheduleResult.requiredSections) {
            self().rememberCompletedClientChunkRenderRefresh(refresh.world, refresh.chunkX, refresh.chunkZ);
        }
        return !loaded || !scheduleResult.completed || refresh.coveredSections < scheduleResult.requiredSections;
    }

    protected ClientChunkRenderScheduleResult scheduleLoadedClientChunkRenderChunks(RenderGlobalAccessor renderGlobal,
                                                                                    ViewFrustum viewFrustum,
                                                                                    World world, Chunk chunk,
                                                                                    int chunkX, int chunkZ,
                                                                                    int startSectionY,
                                                                                    int sectionBudget) {
        int requiredSections = self().countNonEmptyClientChunkSections(chunk);
        if (requiredSections == 0) {
            return new ClientChunkRenderScheduleResult(0, 0, 0, true, requiredSections);
        }
        RenderChunk[] renderChunks = MinecraftReflectionCompat.viewFrustumRenderChunks(viewFrustum);
        if (renderGlobal == null || renderChunks == null) {
            return ClientChunkRenderScheduleResult.empty();
        }

        Set<RenderChunk> chunksToUpdate = renderGlobal.ausm$chunksToUpdate();
        if (chunksToUpdate == null) {
            return ClientChunkRenderScheduleResult.empty();
        }

        if (viewFrustum instanceof ViewFrustumAccessor accessor) {
            return self().scheduleLoadedClientChunkRenderChunksIndexed(accessor, renderChunks,
                    chunksToUpdate, world, chunk, chunkX, chunkZ, requiredSections, startSectionY, sectionBudget);
        }

        int scheduled = 0;
        int covered = 0;
        int processed = 0;
        int maxSections = PipelineContext.maxClientChunkRefreshSections(sectionBudget);
        ExtendedBlockStorage[] sections = MinecraftReflectionCompat.chunkBlockStorageArray(chunk);
        int sectionCount = sections != null ? sections.length : 0;
        int start = PipelineContext.clampSectionCursor(startSectionY, sectionCount);
        for (int sectionY = start; sectionY < sectionCount; sectionY++) {
            if (!PipelineContext.hasNonEmptyClientChunkSection(sections, sectionY)) {
                continue;
            }
            RenderChunk renderChunk = self().findRenderChunkForSection(renderChunks, chunkX, chunkZ, sectionY);
            processed++;
            if (renderChunk == null) {
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            covered++;
            self().assignRenderChunkWorld(renderChunk, world);
            if (MinecraftReflectionCompat.callBoolean(renderChunk, new String[]{"func_178571_g", "needsUpdate"}, MinecraftReflectionCompat.NO_PARAMETERS, false) || chunksToUpdate.contains(renderChunk)) {
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            MinecraftReflectionCompat.invoke(renderChunk, new String[]{"func_178575_a", "setNeedsUpdate"}, new Class<?>[]{boolean.class}, true);
            chunksToUpdate.add(renderChunk);
            scheduled++;
            if (processed >= maxSections) {
                return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
            }
        }
        return new ClientChunkRenderScheduleResult(scheduled, covered, 0, true, requiredSections);
    }

    protected ClientChunkRenderScheduleResult scheduleLoadedClientChunkRenderChunksIndexed(ViewFrustumAccessor viewFrustum,
                                                                                           RenderChunk[] renderChunks,
                                                                                           Set<RenderChunk> chunksToUpdate,
                                                                                           World world,
                                                                                           Chunk chunk,
                                                                                           int chunkX,
                                                                                           int chunkZ,
                                                                                           int requiredSections,
                                                                                           int startSectionY,
                                                                                           int sectionBudget) {
        int countX = viewFrustum.ausm$countChunksX();
        int countY = viewFrustum.ausm$countChunksY();
        int countZ = viewFrustum.ausm$countChunksZ();
        if (countX <= 0 || countY <= 0 || countZ <= 0 || renderChunks == null) {
            return ClientChunkRenderScheduleResult.empty();
        }

        int xIndex = PipelineContext.floorDiv(chunkX, countX);
        xIndex = chunkX - xIndex * countX;
        if (xIndex < 0) {
            xIndex += countX;
        }
        int zIndex = PipelineContext.floorDiv(chunkZ, countZ);
        zIndex = chunkZ - zIndex * countZ;
        if (zIndex < 0) {
            zIndex += countZ;
        }

        int scheduled = 0;
        int covered = 0;
        int processed = 0;
        int maxSections = PipelineContext.maxClientChunkRefreshSections(sectionBudget);
        ExtendedBlockStorage[] sections = MinecraftReflectionCompat.chunkBlockStorageArray(chunk);
        int sectionCount = Math.min(countY, sections != null ? sections.length : 0);
        int start = PipelineContext.clampSectionCursor(startSectionY, sectionCount);
        for (int sectionY = start; sectionY < sectionCount; sectionY++) {
            if (!PipelineContext.hasNonEmptyClientChunkSection(sections, sectionY)) {
                continue;
            }
            int index = (zIndex * countY + sectionY) * countX + xIndex;
            if (index < 0 || index >= renderChunks.length) {
                processed++;
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }

            RenderChunk renderChunk = renderChunks[index];
            BlockPos position = renderChunk != null ? MinecraftReflectionCompat.renderChunkPosition(renderChunk) : null;
            if (renderChunk == null
                    || position == null
                    || (MinecraftReflectionCompat.blockPosX(position) >> 4) != chunkX
                    || (MinecraftReflectionCompat.blockPosZ(position) >> 4) != chunkZ
                    || !self().shouldScheduleLoadedClientRenderChunk(renderChunk, chunk, position)) {
                processed++;
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            processed++;
            covered++;
            self().assignRenderChunkWorld(renderChunk, world);
            if (MinecraftReflectionCompat.callBoolean(renderChunk, new String[]{"func_178571_g", "needsUpdate"}, MinecraftReflectionCompat.NO_PARAMETERS, false) || chunksToUpdate.contains(renderChunk)) {
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            MinecraftReflectionCompat.invoke(renderChunk, new String[]{"func_178575_a", "setNeedsUpdate"}, new Class<?>[]{boolean.class}, true);
            chunksToUpdate.add(renderChunk);
            scheduled++;
            if (processed >= maxSections) {
                return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
            }
        }
        return new ClientChunkRenderScheduleResult(scheduled, covered, 0, true, requiredSections);
    }
}
