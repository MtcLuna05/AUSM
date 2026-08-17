package com.l.ausm.impl.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.compat.NothiriumBypass;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderList;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.client.renderer.chunk.ListChunkFactory;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.chunk.VboChunkFactory;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_STEADY_VANILLA_TERRAIN_DIAGNOSTIC_LOGS;

abstract class PipelineVanillaTerrainMaintenance extends PipelineBetterPortalsUpdates {
    protected void rebuildMainWorldVanillaViewFrustum(RenderGlobal renderGlobal, World world, String stagePrefix) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || world == null) {
            return;
        }

        self().pruneBetterPortalsVanillaViewFrustumCache(renderGlobal, world);
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.gameSettings(mc) == null) {
            return;
        }

        boolean worldChanged = self().syncRenderGlobalWorld(renderGlobal, world);
        boolean useVbo = MinecraftReflectionCompat.callBoolean(OpenGlHelper.class, new String[]{"func_176075_f", "useVbo"}, MinecraftReflectionCompat.NO_PARAMETERS, true);
        if (accessor.ausm$renderDispatcher() == null) {
            accessor.ausm$setRenderDispatcher(new ChunkRenderDispatcher());
        }
        IRenderChunkFactory renderChunkFactory = accessor.ausm$renderChunkFactory();
        if (renderChunkFactory == null) {
            renderChunkFactory = useVbo ? new VboChunkFactory() : new ListChunkFactory();
            accessor.ausm$setRenderChunkFactory(renderChunkFactory);
        }
        if (accessor.ausm$renderContainer() == null) {
            accessor.ausm$setRenderContainer(useVbo ? new VboRenderList() : new RenderList());
        }

        int renderDistanceChunks = MinecraftReflectionCompat.renderDistanceChunks(mc);
        ViewFrustum previousActive = accessor.ausm$viewFrustum();
        Set<ViewFrustum> removedViewFrustums = new HashSet<>();
        if (previousActive != null) {
            removedViewFrustums.add(previousActive);
        }
        for (Map<World, ViewFrustum> rendererViewFrustums : vanillaViewFrustums.values()) {
            ViewFrustum removed = rendererViewFrustums.remove(world);
            if (removed != null) {
                removedViewFrustums.add(removed);
            }
        }
        for (Map<World, Integer> rendererViewFrustumDistances : vanillaViewFrustumRenderDistances.values()) {
            rendererViewFrustumDistances.remove(world);
        }

        ViewFrustum freshViewFrustum = new ViewFrustum(
                world,
                renderDistanceChunks,
                renderGlobal,
                renderChunkFactory
        );
        accessor.ausm$setViewFrustum(freshViewFrustum);

        vanillaViewFrustums
                .computeIfAbsent(renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, freshViewFrustum);
        vanillaViewFrustumRenderDistances
                .computeIfAbsent(renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, renderDistanceChunks);
        self().rememberStableMainWorldVanillaRenderDistance(world, renderDistanceChunks);
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = renderGlobal;
        activeVanillaViewFrustumWorld = world;
        activeVanillaViewFrustumRenderDistanceChunks = renderDistanceChunks;

        int scheduledChunks = self().scheduleAllFreshViewFrustumChunks(accessor, freshViewFrustum, world);
        self().forceUpdateVanillaViewFrustumChunkPositions(freshViewFrustum, MinecraftReflectionCompat.renderViewEntity(mc), world, stagePrefix);
        accessor.ausm$setDisplayListEntitiesDirty(true);

        self().clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
        for (ViewFrustum removedViewFrustum : removedViewFrustums) {
            if (removedViewFrustum != null && removedViewFrustum != freshViewFrustum) {
                vanillaViewFrustumChunkPositionKeys.remove(removedViewFrustum);
                MinecraftReflectionCompat.deleteViewFrustumGlResources(removedViewFrustum);
            }
        }

        self().logTerrainDiagnostic(stagePrefix + ":rebuild-view-frustum", world,
                "previous=" + PipelineWorldRenderScope.viewFrustumId(previousActive)
                        + ", current=" + PipelineWorldRenderScope.viewFrustumId(freshViewFrustum)
                        + ", renderDistance=" + renderDistanceChunks
                        + ", scheduledChunks=" + scheduledChunks
                        + ", worldChanged=" + worldChanged);
    }

    protected int scheduleAllFreshViewFrustumChunks(RenderGlobalAccessor renderGlobal, ViewFrustum viewFrustum, World world) {
        RenderChunk[] renderChunks = MinecraftReflectionCompat.viewFrustumRenderChunks(viewFrustum);
        if (renderGlobal == null || renderChunks == null) {
            return 0;
        }

        Set<RenderChunk> chunksToUpdate = renderGlobal.ausm$chunksToUpdate();
        if (chunksToUpdate == null) {
            return 0;
        }

        chunksToUpdate.clear();
        int scheduled = 0;
        for (RenderChunk renderChunk : renderChunks) {
            if (renderChunk == null) {
                continue;
            }
            self().assignRenderChunkWorld(renderChunk, world);
            MinecraftReflectionCompat.invoke(renderChunk, new String[]{"func_178575_a", "setNeedsUpdate"}, new Class<?>[]{boolean.class}, true);
            chunksToUpdate.add(renderChunk);
            scheduled++;
        }
        return scheduled;
    }

    protected void clearQueuedUpdatesForViewFrustums(RenderGlobalAccessor renderGlobal, Set<ViewFrustum> viewFrustums) {
        if (renderGlobal == null || viewFrustums == null || viewFrustums.isEmpty()) {
            return;
        }

        Set<RenderChunk> chunksToUpdate = renderGlobal.ausm$chunksToUpdate();
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return;
        }

        Set<RenderChunk> removedChunks = new HashSet<>();
        for (ViewFrustum viewFrustum : viewFrustums) {
            RenderChunk[] renderChunks = MinecraftReflectionCompat.viewFrustumRenderChunks(viewFrustum);
            if (renderChunks == null) {
                continue;
            }
            for (RenderChunk renderChunk : renderChunks) {
                if (renderChunk != null) {
                    removedChunks.add(renderChunk);
                }
            }
        }
        if (!removedChunks.isEmpty()) {
            chunksToUpdate.removeAll(removedChunks);
        }
    }

    protected void forceUpdateVanillaViewFrustumChunkPositions(ViewFrustum viewFrustum, Entity viewEntity, World world, String stagePrefix) {
        if (viewFrustum == null || viewEntity == null) {
            return;
        }

        try {
            MinecraftReflectionCompat.invoke(viewFrustum, new String[]{"func_178163_a", "updateChunkPositions"},
                    new Class<?>[]{double.class, double.class}, MinecraftReflectionCompat.posX(viewEntity), MinecraftReflectionCompat.posZ(viewEntity));
            self().rememberVanillaViewFrustumChunkPosition(viewFrustum, viewEntity);
        } catch (NullPointerException e) {
            if (!BetterPortalsCompat.isInstalled()) {
                throw e;
            }
            self().logTerrainDiagnostic(stagePrefix + ":deferred-chunk-positions", world, e.getClass().getSimpleName());
        }
    }

    protected void logSteadyVanillaTerrainDiagnostic(String stage, World world, String detail) {
        if (steadyVanillaTerrainDiagnosticLogs >= MAX_STEADY_VANILLA_TERRAIN_DIAGNOSTIC_LOGS) {
            return;
        }
        steadyVanillaTerrainDiagnosticLogs++;
        self().logTerrainDiagnostic(stage, world, detail);
    }

    protected void logShaderlessNothiriumLoadRendererReload(World world, boolean marked, String reason) {
        if (shaderlessNothiriumLoadRendererReloadLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        shaderlessNothiriumLoadRendererReloadLogs++;

        MainMod.LOGGER.info(
                "[AUSMNothiriumReload] loadRenderers bridge call={} reason={} world={} marked={} active={} bypass={} nested={} renderPass={} caller={}",
                shaderlessNothiriumLoadRendererReloadLogs,
                reason,
                safeDimensionId(world),
                marked,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isRenderingNestedView(),
                BetterPortalsCompat.isRenderingRenderPass(),
                self().externalRenderCaller()
        );
    }

    public void logRenderGlobalLoadRenderers(RenderGlobal renderGlobal) {
        if (renderGlobalLoadRendererLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        renderGlobalLoadRendererLogs++;

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World renderGlobalWorld = renderGlobal instanceof RenderGlobalAccessor accessor ? accessor.ausm$world() : null;
        MainMod.LOGGER.info(
                "[AUSMRenderGlobal] loadRenderers call={} frame={} renderGlobalWorld={} clientWorld={} active={} bypass={} nested={} renderPass={} recovery={} pendingAttempts={} pendingDelay={} pendingDim={} pendingReset={} pendingFullReset={} pendingVanillaReload={} bpState={} caller={}",
                renderGlobalLoadRendererLogs,
                pipelineFrameId,
                safeDimensionId(renderGlobalWorld),
                mc != null ? safeDimensionId(MinecraftReflectionCompat.world(mc)) : Integer.MIN_VALUE,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isRenderingNestedView(),
                BetterPortalsCompat.isRenderingRenderPass(),
                BetterPortalsCompat.isMainViewSwapRecoveryActive(),
                pendingWorldTerrainRefreshAttempts,
                pendingWorldTerrainRefreshDelay,
                pendingWorldTerrainRefreshDimension,
                pendingWorldTerrainRendererReset,
                pendingWorldTerrainFullRendererReset,
                pendingWorldTerrainVanillaReload,
                BetterPortalsCompat.describeTransitionState(),
                self().externalRenderCaller()
        );
    }

    protected void logTerrainDiagnostic(String stage, World world, String detail) {
        // Diagnostic disabled.
    }

    protected void logVanillaTerrainRendererCreation(World world, boolean force, String reason) {
        if (vanillaTerrainRendererCreationLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        vanillaTerrainRendererCreationLogs++;

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        MainMod.LOGGER.info(
                "[AUSMRenderGlobal] created vanilla ChunkRenderDispatcher call={} reason={} force={} world={} clientWorld={} active={} bypass={} nested={} renderPass={} recovery={} caller={}",
                vanillaTerrainRendererCreationLogs,
                reason,
                force,
                safeDimensionId(world),
                mc != null ? safeDimensionId(MinecraftReflectionCompat.world(mc)) : Integer.MIN_VALUE,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isRenderingNestedView(),
                BetterPortalsCompat.isRenderingRenderPass(),
                BetterPortalsCompat.isMainViewSwapRecoveryActive(),
                self().externalRenderCaller()
        );
    }

    protected static String viewFrustumId(ViewFrustum viewFrustum) {
        return viewFrustum != null ? Integer.toHexString(System.identityHashCode(viewFrustum)) : "null";
    }

    protected static String blockName(IBlockState state) {
        return state != null && MinecraftReflectionCompat.blockFromState(state) != null ? String.valueOf(MinecraftReflectionCompat.blockRegistryName(MinecraftReflectionCompat.blockFromState(state))) : "null";
    }

    protected String externalRenderCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(PipelineContext.class.getName())
                    || className.equals("com.l.ausm.impl.mixin.pipeline.RenderSkyMixin")
                    || className.equals("net.minecraft.client.renderer.RenderGlobal")) {
                continue;
            }
            return className + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "unknown";
    }

    protected void updateVanillaViewFrustumChunkPositions(ViewFrustum viewFrustum, Entity viewEntity) {
        if (viewFrustum == null || viewEntity == null) {
            return;
        }

        if (!self().shouldUpdateVanillaViewFrustumChunkPositions(viewFrustum, viewEntity)) {
            return;
        }

        if (BetterPortalsCompat.isMainViewSwapHandling()) {
            return;
        }

        if (BetterPortalsCompat.isInstalled() && !BetterPortalsCompat.isRenderingRenderPass()) {
            return;
        }

        try {
            MinecraftReflectionCompat.invoke(viewFrustum, new String[]{"func_178163_a", "updateChunkPositions"},
                    new Class<?>[]{double.class, double.class}, MinecraftReflectionCompat.posX(viewEntity), MinecraftReflectionCompat.posZ(viewEntity));
            self().rememberVanillaViewFrustumChunkPosition(viewFrustum, viewEntity);
        } catch (NullPointerException e) {
            if (!BetterPortalsCompat.isInstalled()) {
                throw e;
            }
            if (!betterPortalsViewFrustumUpdateWarningLogged) {
                betterPortalsViewFrustumUpdateWarningLogged = true;
                MainMod.LOGGER.warn("[BetterPortalsCompat] Deferred vanilla ViewFrustum chunk-position update because Better Portals has no active render pass", e);
            }
        }
    }

    protected boolean shouldUpdateVanillaViewFrustumChunkPositions(ViewFrustum viewFrustum, Entity viewEntity) {
        Long previous = vanillaViewFrustumChunkPositionKeys.get(viewFrustum);
        if (previous == null) {
            return true;
        }
        return previous.longValue() != self().vanillaViewFrustumChunkPositionKey(viewEntity);
    }

    protected void rememberVanillaViewFrustumChunkPosition(ViewFrustum viewFrustum, Entity viewEntity) {
        if (viewFrustum == null || viewEntity == null) {
            return;
        }
        vanillaViewFrustumChunkPositionKeys.put(viewFrustum, self().vanillaViewFrustumChunkPositionKey(viewEntity));
    }

    protected long vanillaViewFrustumChunkPositionKey(Entity viewEntity) {
        int chunkX = (int) Math.floor(MinecraftReflectionCompat.posX(viewEntity)) >> 4;
        int chunkZ = (int) Math.floor(MinecraftReflectionCompat.posZ(viewEntity)) >> 4;
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    protected void deleteCachedVanillaTerrainRenderers() {
        if (vanillaViewFrustums.isEmpty()) {
            vanillaViewFrustumRenderDistances.clear();
            vanillaViewFrustumChunkPositionKeys.clear();
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
            lastStableMainWorldVanillaRenderDistanceChunks = -1;
            return;
        }

        Set<ViewFrustum> uniqueViewFrustums = new HashSet<>();
        for (Map.Entry<RenderGlobal, Map<World, ViewFrustum>> rendererEntry : vanillaViewFrustums.entrySet()) {
            Map<World, ViewFrustum> rendererViewFrustums = rendererEntry.getValue();
            if (rendererEntry.getKey() instanceof RenderGlobalAccessor accessor && rendererViewFrustums != null) {
                self().clearQueuedUpdatesForViewFrustums(accessor, new HashSet<>(rendererViewFrustums.values()));
            }
            if (rendererViewFrustums == null) {
                continue;
            }
            uniqueViewFrustums.addAll(rendererViewFrustums.values());
        }
        for (ViewFrustum viewFrustum : uniqueViewFrustums) {
            if (viewFrustum != null) {
                vanillaViewFrustumChunkPositionKeys.remove(viewFrustum);
                MinecraftReflectionCompat.deleteViewFrustumGlResources(viewFrustum);
            }
        }
        vanillaViewFrustums.clear();
        vanillaViewFrustumRenderDistances.clear();
        vanillaViewFrustumChunkPositionKeys.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
        lastStableMainWorldVanillaRenderDistanceChunks = -1;
    }

    protected void clearCachedVanillaTerrainRendererReferences() {
        vanillaViewFrustums.clear();
        vanillaViewFrustumRenderDistances.clear();
        vanillaViewFrustumChunkPositionKeys.clear();
        self().clearShaderlessBloomMetadata();
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
    }

    protected void deleteCachedVanillaTerrainRenderer(World world) {
        if (world == null || vanillaViewFrustums.isEmpty()) {
            if (world == null) {
                vanillaViewFrustumRenderDistances.clear();
            }
            if (activeVanillaViewFrustumWorld == world) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
            }
            return;
        }

        for (Map.Entry<RenderGlobal, Map<World, ViewFrustum>> rendererEntry : vanillaViewFrustums.entrySet()) {
            Map<World, ViewFrustum> rendererViewFrustums = rendererEntry.getValue();
            if (rendererViewFrustums == null) {
                continue;
            }
            ViewFrustum removed = rendererViewFrustums.remove(world);
            if (removed != null) {
                if (rendererEntry.getKey() instanceof RenderGlobalAccessor accessor) {
                    Set<ViewFrustum> removedViewFrustums = new HashSet<>();
                    removedViewFrustums.add(removed);
                    self().clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
                }
                vanillaViewFrustumChunkPositionKeys.remove(removed);
                MinecraftReflectionCompat.deleteViewFrustumGlResources(removed);
            }
        }
        for (Map<World, Integer> rendererViewFrustumDistances : vanillaViewFrustumRenderDistances.values()) {
            rendererViewFrustumDistances.remove(world);
        }
        if (activeVanillaViewFrustumWorld == world) {
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
        }
    }

    protected void pruneBetterPortalsVanillaViewFrustumCache(RenderGlobal currentRenderGlobal, World primaryWorld) {
        if (!BetterPortalsCompat.isInstalled()
                || currentRenderGlobal == null
                || primaryWorld == null
                || vanillaViewFrustums.isEmpty()) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World mainWorld = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        if (self().countCachedVanillaViewFrustums() <= 2) {
            return;
        }
        if (BetterPortalsCompat.isRenderingRenderPass() || BetterPortalsCompat.isRenderingNestedView()) {
            return;
        }

        ViewFrustum activeViewFrustum = currentRenderGlobal instanceof RenderGlobalAccessor accessor
                ? accessor.ausm$viewFrustum()
                : null;
        Set<ViewFrustum> removedViewFrustums = new HashSet<>();

        Iterator<Map.Entry<RenderGlobal, Map<World, ViewFrustum>>> rendererIterator =
                vanillaViewFrustums.entrySet().iterator();
        while (rendererIterator.hasNext()) {
            Map.Entry<RenderGlobal, Map<World, ViewFrustum>> rendererEntry = rendererIterator.next();
            Map<World, ViewFrustum> rendererViewFrustums = rendererEntry.getValue();
            if (rendererViewFrustums == null || rendererViewFrustums.isEmpty()) {
                rendererIterator.remove();
                continue;
            }

            Iterator<Map.Entry<World, ViewFrustum>> worldIterator = rendererViewFrustums.entrySet().iterator();
            while (worldIterator.hasNext()) {
                Map.Entry<World, ViewFrustum> worldEntry = worldIterator.next();
                World cachedWorld = worldEntry.getKey();
                if (cachedWorld == primaryWorld
                        || cachedWorld == mainWorld
                        || cachedWorld == activeVanillaViewFrustumWorld) {
                    continue;
                }

                ViewFrustum removed = worldEntry.getValue();
                if (removed != null && removed != activeViewFrustum) {
                    removedViewFrustums.add(removed);
                }
                worldIterator.remove();
            }

            if (rendererViewFrustums.isEmpty()) {
                rendererIterator.remove();
            }
        }

        Iterator<Map.Entry<RenderGlobal, Map<World, Integer>>> distanceRendererIterator =
                vanillaViewFrustumRenderDistances.entrySet().iterator();
        while (distanceRendererIterator.hasNext()) {
            Map.Entry<RenderGlobal, Map<World, Integer>> rendererEntry = distanceRendererIterator.next();
            Map<World, Integer> rendererViewFrustumDistances = rendererEntry.getValue();
            if (rendererViewFrustumDistances == null || rendererViewFrustumDistances.isEmpty()) {
                distanceRendererIterator.remove();
                continue;
            }

            rendererViewFrustumDistances.keySet().removeIf(cachedWorld ->
                    cachedWorld != primaryWorld
                            && cachedWorld != mainWorld
                            && cachedWorld != activeVanillaViewFrustumWorld
            );
            if (rendererViewFrustumDistances.isEmpty()) {
                distanceRendererIterator.remove();
            }
        }

        if (currentRenderGlobal instanceof RenderGlobalAccessor accessor) {
            self().clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
        }
        for (ViewFrustum removedViewFrustum : removedViewFrustums) {
            vanillaViewFrustumChunkPositionKeys.remove(removedViewFrustum);
            MinecraftReflectionCompat.deleteViewFrustumGlResources(removedViewFrustum);
        }

        if (!removedViewFrustums.isEmpty()) {
            if (activeVanillaViewFrustumWorld != primaryWorld
                    && activeVanillaViewFrustumRenderGlobal != currentRenderGlobal) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
            }
            self().logTerrainDiagnostic("prune-vanilla-frustums", primaryWorld,
                    "removed=" + removedViewFrustums.size()
                            + ", mainWorld=" + safeDimensionId(mainWorld)
                            + ", primaryWorld=" + safeDimensionId(primaryWorld));
        }
    }

    protected int countCachedVanillaViewFrustums() {
        Set<ViewFrustum> uniqueViewFrustums = new HashSet<>();
        for (Map<World, ViewFrustum> rendererViewFrustums : vanillaViewFrustums.values()) {
            if (rendererViewFrustums != null) {
                uniqueViewFrustums.addAll(rendererViewFrustums.values());
            }
        }
        return uniqueViewFrustums.size();
    }

    protected void refreshBetterPortalsMainViewTerrain(Minecraft mc, String reason) {
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }
        if (!isPipelineActive) {
            self().logInactiveBetterPortalsTerrainSkip("refresh-main-view-terrain", MinecraftReflectionCompat.world(mc));
            return;
        }

        try {
            self().logTerrainDiagnostic("bp-refresh-main-view:start", MinecraftReflectionCompat.world(mc), "");
            boolean worldChanged = self().syncRenderGlobalWorld(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc));
            self().adoptMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), reason);
            self().resetCameraFrustumSyncState();
            self().logTerrainDiagnostic("bp-refresh-main-view:end", MinecraftReflectionCompat.world(mc), "worldChanged=" + worldChanged);
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to refresh terrain after main view swap", e);
        }
    }
}
