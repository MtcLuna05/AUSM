package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.compat.NothiriumBypass;
import com.luna.ausm.impl.pipeline.fbo.PingPongManager;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.pipeline.shader.ShaderProgram;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.FORCE_LIGHT_RECALC_MAX_RADIUS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.FORCE_LIGHT_RECALC_MIN_RADIUS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.MAX_PENDING_CLIENT_CHUNK_RENDER_REFRESHES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.MAX_PENDING_SHADER_CHUNK_REFRESHES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.WORLD_LOAD_LIGHT_REFRESH_RADIUS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.WORLD_LOAD_TERRAIN_REFRESH_ATTEMPTS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES;

abstract class PipelineDeferredPassOrchestration2 extends PipelineDeferredPassOrchestration1 {
    protected int compileDistantHorizonsCompositeShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            MainMod.LOGGER.warn("[DistantHorizons] Failed to compile composite shader stage {}: {}",
                    type,
                    GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            distantHorizonsCompositeProgramFailed = true;
            return 0;
        }
        return shader;
    }

    protected void deleteDistantHorizonsCompositeProgram() {
        if (distantHorizonsCompositeProgramId != 0) {
            GL20.glDeleteProgram(distantHorizonsCompositeProgramId);
        }
        distantHorizonsCompositeProgramId = 0;
        distantHorizonsCompositeTextureUniform = -1;
        distantHorizonsCompositeDepthUniform = -1;
        distantHorizonsCompositeProgramFailed = false;
    }

    protected void deleteDistantHorizonsFramebuffer() {
        if (distantHorizonsFramebufferId != 0) {
            MinecraftReflectionCompat.glDeleteFramebuffers(distantHorizonsFramebufferId);
        }
        if (distantHorizonsTexturesOwned && distantHorizonsColorTextureId != 0) {
            GL11.glDeleteTextures(distantHorizonsColorTextureId);
        }
        if (distantHorizonsTexturesOwned && distantHorizonsDepthTextureId != 0) {
            GL11.glDeleteTextures(distantHorizonsDepthTextureId);
        }
        if (distantHorizonsTextureReadbackFramebufferId != 0) {
            MinecraftReflectionCompat.glDeleteFramebuffers(distantHorizonsTextureReadbackFramebufferId);
        }
        distantHorizonsFramebufferId = 0;
        distantHorizonsColorTextureId = 0;
        distantHorizonsDepthTextureId = 0;
        distantHorizonsTexturesOwned = false;
        distantHorizonsFramebufferWidth = 0;
        distantHorizonsFramebufferHeight = 0;
        distantHorizonsFramebufferClearFrame = Long.MIN_VALUE;
        distantHorizonsTextureReadbackFramebufferId = 0;
        distantHorizonsFramebufferPendingComposite = false;
    }

    public void setDistantHorizonsModelOffset(Object vec) {
        if (vec == null) {
            return;
        }
        try {
            distantHorizonsMatrices.updateModelOffset(vec);
            if (currentDistantHorizonsProgram != null) {
                uniformRegistry.upload(currentDistantHorizonsProgram, "dhModelOffset");
                uniformRegistry.upload(currentDistantHorizonsProgram, "iris_ModelOffset");
            } else if (currentDistantHorizonsFallbackProgram) {
                self().uploadDistantHorizonsFallbackModelOffset();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public void uploadDistantHorizonsUniforms(Object renderParam) {
        if (renderParam == null) {
            return;
        }
        try {
            updateDistantHorizonsRenderPass(renderParam);

            distantHorizonsMatrices.update(renderParam);
            self().bindDistantHorizonsShaderProgram();
            self().uploadDistantHorizonsWorldYOffset(renderParam);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    protected void uploadDistantHorizonsWorldYOffset(Object renderParam) {
        if (!currentDistantHorizonsFallbackProgram || distantHorizonsFallbackProgramId == 0 || distantHorizonsFallbackWorldYOffsetUniform < 0) {
            return;
        }
        try {
            float worldYOffset = ((Number) renderParam.getClass().getField("worldYOffset").get(renderParam)).floatValue();
            GL20.glUniform1f(distantHorizonsFallbackWorldYOffsetUniform, worldYOffset);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            GL20.glUniform1f(distantHorizonsFallbackWorldYOffsetUniform, 0.0F);
        }
    }

    protected ShaderProgram activeProgram() {
        if (activePass == null) {
            return null;
        }
        return self().getProgram(activePass);
    }

    public PingPongManager getPingPongManager() {
        return pingPongManager;
    }

    public int getShadowDepthTexture() {
        return shadowFramebuffer != null ? shadowFramebuffer.depthTextureId() : -1;
    }

    public int getShadowDepthSnapshotTexture() {
        return shadowFramebuffer != null ? shadowFramebuffer.depthSnapshotTextureId() : -1;
    }

    public int getRawShadowDepthTexture() {
        return shadowFramebuffer != null ? shadowFramebuffer.rawDepthTextureId() : -1;
    }

    public int getShadowColor0Texture() {
        return shadowFramebuffer != null ? shadowFramebuffer.colorTextureId() : -1;
    }

    public int getShadowColor1Texture() {
        return shadowFramebuffer != null ? shadowFramebuffer.colorTextureId(1) : -1;
    }

    public int getShadowColorTexture(int index) {
        return shadowFramebuffer != null ? shadowFramebuffer.colorTextureId(index) : -1;
    }

    public boolean shouldUseNeutralShadowTextures() {
        // A clear or sparse shadow map samples as zero with Entree's
        // sampler2DShadow path, which makes the whole scene look shadowed.
        // Keep the real map bound only after the shadow health gate confirms
        // usable terrain coverage; the neutral depth texture represents a
        // fully lit frame while the map is warming up or invalid.
        return isBetterPortalsExternalWorldTarget() || !shadowMapUsable;
    }

    public boolean isShadowMapUsable() {
        return shadowMapUsable;
    }

    public boolean isShadowMapPopulated() {
        return shadowMapPopulated;
    }

    public boolean shouldUseShadowHardwareFiltering() {
        return packDirectives.renderTargets().shadowHardwareFiltering();
    }

    public void configureShadowDepthTextureCompareMode() {
        if (shadowFramebuffer != null) {
            shadowFramebuffer.configureDepthTextureCompareMode();
        }
    }

    public void setActive(boolean active) {
        boolean wasPipelineActive = isPipelineActive;
        isPipelineActive = active && pingPongManager.isInitialized();
        boolean activeStateChanged = wasPipelineActive != isPipelineActive;
        boolean shaderDisable = wasPipelineActive && !isPipelineActive;
        if (isPipelineActive) {
            TextureBinder.markShaderOnlyFixedFunctionTextureUnitsDirty();
            zeroOpaqueTerrainFrames = 0;
            sparseOpaqueTerrainFrames = 0;
            zeroOpaqueTerrainRecoveryRequested = false;
            betterPortalsPipelineLogs = 0;
            BetterPortalsCompat.resetRenderStateDiagnostics();
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            if (mc != null && MinecraftReflectionCompat.world(mc) != null) {
                resizeFramebuffer(MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc), true);
            }
            clearShaderedNothiriumGlobalBypassState(true);
        } else {
            clearShaderedTerrainFallbackState();
            self().clearPendingShaderChunkRefreshes();
            self().clearShaderlessBloomMetadata();
            self().clearScheduledWorldTerrainRefresh();
            self().clearScheduledBloomTerrainRefresh();
            shaderlessBloomVertexFormatRefreshRequested = false;
            scheduleInactiveVanillaRecoveryFrame();
            self().resetPipelineState();
        }
        boolean nothiriumFormatChanged = self().updateNothiriumPipelineBlockFormatMode();
        if (!shaderDisable && (activeStateChanged || nothiriumFormatChanged)) {
            self().rebuildTerrainRenderers(activeStateChanged || nothiriumFormatChanged, true);
        } else if (shaderDisable) {
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            logTerrainDiagnostic("shader-disable:skip-global-rebuild",
                    mc != null ? MinecraftReflectionCompat.world(mc) : null,
                    "formatChanged=" + nothiriumFormatChanged + ", activeStateChanged=" + activeStateChanged);
        }
    }

    public void recoverShaderlessBloomAfterShaderDisable(String reason) {
        self().clearShaderlessBloomMetadata();
        shaderlessBloomVertexFormatRefreshRequested = false;
        self().clearPendingShaderChunkRefreshes();
        self().clearPendingClientChunkRenderRefreshes();
        self().clearScheduledWorldTerrainRefresh();
        self().clearScheduledBloomTerrainRefresh();
        scheduleInactiveVanillaRecoveryFrame();
        self().scheduleWorldLoadLightRecalculation();
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        logTerrainDiagnostic("shader-disable:defer-shaderless-bloom-recovery",
                mc != null ? MinecraftReflectionCompat.world(mc) : null,
                "reason=" + reason + ", bloomResources=" + bloomRenderer.hasBloomResources());
    }

    public void rebuildTerrainRenderers() {
        self().rebuildTerrainRenderers(self().updateNothiriumPipelineBlockFormatMode());
    }

    public void handleResourcePackReload() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return;
        }

        textureReloadCount++;
        self().resetPipelineState(MinecraftReflectionCompat.minecraftFramebuffer(mc));
        self().clearPendingShaderChunkRefreshes();
        self().clearPendingClientChunkRenderRefreshes();
        self().clearScheduledWorldTerrainRefresh();
        self().clearScheduledBloomTerrainRefresh();
        self().scheduleWorldTerrainRefresh();
        self().scheduleBloomTerrainRefresh("resource-pack-reload");
        if (MinecraftReflectionCompat.world(mc) != null) {
            self().scheduleWorldLoadLightRecalculation();
            self().rebuildTerrainRenderers(self().updateNothiriumPipelineBlockFormatMode());
        }
        MainMod.LOGGER.info("[Pipeline] Recovered render state after resource pack reload.");
    }

    protected void rebuildTerrainRenderers(boolean recreateNothiriumRenderer) {
        self().rebuildTerrainRenderers(recreateNothiriumRenderer, true);
    }

    protected void rebuildTerrainRenderers(boolean recreateNothiriumRenderer, boolean reloadVanillaRenderGlobal) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }
        logTerrainDiagnostic("rebuild-terrain-renderers", MinecraftReflectionCompat.world(mc), "recreateNothirium=" + recreateNothiriumRenderer
                + ", reloadVanilla=" + reloadVanillaRenderGlobal);
        if (recreateNothiriumRenderer) {
            NothiriumBypass.recreateRenderer();
        } else {
            NothiriumBypass.markAllChanged();
        }
        if (reloadVanillaRenderGlobal) {
            MinecraftReflectionCompat.loadRenderers(MinecraftReflectionCompat.renderGlobal(mc));
        }
        if (isPipelineActive && MinecraftReflectionCompat.world(mc) != null) {
            rebuildMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), "rebuild-terrain-renderers");
            resetCameraFrustumSyncState();
        } else if (isPipelineActive) {
            ensureVanillaTerrainRenderer();
        }
    }

    protected boolean updateNothiriumPipelineBlockFormatMode() {
        boolean active = shouldUsePipelineBlockFormat();
        if (nothiriumPipelineBlockFormatActive == active) {
            return false;
        }
        nothiriumPipelineBlockFormatActive = active;
        MainMod.LOGGER.info("[AUSMNothiriumFormat] pipelineBlockFormat={} pipelineActive={} nativeBloom={} bloomResources={} terrainFormatSupported={}",
                active,
                isPipelineActive,
                AusmBloomLayer.shouldUseNativeHook(),
                bloomRenderer.hasBloomResources(),
                pipelineTerrainFormatSupported());
        return true;
    }

    public int[] forceLightRecalculation() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.player(mc) == null) {
            return new int[]{0, 0, 0, 0};
        }

        World world = MinecraftReflectionCompat.world(mc);
        BlockPos center = new BlockPos(MinecraftReflectionCompat.player(mc));
        int horizontalRadius = Math.min(
                FORCE_LIGHT_RECALC_MAX_RADIUS,
                Math.max(FORCE_LIGHT_RECALC_MIN_RADIUS, WORLD_LOAD_LIGHT_REFRESH_RADIUS)
        );
        int verticalRadius = Math.min(8, horizontalRadius);
        int minX = MinecraftReflectionCompat.blockPosX(center) - horizontalRadius;
        int maxX = MinecraftReflectionCompat.blockPosX(center) + horizontalRadius;
        int minY = Math.max(0, MinecraftReflectionCompat.blockPosY(center) - verticalRadius);
        int maxY = Math.min(255, MinecraftReflectionCompat.blockPosY(center) + verticalRadius);
        int minZ = MinecraftReflectionCompat.blockPosZ(center) - horizontalRadius;
        int maxZ = MinecraftReflectionCompat.blockPosZ(center) + horizontalRadius;

        syntheticLightCandidates.clear();
        if (shaderImages.active()) {
            clearColoredLightImages();
        }

        int chunkCount = self().forceChunkLightingRefresh(world, minX, maxX, minZ, maxZ);
        int blockChecks = self().forceBlockLightingRefresh(world, minX, minY, minZ, maxX, maxY, maxZ);

        MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(world, minX, minY, minZ, maxX, maxY, maxZ);
        self().refreshVanillaLightmap(mc);
        self().rebuildTerrainRenderers();

        MainMod.LOGGER.info(
                "[Lighting] Forced nearby light recalculation radius={} verticalRadius={} chunks={} blockChecks={} shadersActive={}",
                horizontalRadius,
                verticalRadius,
                chunkCount,
                blockChecks,
                isPipelineActive
        );
        return new int[]{horizontalRadius, chunkCount, blockChecks, isPipelineActive ? 1 : 0};
    }

    public void scheduleWorldLoadLightRecalculation() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        int dimension = mc != null && MinecraftReflectionCompat.world(mc) != null ? self().safeDimensionId(MinecraftReflectionCompat.world(mc)) : Integer.MIN_VALUE;
        if (pendingWorldLoadLightRecalculationAttempts > 0
                && pendingWorldLoadLightRecalculationDimension == dimension) {
            pendingWorldLoadLightRecalculationAttempts = Math.max(
                    pendingWorldLoadLightRecalculationAttempts,
                    WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS
            );
            pendingWorldLoadLightRecalculationDelay = Math.min(
                    pendingWorldLoadLightRecalculationDelay,
                    WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES
            );
            return;
        }

        pendingWorldLoadLightRecalculationAttempts = WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS;
        pendingWorldLoadLightRecalculationDelay = WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES;
        pendingWorldLoadLightRecalculationDimension = dimension;
    }

    public void clearScheduledWorldLoadLightRecalculation() {
        pendingWorldLoadLightRecalculationAttempts = 0;
        pendingWorldLoadLightRecalculationDelay = 0;
        pendingWorldLoadLightRecalculationDimension = Integer.MIN_VALUE;
    }

    public void scheduleWorldTerrainRefresh() {
        self().scheduleWorldTerrainRefresh(false);
    }

    public void scheduleFullWorldTerrainRefresh() {
        self().scheduleWorldTerrainRefresh(true, true);
    }

    public void scheduleSingleFullWorldTerrainRefresh() {
        self().scheduleWorldTerrainRefresh(true, true, WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES, 1);
    }

    protected void scheduleDimensionSwitchTerrainRefresh() {
        self().scheduleWorldTerrainRefresh(true, true, 0);
    }

    protected void scheduleWorldTerrainRefresh(boolean fullRendererReset) {
        self().scheduleWorldTerrainRefresh(fullRendererReset, fullRendererReset);
    }

    protected void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload) {
        self().scheduleWorldTerrainRefresh(fullRendererReset, vanillaReload, WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES);
    }

    protected void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload, int initialDelay) {
        self().scheduleWorldTerrainRefresh(fullRendererReset, vanillaReload, initialDelay, WORLD_LOAD_TERRAIN_REFRESH_ATTEMPTS);
    }

    protected void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload, int initialDelay, int attempts) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        int dimension = mc != null && MinecraftReflectionCompat.world(mc) != null ? self().safeDimensionId(MinecraftReflectionCompat.world(mc)) : Integer.MIN_VALUE;
        int delay = Math.max(0, initialDelay);
        int refreshAttempts = Math.max(1, attempts);
        if (pendingWorldTerrainRefreshAttempts > 0 && pendingWorldTerrainRefreshDimension == dimension) {
            logTerrainDiagnostic("schedule-world-terrain:coalesce",
                    mc != null ? MinecraftReflectionCompat.world(mc) : null,
                    "fullReset=" + fullRendererReset
                            + ", vanillaReload=" + vanillaReload
                            + ", initialDelay=" + delay
                            + ", requestedAttempts=" + refreshAttempts
                            + ", oldAttempts=" + pendingWorldTerrainRefreshAttempts
                            + ", oldDelay=" + pendingWorldTerrainRefreshDelay);
            pendingWorldTerrainRefreshAttempts = Math.max(pendingWorldTerrainRefreshAttempts, refreshAttempts);
            pendingWorldTerrainRefreshDelay = Math.min(pendingWorldTerrainRefreshDelay, delay);
            pendingWorldTerrainRendererReset |= fullRendererReset;
            pendingWorldTerrainFullRendererReset |= fullRendererReset;
            pendingWorldTerrainVanillaReload |= vanillaReload;
            return;
        }

        pendingWorldTerrainRefreshAttempts = refreshAttempts;
        pendingWorldTerrainRefreshDelay = delay;
        pendingWorldTerrainRefreshDimension = dimension;
        pendingWorldTerrainRendererReset = fullRendererReset;
        pendingWorldTerrainFullRendererReset = fullRendererReset;
        pendingWorldTerrainVanillaReload = vanillaReload;
        logTerrainDiagnostic("schedule-world-terrain:new",
                mc != null ? MinecraftReflectionCompat.world(mc) : null,
                "fullReset=" + fullRendererReset + ", vanillaReload=" + vanillaReload + ", initialDelay=" + delay + ", attempts=" + refreshAttempts);
    }

    public void clearScheduledWorldTerrainRefresh() {
        if (pendingWorldTerrainRefreshAttempts > 0) {
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            logTerrainDiagnostic("schedule-world-terrain:clear",
                    mc != null ? MinecraftReflectionCompat.world(mc) : null,
                    "attempts=" + pendingWorldTerrainRefreshAttempts
                            + ", delay=" + pendingWorldTerrainRefreshDelay
                            + ", dim=" + pendingWorldTerrainRefreshDimension
                            + ", rendererReset=" + pendingWorldTerrainRendererReset
                            + ", fullReset=" + pendingWorldTerrainFullRendererReset
                            + ", vanillaReload=" + pendingWorldTerrainVanillaReload);
        }
        pendingWorldTerrainRefreshAttempts = 0;
        pendingWorldTerrainRefreshDelay = 0;
        pendingWorldTerrainRefreshDimension = Integer.MIN_VALUE;
        pendingWorldTerrainRendererReset = false;
        pendingWorldTerrainFullRendererReset = false;
        pendingWorldTerrainVanillaReload = false;
    }

    public void queueShaderChunkRefresh(WorldClient world, int chunkX, int chunkZ) {
        if (world == null || !isPipelineActive) {
            return;
        }

        ShaderChunkRefresh refresh = new ShaderChunkRefresh(world, chunkX, chunkZ);
        synchronized (pendingShaderChunkRefreshes) {
            if (pendingShaderChunkRefreshes.contains(refresh)) {
                return;
            }
            if (pendingShaderChunkRefreshes.size() >= MAX_PENDING_SHADER_CHUNK_REFRESHES) {
                ShaderChunkRefresh oldest = pendingShaderChunkRefreshes.iterator().next();
                pendingShaderChunkRefreshes.remove(oldest);
            }
            pendingShaderChunkRefreshes.add(refresh);
        }
    }

    public void queueClientChunkRenderRefresh(WorldClient world, int chunkX, int chunkZ, String reason) {
        String normalizedReason = reason != null ? reason : "unknown";
        if (world == null || !self().shouldQueueClientChunkRenderRefresh(world, normalizedReason)) {
            return;
        }

        synchronized (pendingClientChunkRenderRefreshes) {
            long chunkKey = PipelineContext.clientChunkRenderRefreshChunkKey(chunkX, chunkZ);
            if ("chunk-data".equals(normalizedReason)
                    || CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE.equals(normalizedReason)
                    || CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM.equals(normalizedReason)) {
                self().forgetRecentlyCompletedClientChunkRenderRefreshLocked(world, chunkKey);
            } else if (self().isRecentlyCompletedClientChunkRenderRefreshLocked(world, chunkKey)) {
                return;
            }
            Map<Long, ClientChunkRenderRefresh> worldLookup = pendingClientChunkRenderRefreshLookupByWorld.get(world);
            ClientChunkRenderRefresh existing = worldLookup != null ? worldLookup.get(chunkKey) : null;
            if (existing != null) {
                self().mergeClientChunkRenderRefresh(existing, normalizedReason);
                return;
            }
            if (pendingClientChunkRenderRefreshes.size() >= MAX_PENDING_CLIENT_CHUNK_RENDER_REFRESHES) {
                ClientChunkRenderRefresh oldest = pendingClientChunkRenderRefreshes.iterator().next();
                self().removePendingClientChunkRenderRefreshLocked(oldest);
            }
            ClientChunkRenderRefresh refresh = new ClientChunkRenderRefresh(
                    world,
                    chunkX,
                    chunkZ,
                    normalizedReason,
                    CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS,
                    self().clientChunkRenderRefreshInitialDelay(normalizedReason)
            );
            self().addPendingClientChunkRenderRefreshLocked(refresh);
        }
    }

    protected void mergeClientChunkRenderRefresh(ClientChunkRenderRefresh existing, String reason) {
        if (existing == null) {
            return;
        }
        existing.attemptsRemaining = Math.max(existing.attemptsRemaining, CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS);
        if ("chunk-data".equals(reason)
                || CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE.equals(reason)
                || CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM.equals(reason)) {
            existing.reason = reason;
            existing.delayFrames = Math.min(existing.delayFrames, self().clientChunkRenderRefreshInitialDelay(reason));
        } else {
            existing.delayFrames = Math.min(existing.delayFrames, CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES);
        }
    }

    protected int clientChunkRenderRefreshInitialDelay(String reason) {
        return PipelineClientChunkRefreshPolicy.initialDelay(reason, CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE,
                CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM, CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES);
    }

    protected static long clientChunkRenderRefreshChunkKey(int chunkX, int chunkZ) {
        return PipelineClientChunkRefreshPolicy.chunkKey(chunkX, chunkZ);
    }

    public void clearPendingShaderChunkRefreshes() {
        synchronized (pendingShaderChunkRefreshes) {
            pendingShaderChunkRefreshes.clear();
        }
    }

    public void clearPendingClientChunkRenderRefreshes() {
        synchronized (pendingClientChunkRenderRefreshes) {
            pendingClientChunkRenderRefreshes.clear();
            pendingClientChunkRenderRefreshLookupByWorld.clear();
            pendingClientChunkRenderRefreshesByWorld.clear();
            recentlyCompletedClientChunkRenderRefreshes.clear();
            recentlyCompletedClientChunkRenderRefreshLastPruneFrame = Long.MIN_VALUE;
        }
    }
}
