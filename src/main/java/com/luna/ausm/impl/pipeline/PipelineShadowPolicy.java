package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.compat.NothiriumBypass;
import com.luna.ausm.impl.pipeline.compat.NothiriumShadowRenderer;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_CAMERA_FRUSTUM_SYNC_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_TELEPORT_TERRAIN_REFRESH_DISTANCE_SQ;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_SAFE_TERRAIN_FALLBACKS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.WORLD_TERRAIN_TRANSITION_DEBOUNCE_MS;

abstract class PipelineShadowPolicy extends PipelineFrameLifecycle {
    protected Attachment[] persistentHistoryAttachments() {
        Set<Attachment> clearDisabled = packDirectives.renderTargets().clearDisabled();
        if (clearDisabled.isEmpty()) {
            return new Attachment[0];
        }

        List<Attachment> attachments = new ArrayList<>();
        for (Attachment attachment : clearDisabled) {
            // COLOR contains the current gbuffer terrain for this frame; clearing it mid-frame
            // causes the white/blank terrain this recovery is meant to avoid.
            if (attachment != Attachment.COLOR) {
                attachments.add(attachment);
            }
        }
        return attachments.toArray(new Attachment[0]);
    }

    protected boolean hasActiveShadowProgram() {
        Boolean shadowEnabled = shaderProperties.renderSettings().shadowEnabled();
        if (shadowEnabled != null) {
            return shadowEnabled;
        }
        for (PipelineProgram program : programs.values()) {
            if (program.stage() == ProgramStage.SHADOW && program.effectiveProgram(programs) != null) {
                return true;
            }
        }
        if (!shadowComputePrograms.isEmpty()
                || !computeProgramArrays.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of()).isEmpty()
                || !fullscreenArrayPrograms.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of()).isEmpty()) {
            return true;
        }
        return false;
    }

    public boolean shouldDisableVanillaEntityShadows() {
        return isPipelineActive && shadowFramebuffer != null && self().hasActiveShadowProgram();
    }

    public boolean shouldRenderShadowMapBeforeTerrainSetup() {
        if (isBetterPortalsExternalWorldTarget() || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return false;
        }
        if ((ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain)
                || shouldSuppressShadowMapForSoftVanillaStartupPack()) {
            return false;
        }
        return !self().shouldUseNothiriumShadowBridge();
    }

    public boolean shouldRenderShadowMapAfterTerrainSetup() {
        if (isBetterPortalsExternalWorldTarget() || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return false;
        }
        if (shouldSuppressShadowMapForSoftVanillaStartupPack()) {
            return false;
        }
        return self().shouldUseNothiriumShadowBridge();
    }

    public boolean shouldRenderShadowMapAfterOpaqueTerrain() {
        return false;
    }

    protected boolean shouldUseNothiriumShadowBridge() {
        return isPipelineActive
                && worldFrameActive
                && PipelineWorldRenderScope.isNothiriumLoaded()
                && NothiriumShadowRenderer.isAvailable()
                && !self().shouldForceVanillaTerrainRenderer()
                && !BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    protected boolean shouldUseNothiriumMainTerrainBridge() {
        return isPipelineActive
                && PipelineWorldRenderScope.isNothiriumLoaded()
                && NothiriumShadowRenderer.isAvailable();
    }

    protected boolean shouldSuppressNothiriumShadowTerrain() {
        return false;
    }

    protected boolean shouldReuseMainTerrainForShadowMap() {
        return false;
    }

    public void ensureVanillaTerrainRenderer() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = BetterPortalsCompat.currentRenderPassWorld();
        self().ensureVanillaTerrainRenderer(
                world != null ? world : (mc != null ? MinecraftReflectionCompat.world(mc) : null),
                hardwareSafeVanillaTerrain || isPipelineActive
        );
    }

    protected void pushVanillaTerrainRendererState() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.renderGlobal(mc) == null || !(MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor renderGlobal)) {
            vanillaViewFrustumStateStack.push(new Object[]{null, null});
            return;
        }

        vanillaViewFrustumStateStack.push(new Object[]{MinecraftReflectionCompat.renderGlobal(mc), renderGlobal.ausm$viewFrustum()});
    }

    protected void popVanillaTerrainRendererState() {
        Object[] state = vanillaViewFrustumStateStack.poll();
        if (state == null || state.length < 2 || !(state[0] instanceof RenderGlobal savedRenderGlobal)
                || !(savedRenderGlobal instanceof RenderGlobalAccessor renderGlobal)) {
            return;
        }
        ViewFrustum savedViewFrustum = state[1] instanceof ViewFrustum viewFrustum ? viewFrustum : null;

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && !BetterPortalsCompat.isRenderingNestedView()
                && mc != null
                && MinecraftReflectionCompat.world(mc) != null) {
            self().ensureVanillaTerrainRenderer(MinecraftReflectionCompat.world(mc), true);
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
            return;
        }

        if (savedViewFrustum == null) {
            if (mc != null && MinecraftReflectionCompat.world(mc) != null && renderGlobal.ausm$viewFrustum() == null) {
                self().ensureVanillaTerrainRenderer(MinecraftReflectionCompat.world(mc), true);
            }
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
            return;
        }

        if (renderGlobal.ausm$viewFrustum() != savedViewFrustum) {
            renderGlobal.ausm$setViewFrustum(savedViewFrustum);
            renderGlobal.ausm$setDisplayListEntitiesDirty(true);
        }
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
    }

    public void ensureVanillaTerrainRenderer(World world) {
        self().ensureVanillaTerrainRenderer(world, false);
    }

    public void ensureRenderGlobalViewFrustum(RenderGlobal renderGlobal) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || accessor.ausm$viewFrustum() != null) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.renderGlobal(mc) != renderGlobal) {
            return;
        }

        self().logTerrainDiagnostic("ensure-render-global-view-frustum", MinecraftReflectionCompat.world(mc), "missing-view-frustum=true");
        self().ensureVanillaTerrainRenderer(MinecraftReflectionCompat.world(mc), true);
    }

    public void updateShaderlessVanillaViewFrustumForCamera() {
        if (!self().shouldSyncShaderlessVanillaViewFrustumForCamera()) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null
                || MinecraftReflectionCompat.world(mc) == null
                || MinecraftReflectionCompat.renderGlobal(mc) == null
                || !(MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor renderGlobal)) {
            return;
        }

        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld != null && renderPassWorld != MinecraftReflectionCompat.world(mc)) {
            return;
        }

        boolean worldChanged = false;
        World renderGlobalWorld = renderGlobal.ausm$world();
        if (renderGlobalWorld != null && renderGlobalWorld != MinecraftReflectionCompat.world(mc)) {
            worldChanged = self().syncRenderGlobalWorld(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc));
        }
        self().ensureVanillaTerrainRenderer(MinecraftReflectionCompat.world(mc), false);

        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        if (viewFrustum == null) {
            self().ensureVanillaTerrainRenderer(MinecraftReflectionCompat.world(mc), true);
            viewFrustum = renderGlobal.ausm$viewFrustum();
        }
        if (viewFrustum == null) {
            return;
        }

        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        self().updateVanillaViewFrustumChunkPositions(viewFrustum, viewEntity);
        self().logCameraFrustumSyncIfChanged(MinecraftReflectionCompat.world(mc), viewFrustum, viewEntity, renderPassWorld != null, worldChanged);
    }

    protected boolean shouldSyncShaderlessVanillaViewFrustumForCamera() {
        return (BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && NothiriumBypass.shouldBypass())
                || (isPipelineActive
                && ENABLE_SAFE_TERRAIN_FALLBACKS
                && (hardwareSafeVanillaTerrain || softVanillaTerrainRenderer));
    }

    protected void logCameraFrustumSyncIfChanged(World world, ViewFrustum viewFrustum, Entity viewEntity,
                                                 boolean renderPass, boolean worldChanged) {
        if (world == null || viewFrustum == null || viewEntity == null) {
            return;
        }

        int chunkX = (int) Math.floor(MinecraftReflectionCompat.posX(viewEntity)) >> 4;
        int chunkZ = (int) Math.floor(MinecraftReflectionCompat.posZ(viewEntity)) >> 4;
        if (lastCameraFrustumSyncWorld == world
                && lastCameraFrustumSyncViewFrustum == viewFrustum
                && lastCameraFrustumSyncChunkX == chunkX
                && lastCameraFrustumSyncChunkZ == chunkZ
                && !worldChanged) {
            return;
        }

        lastCameraFrustumSyncWorld = world;
        lastCameraFrustumSyncViewFrustum = viewFrustum;
        lastCameraFrustumSyncChunkX = chunkX;
        lastCameraFrustumSyncChunkZ = chunkZ;
        if (cameraFrustumSyncLogs >= MAX_CAMERA_FRUSTUM_SYNC_LOGS) {
            return;
        }

        cameraFrustumSyncLogs++;
        MainMod.LOGGER.info(
                "[AUSMFrustumSync] call={} world={} chunk={},{} viewFrustum={} renderPass={} worldChanged={} bp={}",
                cameraFrustumSyncLogs,
                safeDimensionId(world),
                chunkX,
                chunkZ,
                PipelineWorldRenderScope.viewFrustumId(viewFrustum),
                renderPass,
                worldChanged,
                BetterPortalsCompat.describeTransitionState()
        );
    }

    public void handleBetterPortalsMainViewSwap() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            return;
        }

        self().logTerrainDiagnostic("bp-main-view-swap:start", MinecraftReflectionCompat.world(mc), "");
        self().startVanillaParticleRecovery();
        if (!isPipelineActive) {
            BetterPortalsCompat.clearMainViewSwapTransientState();
            BetterPortalsCompat.cancelMainViewSwapRecovery();
            self().clearScheduledWorldTerrainRefresh();
            self().recoverShaderlessMainWorldTerrain(mc, "bp-main-view-swap");
            self().logInactiveBetterPortalsTerrainSkip("main-view-swap", MinecraftReflectionCompat.world(mc));
            return;
        }

        boolean terrainTransition = self().beginTerrainTransition(MinecraftReflectionCompat.world(mc));
        self().logTerrainDiagnostic("bp-main-view-swap:transition", MinecraftReflectionCompat.world(mc), "accepted=" + terrainTransition);
        self().clearClientParticles("bp-main-view-swap");
        BetterPortalsCompat.clearMainViewSwapTransientState();
        BetterPortalsCompat.beginMainViewSwapHandling();
        try {
            BetterPortalsCompat.startMainViewSwapRecovery(MinecraftReflectionCompat.world(mc));
            BetterPortalsCompat.logMainViewSwapRecoveryIfNeeded(MinecraftReflectionCompat.world(mc));
            self().rebuildMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), "bp-main-view-swap");
            self().resetCameraFrustumSyncState();
            self().scheduleDimensionSwitchTerrainRefresh();
            self().scheduleBloomTerrainRefresh("bp-main-view-swap");
            self().scheduleInactiveVanillaRecoveryFrame();
            self().scheduleWorldLoadLightRecalculation();
        } finally {
            BetterPortalsCompat.endMainViewSwapHandling();
        }
        self().logTerrainDiagnostic("bp-main-view-swap:end", MinecraftReflectionCompat.world(mc), "accepted=" + terrainTransition);
    }

    public void handleWorldDimensionSwitch(int previousDimensionId, int dimensionId) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            return;
        }

        self().logTerrainDiagnostic("dimension-switch:start", MinecraftReflectionCompat.world(mc), "previous=" + previousDimensionId + ", current=" + dimensionId);
        self().clearClientParticles("dimension-switch");
        self().startVanillaParticleRecovery();
        BetterPortalsCompat.clearMainViewSwapTransientState();
        if (!isPipelineActive) {
            BetterPortalsCompat.cancelMainViewSwapRecovery();
            self().clearPendingShaderChunkRefreshes();
            self().clearPendingBetterPortalsPortalBlockRefresh();
            self().clearScheduledWorldTerrainRefresh();
            self().clearScheduledBloomTerrainRefresh();
            currentWorldPass = 0;
            currentWorldPartialTicks = 0.0F;
            self().recoverShaderlessMainWorldTerrain(mc, "dimension-switch");
            self().logInactiveBetterPortalsTerrainSkip("dimension-switch", MinecraftReflectionCompat.world(mc));
            return;
        }

        boolean terrainTransition = self().beginTerrainTransition(MinecraftReflectionCompat.world(mc));
        if (!terrainTransition) {
            self().clearPendingShaderChunkRefreshes();
            self().clearPendingBetterPortalsPortalBlockRefresh();
            self().scheduleWorldLoadLightRecalculation();
            self().logTerrainDiagnostic("dimension-switch:debounced", MinecraftReflectionCompat.world(mc), "previous=" + previousDimensionId + ", current=" + dimensionId);
            return;
        }

        self().clearPendingShaderChunkRefreshes();
        self().clearPendingBetterPortalsPortalBlockRefresh();
        boolean betterPortalsRecovery = BetterPortalsCompat.isMainViewSwapRecoveryActive();
        if (betterPortalsRecovery) {
            self().rebuildMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), "dimension-switch-bp-recovery");
            self().resetCameraFrustumSyncState();
            self().scheduleDimensionSwitchTerrainRefresh();
            self().scheduleBloomTerrainRefresh("dimension-switch-bp-recovery");
            self().scheduleInactiveVanillaRecoveryFrame();
            self().scheduleWorldLoadLightRecalculation();
            self().logTerrainDiagnostic("dimension-switch:bp-recovery-deferred", MinecraftReflectionCompat.world(mc),
                    "previous=" + previousDimensionId + ", current=" + dimensionId);
            return;
        }

        self().resetPipelineState(MinecraftReflectionCompat.minecraftFramebuffer(mc));
        currentWorldPass = 0;
        currentWorldPartialTicks = 0.0F;

        self().rebuildMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), "dimension-switch");
        self().resetCameraFrustumSyncState();
        self().scheduleDimensionSwitchTerrainRefresh();
        self().scheduleBloomTerrainRefresh("dimension switch");
        self().scheduleInactiveVanillaRecoveryFrame();
        self().scheduleWorldLoadLightRecalculation();
        self().logTerrainDiagnostic("dimension-switch:scheduled", MinecraftReflectionCompat.world(mc), "previous=" + previousDimensionId + ", current=" + dimensionId
                + ", bpRecoveryWasActive=" + betterPortalsRecovery);
    }

    public void handleClientTeleportResync(int previousDimensionId, int currentDimensionId, double distanceSq, double horizontalDistanceSq) {
        boolean dimensionChanged = previousDimensionId != Integer.MIN_VALUE
                && currentDimensionId != Integer.MIN_VALUE
                && previousDimensionId != currentDimensionId;
        boolean longTeleport = horizontalDistanceSq >= CLIENT_TELEPORT_TERRAIN_REFRESH_DISTANCE_SQ;
        if (!dimensionChanged && !longTeleport) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            return;
        }

        String reason = dimensionChanged ? "client-teleport-dimension" : "client-teleport";
        self().logTerrainDiagnostic(reason + ":start", MinecraftReflectionCompat.world(mc),
                "previous=" + previousDimensionId + ", current=" + currentDimensionId
                        + ", distanceSq=" + distanceSq + ", horizontalDistanceSq=" + horizontalDistanceSq);
        if (dimensionChanged) {
            self().clearClientParticles(reason);
        }
        self().startVanillaParticleRecovery();

        if (!dimensionChanged) {
            self().clearPendingShaderChunkRefreshes();
            self().clearPendingBetterPortalsPortalBlockRefresh();
            currentWorldPass = 0;
            currentWorldPartialTicks = 0.0F;
            self().resetCameraFrustumSyncState();
            self().scheduleWorldTerrainRefresh();
            self().scheduleWorldLoadLightRecalculation();
            if (!isPipelineActive) {
                self().recoverShaderlessMainWorldTerrain(mc, reason);
            } else {
                self().scheduleInactiveVanillaRecoveryFrame();
            }
            self().logTerrainDiagnostic(reason + ":scheduled", MinecraftReflectionCompat.world(mc), "preservedClientChunkQueue=true");
            return;
        }

        BetterPortalsCompat.clearMainViewSwapTransientState();
        BetterPortalsCompat.cancelMainViewSwapRecovery();
        self().clearPendingShaderChunkRefreshes();
        self().clearPendingBetterPortalsPortalBlockRefresh();
        self().clearScheduledWorldTerrainRefresh();
        self().clearScheduledBloomTerrainRefresh();
        currentWorldPass = 0;
        currentWorldPartialTicks = 0.0F;

        if (!isPipelineActive) {
            self().recoverShaderlessMainWorldTerrain(mc, reason);
            self().scheduleWorldLoadLightRecalculation();
            self().logTerrainDiagnostic(reason + ":shaderless", MinecraftReflectionCompat.world(mc), "");
            return;
        }

        self().resetPipelineState(MinecraftReflectionCompat.minecraftFramebuffer(mc));
        self().rebuildMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), reason);
        self().resetCameraFrustumSyncState();
        self().scheduleFullWorldTerrainRefresh();
        self().scheduleBloomTerrainRefresh(reason);
        self().scheduleInactiveVanillaRecoveryFrame();
        self().scheduleWorldLoadLightRecalculation();
        self().logTerrainDiagnostic(reason + ":scheduled", MinecraftReflectionCompat.world(mc), "");
    }

    protected void recoverShaderlessMainWorldTerrain(Minecraft mc, String reason) {
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        if (self().shouldLeaveShaderlessVanillaTerrainUntouched()) {
            self().recoverShaderlessVanillaOwnerTerrain(mc, reason);
            return;
        }

        boolean hardReset = self().shouldHardResetShaderlessNothirium(reason);
        if (hardReset) {
            self().clearCachedVanillaTerrainRendererReferences();
        }

        boolean ready = NothiriumBypass.ensureRendererReady();
        boolean marked = hardReset ? NothiriumBypass.recreateRenderer() : NothiriumBypass.markAllChanged();
        boolean setup = hardReset && (marked || ready) && NothiriumBypass.setupForIsolatedShaderlessMainPass();

        if (MinecraftReflectionCompat.renderGlobal(mc) != null) {
            self().adoptMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), reason);
        }

        if (marked || ready || setup) {
            self().scheduleInactiveVanillaRecoveryFrame();
        }
        self().scheduleWorldLoadLightRecalculation();
        self().logShaderlessNothiriumLoadRendererReload(MinecraftReflectionCompat.world(mc), marked, reason);
        self().logTerrainDiagnostic(reason + ":shaderless-recover", MinecraftReflectionCompat.world(mc),
                "ready=" + ready + ", marked=" + marked + ", setup=" + setup + ", hardReset=" + hardReset);
    }

    protected void recoverShaderlessVanillaOwnerTerrain(Minecraft mc, String reason) {
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            return;
        }

        boolean transitionReset = self().shouldHardResetShaderlessNothirium(reason);
        if (transitionReset && MinecraftReflectionCompat.renderGlobal(mc) != null) {
            self().rebuildMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), reason + "-vanilla-owner");
            self().resetCameraFrustumSyncState();
            self().scheduleInactiveVanillaRecoveryFrame();
            self().logTerrainDiagnostic(reason + ":shaderless-vanilla-owner-rebuild", MinecraftReflectionCompat.world(mc), "");
        } else if (MinecraftReflectionCompat.renderGlobal(mc) != null) {
            self().adoptMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), reason + "-vanilla-owner");
            self().logTerrainDiagnostic(reason + ":shaderless-vanilla-owner-adopt", MinecraftReflectionCompat.world(mc), "");
        } else {
            self().logTerrainDiagnostic(reason + ":shaderless-vanilla-owner-missing-render-global", MinecraftReflectionCompat.world(mc), "");
        }

        self().scheduleWorldLoadLightRecalculation();
    }

    protected void resetCameraFrustumSyncState() {
        lastCameraFrustumSyncWorld = null;
        lastCameraFrustumSyncViewFrustum = null;
        lastCameraFrustumSyncChunkX = Integer.MIN_VALUE;
        lastCameraFrustumSyncChunkZ = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainRefreshWorld = null;
        lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
    }

    protected boolean beginTerrainTransition(World world) {
        int dimension = safeDimensionId(world);
        long now = System.currentTimeMillis();
        long elapsed = now - lastTerrainTransitionMillis;
        if (world != null
                && lastTerrainTransitionDimension == dimension
                && elapsed >= 0L
                && elapsed < WORLD_TERRAIN_TRANSITION_DEBOUNCE_MS) {
            self().logTerrainDiagnostic("terrain-transition:debounced", world, "elapsedMs=" + elapsed + ", lastDim=" + lastTerrainTransitionDimension);
            return false;
        }

        lastTerrainTransitionWorld = world;
        lastTerrainTransitionDimension = dimension;
        lastTerrainTransitionMillis = now;
        self().logTerrainDiagnostic("terrain-transition:accepted", world, "elapsedMs=" + elapsed + ", lastDim=" + lastTerrainTransitionDimension);
        return true;
    }

    public void queueBetterPortalsPortalBlockChanged(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        if (!BetterPortalsCompat.isInstalled() || world == null || pos == null) {
            return;
        }
        if (self().sameBlockState(oldState, newState)) {
            return;
        }
        if (self().shouldDebounceBetterPortalsPortalBlockRefresh(world, pos)) {
            self().logTerrainDiagnostic("bp-portal-block:debounced", world, "pos=" + pos
                    + ", old=" + PipelineWorldRenderScope.blockName(oldState)
                    + ", new=" + PipelineWorldRenderScope.blockName(newState));
            return;
        }

        pendingBetterPortalsPortalBlockWorld = world;
        pendingBetterPortalsPortalBlockPos = MinecraftReflectionCompat.blockPosToImmutable(pos);
        pendingBetterPortalsPortalBlockOldState = oldState;
        pendingBetterPortalsPortalBlockNewState = newState;
        pendingBetterPortalsPortalBlockChangeCount++;
        if (pendingBetterPortalsPortalBlockRefreshDelay < 0) {
            pendingBetterPortalsPortalBlockRefreshDelay = 3;
        }
        self().logTerrainDiagnostic("bp-portal-block:queued", world, "pos=" + pos
                + ", count=" + pendingBetterPortalsPortalBlockChangeCount
                + ", old=" + PipelineWorldRenderScope.blockName(oldState)
                + ", new=" + PipelineWorldRenderScope.blockName(newState));
    }

    protected void clearPendingBetterPortalsPortalBlockRefresh() {
        pendingBetterPortalsPortalBlockWorld = null;
        pendingBetterPortalsPortalBlockPos = null;
        pendingBetterPortalsPortalBlockOldState = null;
        pendingBetterPortalsPortalBlockNewState = null;
        pendingBetterPortalsPortalBlockChangeCount = 0;
        pendingBetterPortalsPortalBlockRefreshDelay = -1;
        lastBetterPortalsPortalBlockRefreshWorld = null;
        lastBetterPortalsPortalBlockRefreshPos = null;
        lastBetterPortalsPortalBlockRefreshDimension = Integer.MIN_VALUE;
        lastBetterPortalsPortalBlockRefreshMillis = 0L;
    }
}
