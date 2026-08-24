package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.compat.NothiriumBypass;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderList;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.client.renderer.chunk.ListChunkFactory;
import net.minecraft.client.renderer.chunk.VboChunkFactory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.BETTER_PORTALS_PORTAL_BLOCK_REFRESH_DEBOUNCE_MS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.BETTER_PORTALS_VANILLA_RENDER_DISTANCE_CAP;

abstract class PipelineBetterPortalsUpdates extends PipelineShadowPolicy {
    public void runPendingBetterPortalsPortalBlockRefresh() {
        if (pendingBetterPortalsPortalBlockRefreshDelay < 0) {
            return;
        }
        if (pendingBetterPortalsPortalBlockRefreshDelay > 0) {
            pendingBetterPortalsPortalBlockRefreshDelay--;
            return;
        }

        World world = pendingBetterPortalsPortalBlockWorld;
        BlockPos pos = pendingBetterPortalsPortalBlockPos;
        IBlockState oldState = pendingBetterPortalsPortalBlockOldState;
        IBlockState newState = pendingBetterPortalsPortalBlockNewState;
        int changeCount = pendingBetterPortalsPortalBlockChangeCount;

        pendingBetterPortalsPortalBlockWorld = null;
        pendingBetterPortalsPortalBlockPos = null;
        pendingBetterPortalsPortalBlockOldState = null;
        pendingBetterPortalsPortalBlockNewState = null;
        pendingBetterPortalsPortalBlockChangeCount = 0;
        pendingBetterPortalsPortalBlockRefreshDelay = -1;

        self().handleBetterPortalsPortalBlockChanged(world, pos, oldState, newState, changeCount);
    }

    public void handleBetterPortalsPortalBlockChanged(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        self().handleBetterPortalsPortalBlockChanged(world, pos, oldState, newState, 1);
    }

    protected void handleBetterPortalsPortalBlockChanged(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int changeCount) {
        if (!BetterPortalsCompat.isInstalled() || world == null || pos == null) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        BetterPortalsCompat.beginMainViewSwapHandling();
        try {
            self().rememberBetterPortalsPortalBlockRefresh(world, pos);
            self().markPortalChangeRenderRegion(world, pos);
            self().logTerrainDiagnostic("bp-portal-block:refresh", world, "pos=" + pos
                    + ", count=" + Math.max(1, changeCount)
                    + ", old=" + PipelineWorldRenderScope.blockName(oldState)
                    + ", new=" + PipelineWorldRenderScope.blockName(newState));
            MainMod.LOGGER.debug("[BetterPortalsCompat] Refreshed portal terrain after {} coalesced block change(s): world={} pos={} old={} new={}",
                    Math.max(1, changeCount),
                    safeDimensionId(world),
                    pos,
                    oldState != null ? MinecraftReflectionCompat.blockRegistryName(MinecraftReflectionCompat.blockFromState(oldState)) : "null",
                    newState != null ? MinecraftReflectionCompat.blockRegistryName(MinecraftReflectionCompat.blockFromState(newState)) : "null");
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to refresh portal terrain after block change", e);
        } finally {
            BetterPortalsCompat.endMainViewSwapHandling();
        }
    }

    protected boolean sameBlockState(IBlockState oldState, IBlockState newState) {
        return oldState == newState || (oldState != null && oldState.equals(newState));
    }

    protected boolean shouldDebounceBetterPortalsPortalBlockRefresh(World world, BlockPos pos) {
        long now = System.currentTimeMillis();
        return lastBetterPortalsPortalBlockRefreshWorld == world
                && lastBetterPortalsPortalBlockRefreshDimension == safeDimensionId(world)
                && pos.equals(lastBetterPortalsPortalBlockRefreshPos)
                && now - lastBetterPortalsPortalBlockRefreshMillis >= 0L
                && now - lastBetterPortalsPortalBlockRefreshMillis < BETTER_PORTALS_PORTAL_BLOCK_REFRESH_DEBOUNCE_MS;
    }

    protected void rememberBetterPortalsPortalBlockRefresh(World world, BlockPos pos) {
        lastBetterPortalsPortalBlockRefreshWorld = world;
        lastBetterPortalsPortalBlockRefreshPos = pos != null ? MinecraftReflectionCompat.blockPosToImmutable(pos) : null;
        lastBetterPortalsPortalBlockRefreshDimension = safeDimensionId(world);
        lastBetterPortalsPortalBlockRefreshMillis = System.currentTimeMillis();
    }

    protected void markPortalChangeRenderRegion(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }

        int radius = 8;
        MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(world,
                MinecraftReflectionCompat.blockPosX(pos) - radius,
                Math.max(0, MinecraftReflectionCompat.blockPosY(pos) - radius),
                MinecraftReflectionCompat.blockPosZ(pos) - radius,
                MinecraftReflectionCompat.blockPosX(pos) + radius,
                Math.min(255, MinecraftReflectionCompat.blockPosY(pos) + radius),
                MinecraftReflectionCompat.blockPosZ(pos) + radius
        );
    }

    protected void ensureVanillaTerrainRenderer(World world, boolean force) {
        boolean bypass = NothiriumBypass.shouldBypass();
        if (!force && !bypass) {
            self().logSteadyVanillaTerrainDiagnostic("ensure-vanilla:skip", world, "force=false, nothiriumBypass=false");
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || world == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        self().logSteadyVanillaTerrainDiagnostic("ensure-vanilla:start", world, "force=" + force + ", nothiriumBypass=" + bypass);
        RenderGlobal currentRenderGlobal = MinecraftReflectionCompat.renderGlobal(mc);
        RenderGlobalAccessor renderGlobal = (RenderGlobalAccessor) currentRenderGlobal;
        int requestedRenderDistanceChunks = MinecraftReflectionCompat.renderDistanceChunks(mc);
        Integer activeRenderDistanceChunks = activeVanillaViewFrustumRenderDistanceChunks > 0
                ? activeVanillaViewFrustumRenderDistanceChunks
                : null;
        int expectedRenderDistanceChunks = self().vanillaTerrainRenderDistanceChunks(
                world,
                activeRenderDistanceChunks,
                requestedRenderDistanceChunks
        );
        if (self().canReuseActiveVanillaTerrainRenderer(renderGlobal, currentRenderGlobal, world, expectedRenderDistanceChunks)) {
            self().updateVanillaViewFrustumChunkPositions(renderGlobal.ausm$viewFrustum(), MinecraftReflectionCompat.renderViewEntity(mc));
            self().logSteadyVanillaTerrainDiagnostic("ensure-vanilla:reuse-active", world,
                    "renderDistance=" + expectedRenderDistanceChunks + ", force=" + force);
            return;
        }

        boolean rendererStateChanged = self().syncRenderGlobalWorld(currentRenderGlobal, world);
        ViewFrustum activeViewFrustum = renderGlobal.ausm$viewFrustum();
        self().pruneBetterPortalsVanillaViewFrustumCache(currentRenderGlobal, world);

        boolean useVbo = MinecraftReflectionCompat.callBoolean(OpenGlHelper.class, new String[]{"func_176075_f", "useVbo"}, MinecraftReflectionCompat.NO_PARAMETERS, true);
        if (renderGlobal.ausm$renderDispatcher() == null) {
            self().logVanillaTerrainRendererCreation(world, force, "missing-dispatcher");
            renderGlobal.ausm$setRenderDispatcher(new ChunkRenderDispatcher());
            rendererStateChanged = true;
        }
        IRenderChunkFactory renderChunkFactory = renderGlobal.ausm$renderChunkFactory();
        if (renderChunkFactory == null) {
            renderChunkFactory = useVbo ? new VboChunkFactory() : new ListChunkFactory();
            renderGlobal.ausm$setRenderChunkFactory(renderChunkFactory);
            rendererStateChanged = true;
        }
        if (renderGlobal.ausm$renderContainer() == null) {
            renderGlobal.ausm$setRenderContainer(useVbo ? new VboRenderList() : new RenderList());
            rendererStateChanged = true;
        }

        Map<World, ViewFrustum> rendererViewFrustums = vanillaViewFrustums.computeIfAbsent(
                currentRenderGlobal,
                ignored -> new IdentityHashMap<>()
        );
        Map<World, Integer> rendererViewFrustumDistances = vanillaViewFrustumRenderDistances.computeIfAbsent(
                currentRenderGlobal,
                ignored -> new IdentityHashMap<>()
        );
        ViewFrustum viewFrustum = rendererViewFrustums.get(world);
        Integer cachedRenderDistanceChunks = rendererViewFrustumDistances.get(world);
        int renderDistanceChunks = self().vanillaTerrainRenderDistanceChunks(
                world,
                cachedRenderDistanceChunks,
                requestedRenderDistanceChunks
        );
        if (viewFrustum != null && cachedRenderDistanceChunks != null && cachedRenderDistanceChunks != renderDistanceChunks) {
            Set<ViewFrustum> removedViewFrustums = new HashSet<>();
            removedViewFrustums.add(viewFrustum);
            self().clearQueuedUpdatesForViewFrustums(renderGlobal, removedViewFrustums);
            vanillaViewFrustumChunkPositionKeys.remove(viewFrustum);
            MinecraftReflectionCompat.deleteViewFrustumGlResources(viewFrustum);
            if (viewFrustum == activeViewFrustum) {
                activeViewFrustum = null;
            }
            rendererViewFrustums.remove(world);
            rendererViewFrustumDistances.remove(world);
            viewFrustum = null;
            if (activeVanillaViewFrustumRenderGlobal == currentRenderGlobal && activeVanillaViewFrustumWorld == world) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
            }
            rendererStateChanged = true;
            MainMod.LOGGER.info("[Pipeline] Rebuilt vanilla terrain renderer for render distance change: world={} old={} new={} requested={}",
                    safeDimensionId(world),
                    cachedRenderDistanceChunks,
                    renderDistanceChunks,
                    requestedRenderDistanceChunks);
        }
        if (viewFrustum == null) {
            if (activeVanillaViewFrustumRenderGlobal == currentRenderGlobal
                    && activeVanillaViewFrustumWorld == world
                    && activeViewFrustum != null) {
                viewFrustum = activeViewFrustum;
            } else {
                viewFrustum = new ViewFrustum(
                        world,
                        renderDistanceChunks,
                        MinecraftReflectionCompat.renderGlobal(mc),
                        renderChunkFactory
                );
            }
            rendererViewFrustums.put(world, viewFrustum);
            rendererViewFrustumDistances.put(world, renderDistanceChunks);
            rendererStateChanged = true;
        } else if (cachedRenderDistanceChunks == null) {
            rendererViewFrustumDistances.put(world, renderDistanceChunks);
        }

        self().updateVanillaViewFrustumChunkPositions(viewFrustum, MinecraftReflectionCompat.renderViewEntity(mc));
        if (activeViewFrustum != viewFrustum) {
            renderGlobal.ausm$setViewFrustum(viewFrustum);
            rendererStateChanged = true;
        }
        activeVanillaViewFrustumRenderGlobal = currentRenderGlobal;
        activeVanillaViewFrustumWorld = world;
        activeVanillaViewFrustumRenderDistanceChunks = renderDistanceChunks;
        self().rememberStableMainWorldVanillaRenderDistance(world, renderDistanceChunks);
        if (rendererStateChanged) {
            renderGlobal.ausm$setDisplayListEntitiesDirty(true);
        }
        String detail = "force=" + force
                + ", activeViewBefore=" + PipelineWorldRenderScope.viewFrustumId(activeViewFrustum)
                + ", activeViewAfter=" + PipelineWorldRenderScope.viewFrustumId(renderGlobal.ausm$viewFrustum())
                + ", cachedView=" + PipelineWorldRenderScope.viewFrustumId(viewFrustum)
                + ", renderDistance=" + renderDistanceChunks
                + (requestedRenderDistanceChunks != renderDistanceChunks
                ? ", requestedRenderDistance=" + requestedRenderDistanceChunks
                : "");
        if (rendererStateChanged) {
            self().logTerrainDiagnostic("ensure-vanilla:changed", world, detail);
        } else {
            self().logSteadyVanillaTerrainDiagnostic("ensure-vanilla:unchanged", world, detail);
        }
    }

    protected int vanillaTerrainRenderDistanceChunks(World world, Integer cachedRenderDistanceChunks,
                                                     int requestedRenderDistanceChunks) {
        if (self().shouldUseBetterPortalsPortalRenderDistance(world)) {
            return Math.min(requestedRenderDistanceChunks, BETTER_PORTALS_VANILLA_RENDER_DISTANCE_CAP);
        }
        if (self().shouldUseStableMainWorldRenderDistance(world)) {
            if (cachedRenderDistanceChunks != null && cachedRenderDistanceChunks > 0) {
                return cachedRenderDistanceChunks;
            }
            if (lastStableMainWorldVanillaRenderDistanceChunks > 0) {
                return lastStableMainWorldVanillaRenderDistanceChunks;
            }
        }
        return requestedRenderDistanceChunks;
    }

    protected boolean shouldUseBetterPortalsPortalRenderDistance(World world) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        return BetterPortalsCompat.isInstalled()
                && BetterPortalsCompat.isRenderingRenderPass()
                && mc != null
                && MinecraftReflectionCompat.world(mc) != null
                && world != null
                && world != MinecraftReflectionCompat.world(mc);
    }

    protected boolean canReuseActiveVanillaTerrainRenderer(RenderGlobalAccessor renderGlobal,
                                                           RenderGlobal currentRenderGlobal,
                                                           World world,
                                                           int renderDistanceChunks) {
        if (renderGlobal == null
                || currentRenderGlobal == null
                || world == null
                || renderDistanceChunks <= 0
                || activeVanillaViewFrustumRenderGlobal != currentRenderGlobal
                || activeVanillaViewFrustumWorld != world
                || activeVanillaViewFrustumRenderDistanceChunks != renderDistanceChunks) {
            return false;
        }
        if (self().countCachedVanillaViewFrustums() > 2) {
            return false;
        }
        return renderGlobal.ausm$world() == world
                && renderGlobal.ausm$viewFrustum() != null
                && renderGlobal.ausm$renderDispatcher() != null
                && renderGlobal.ausm$renderChunkFactory() != null
                && renderGlobal.ausm$renderContainer() != null;
    }

    protected boolean shouldUseStableMainWorldRenderDistance(World world) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        return BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && mc != null
                && MinecraftReflectionCompat.world(mc) != null
                && renderPassWorld == MinecraftReflectionCompat.world(mc)
                && world == MinecraftReflectionCompat.world(mc);
    }

    protected void rememberStableMainWorldVanillaRenderDistance(World world, int renderDistanceChunks) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || world != MinecraftReflectionCompat.world(mc) || renderDistanceChunks <= 0) {
            return;
        }
        if (BetterPortalsCompat.isRenderingRenderPass() || BetterPortalsCompat.isRenderingNestedView()) {
            return;
        }
        lastStableMainWorldVanillaRenderDistanceChunks = renderDistanceChunks;
    }

    public void handleRenderGlobalLoadRenderers(RenderGlobal renderGlobal) {
        self().handleShaderlessMainWorldNothiriumReload(renderGlobal);
        self().logRenderGlobalLoadRenderers(renderGlobal);
    }

    public void handleRenderGlobalLoadRenderersComplete(RenderGlobal renderGlobal) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        String caller = self().externalRenderCaller();
        boolean manualChunkReload = PipelineWorldRenderScope.isManualChunkReloadCaller(caller);
        if (renderGlobal == null
                || mc == null
                || MinecraftReflectionCompat.world(mc) == null
                || MinecraftReflectionCompat.renderGlobal(mc) != renderGlobal
                || !PipelineWorldRenderScope.isStableMainWorldLoadRenderersCaller(caller)
                || isPipelineActive
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return;
        }

        World renderGlobalWorld = renderGlobal instanceof RenderGlobalAccessor accessor ? accessor.ausm$world() : null;
        if (renderGlobalWorld != null && renderGlobalWorld != MinecraftReflectionCompat.world(mc)) {
            return;
        }

        if (self().shouldLeaveShaderlessVanillaTerrainUntouched()) {
            if (manualChunkReload) {
                self().rebuildMainWorldVanillaViewFrustum(renderGlobal, MinecraftReflectionCompat.world(mc), "manual-reload-vanilla-owner");
            } else {
                self().adoptMainWorldVanillaViewFrustum(renderGlobal, MinecraftReflectionCompat.world(mc), "main-load-vanilla-owner");
            }
            self().clearShaderlessBloomMetadata();
            self().scheduleWorldLoadLightRecalculation();
            return;
        }

        self().adoptMainWorldVanillaViewFrustum(renderGlobal, MinecraftReflectionCompat.world(mc), manualChunkReload ? "manual-reload" : "main-load");
        self().markShaderlessMainWorldNothiriumReload(MinecraftReflectionCompat.world(mc), manualChunkReload ? "manual-load-renderers" : "main-load-renderers");
        self().clearShaderlessBloomMetadata();
        self().scheduleInactiveVanillaRecoveryFrame();
    }

    protected void handleShaderlessMainWorldNothiriumReload(RenderGlobal renderGlobal) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        String caller = self().externalRenderCaller();
        if (renderGlobal == null
                || mc == null
                || MinecraftReflectionCompat.world(mc) == null
                || MinecraftReflectionCompat.renderGlobal(mc) != renderGlobal
                || !PipelineWorldRenderScope.isManualChunkReloadCaller(caller)
                || isPipelineActive
                || NothiriumBypass.shouldBypass()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return;
        }

        World renderGlobalWorld = renderGlobal instanceof RenderGlobalAccessor accessor ? accessor.ausm$world() : null;
        if (renderGlobalWorld != null && renderGlobalWorld != MinecraftReflectionCompat.world(mc)) {
            return;
        }

        self().markShaderlessMainWorldNothiriumReload(MinecraftReflectionCompat.world(mc), "manual-load-renderers");
    }

    protected void markShaderlessMainWorldNothiriumReload(World world, String reason) {
        if (world == null) {
            return;
        }
        if (self().shouldLeaveShaderlessVanillaTerrainUntouched()) {
            return;
        }

        int dimension = safeDimensionId(world);
        long now = System.currentTimeMillis();
        boolean debounced = dimension == lastShaderlessNothiriumLoadRendererReloadDimension
                && now - lastShaderlessNothiriumLoadRendererReloadMillis < 1000L;
        if (debounced) {
            self().logShaderlessNothiriumLoadRendererReload(world, false, "debounced");
            return;
        }

        lastShaderlessNothiriumLoadRendererReloadDimension = dimension;
        lastShaderlessNothiriumLoadRendererReloadMillis = now;
        boolean hardReset = self().shouldHardResetShaderlessNothirium(reason);
        if (hardReset) {
            self().clearCachedVanillaTerrainRendererReferences();
        }

        boolean marked = hardReset ? NothiriumBypass.recreateRenderer() : NothiriumBypass.markAllChanged();
        boolean setup = hardReset && marked && NothiriumBypass.setupForIsolatedShaderlessMainPass();
        if (marked || setup) {
            self().scheduleInactiveVanillaRecoveryFrame();
        }
        self().logShaderlessNothiriumLoadRendererReload(world, marked, reason);
        if (setup) {
            self().logTerrainDiagnostic(reason + ":shaderless-reload-setup", world, "marked=" + marked);
        }
    }

    protected boolean shouldHardResetShaderlessNothirium(String reason) {
        if (!BetterPortalsCompat.isInstalled() || isPipelineActive || reason == null) {
            return false;
        }
        return "dimension-switch".equals(reason)
                || "bp-main-view-swap".equals(reason)
                || "manual-load-renderers".equals(reason);
    }

    protected boolean shouldLeaveShaderlessVanillaTerrainUntouched() {
        return BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && NothiriumBypass.shouldBypass()
                && !BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    protected static boolean isManualChunkReloadCaller(String caller) {
        return caller != null && caller.startsWith("net.minecraft.client.Minecraft#func_184122_c:");
    }

    protected static boolean isStableMainWorldLoadRenderersCaller(String caller) {
        return PipelineWorldRenderScope.isManualChunkReloadCaller(caller)
                || caller != null && caller.startsWith("net.minecraft.client.Minecraft#func_71353_a:");
    }

    protected void adoptMainWorldVanillaViewFrustum(RenderGlobal renderGlobal, World world, String stagePrefix) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || world == null) {
            return;
        }

        self().pruneBetterPortalsVanillaViewFrustumCache(renderGlobal, world);
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        int renderDistanceChunks = mc != null && MinecraftReflectionCompat.gameSettings(mc) != null ? MinecraftReflectionCompat.renderDistanceChunks(mc) : -1;
        ViewFrustum viewFrustum = accessor.ausm$viewFrustum();
        if (viewFrustum == null) {
            self().ensureVanillaTerrainRenderer(world, true);
            viewFrustum = accessor.ausm$viewFrustum();
            if (viewFrustum == null) {
                self().deleteCachedVanillaTerrainRenderer(world);
                vanillaViewFrustumStateStack.clear();
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
                self().logTerrainDiagnostic(stagePrefix + ":missing-view-frustum", world, "");
                return;
            }
            self().logTerrainDiagnostic(stagePrefix + ":created-view-frustum", world,
                    "current=" + PipelineWorldRenderScope.viewFrustumId(viewFrustum)
                            + ", renderDistance=" + renderDistanceChunks);
        }

        Map<World, ViewFrustum> rendererViewFrustums = vanillaViewFrustums.computeIfAbsent(
                renderGlobal,
                ignored -> new IdentityHashMap<>()
        );
        ViewFrustum previous = rendererViewFrustums.put(world, viewFrustum);
        if (previous != null && previous != viewFrustum) {
            Set<ViewFrustum> removedViewFrustums = new HashSet<>();
            removedViewFrustums.add(previous);
            self().clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
            vanillaViewFrustumChunkPositionKeys.remove(previous);
            MinecraftReflectionCompat.deleteViewFrustumGlResources(previous);
        }

        vanillaViewFrustumRenderDistances
                .computeIfAbsent(renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, renderDistanceChunks);
        self().rememberStableMainWorldVanillaRenderDistance(world, renderDistanceChunks);
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = renderGlobal;
        activeVanillaViewFrustumWorld = world;
        activeVanillaViewFrustumRenderDistanceChunks = renderDistanceChunks;
        if (mc != null) {
            self().updateVanillaViewFrustumChunkPositions(viewFrustum, MinecraftReflectionCompat.renderViewEntity(mc));
        }
        accessor.ausm$setDisplayListEntitiesDirty(true);
        self().logTerrainDiagnostic(stagePrefix + ":adopt-view-frustum", world,
                "previous=" + PipelineWorldRenderScope.viewFrustumId(previous)
                        + ", current=" + PipelineWorldRenderScope.viewFrustumId(viewFrustum)
                        + ", renderDistance=" + renderDistanceChunks);
    }
}
