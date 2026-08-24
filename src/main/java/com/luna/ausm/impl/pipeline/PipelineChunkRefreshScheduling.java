package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.compat.NothiriumBypass;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

import static com.luna.ausm.impl.pipeline.PipelineGlState.disablePipelineVertexAttributes;
import static com.luna.ausm.impl.pipeline.PipelineGlState.markShaderStorageBuffersUnbound;
import static com.luna.ausm.impl.pipeline.PipelineGlState.maxDrawBuffers;
import static com.luna.ausm.impl.pipeline.PipelineGlState.maxShaderStorageBufferBindings;
import static com.luna.ausm.impl.pipeline.PipelineGlState.setIndexedBlend;
import static com.luna.ausm.impl.pipeline.PipelineGlState.shaderStorageBuffersKnownUnbound;
import static com.luna.ausm.impl.pipeline.PipelineGlState.unbindShaderImages;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_CLIENT_CHUNK_RENDER_REFRESH_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.WORLD_LOAD_LIGHT_REFRESH_RADIUS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.WORLD_LOAD_TERRAIN_REFRESH_REPEAT_DELAY_FRAMES;

abstract class PipelineDeferredPassOrchestration4 extends PipelineDeferredPassOrchestration3 {
    protected int countNonEmptyClientChunkSections(Chunk chunk) {
        if (chunk == null) {
            return 0;
        }
        ExtendedBlockStorage[] sections = MinecraftReflectionCompat.chunkBlockStorageArray(chunk);
        if (sections == null) {
            return 0;
        }
        int count = 0;
        for (ExtendedBlockStorage section : sections) {
            if (!MinecraftReflectionCompat.blockStorageEmpty(section)) {
                count++;
            }
        }
        return count;
    }

    protected boolean shouldScheduleLoadedClientRenderChunk(RenderChunk renderChunk, Chunk chunk, BlockPos position) {
        if (renderChunk == null || chunk == null || position == null) {
            return false;
        }

        int sectionY = MinecraftReflectionCompat.blockPosY(position) >> 4;
        ExtendedBlockStorage[] sections = MinecraftReflectionCompat.chunkBlockStorageArray(chunk);
        if (sectionY < 0 || sections == null || sectionY >= sections.length) {
            return false;
        }

        ExtendedBlockStorage section = sections[sectionY];
        return !MinecraftReflectionCompat.blockStorageEmpty(section);
    }

    protected static int maxClientChunkRefreshSections(int sectionBudget) {
        return Math.max(1, sectionBudget);
    }

    protected static int clampSectionCursor(int sectionY, int sectionCount) {
        if (sectionY < 0 || sectionY >= sectionCount) {
            return 0;
        }
        return sectionY;
    }

    protected static boolean hasNonEmptyClientChunkSection(ExtendedBlockStorage[] sections, int sectionY) {
        return sections != null
                && sectionY >= 0
                && sectionY < sections.length
                && !MinecraftReflectionCompat.blockStorageEmpty(sections[sectionY]);
    }

    protected RenderChunk findRenderChunkForSection(RenderChunk[] renderChunks, int chunkX, int chunkZ, int sectionY) {
        if (renderChunks == null) {
            return null;
        }
        for (RenderChunk renderChunk : renderChunks) {
            BlockPos position = renderChunk != null ? MinecraftReflectionCompat.renderChunkPosition(renderChunk) : null;
            if (position != null
                    && (MinecraftReflectionCompat.blockPosX(position) >> 4) == chunkX
                    && (MinecraftReflectionCompat.blockPosZ(position) >> 4) == chunkZ
                    && (MinecraftReflectionCompat.blockPosY(position) >> 4) == sectionY) {
                return renderChunk;
            }
        }
        return null;
    }

    protected void logClientChunkRenderRefresh(ClientChunkRenderRefresh refresh, boolean loaded, int scheduledChunks) {
        if (clientChunkRenderRefreshLogs >= MAX_CLIENT_CHUNK_RENDER_REFRESH_LOGS) {
            return;
        }
        clientChunkRenderRefreshLogs++;
        MainMod.LOGGER.info(
                "[AUSMClientChunkRefresh] call={} reason={} world={} chunk={},{} loaded={} scheduledChunks={} attemptsLeft={} active={} bypass={} bp={}",
                clientChunkRenderRefreshLogs,
                refresh.reason,
                self().safeDimensionId(refresh.world),
                refresh.chunkX,
                refresh.chunkZ,
                loaded,
                scheduledChunks,
                refresh.attemptsRemaining,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.describeTransitionState()
        );
    }

    protected void refreshShaderChunk(Minecraft mc, ShaderChunkRefresh refresh) {
        if (refresh == null || refresh.world == null || MinecraftReflectionCompat.worldProvider(refresh.world) == null) {
            return;
        }

        if (MinecraftReflectionCompat.world(mc) != refresh.world) {
            return;
        }

        if (!NothiriumBypass.shouldBypass()) {
            nothiriumShadowRenderer.refreshChunkColumn(refresh.chunkX, refresh.chunkZ);
        }
    }

    public void scheduleBloomTerrainRefresh(String reason) {
        if (!AusmBloomLayer.isAvailable()) {
            return;
        }
        pendingBloomTerrainRefreshAttempts = Math.max(pendingBloomTerrainRefreshAttempts, 3);
        if (pendingBloomTerrainRefreshDelay <= 0) {
            pendingBloomTerrainRefreshDelay = 1;
        }
        pendingBloomTerrainRefreshReason = reason != null && !reason.isEmpty() ? reason : "unspecified";
    }

    public void clearScheduledBloomTerrainRefresh() {
        pendingBloomTerrainRefreshAttempts = 0;
        pendingBloomTerrainRefreshDelay = 0;
        pendingBloomTerrainRefreshReason = "";
        bloomZeroGeometryFrames = 0;
        bloomZeroGeometryRefreshCooldown = 0;
    }

    public void runScheduledBloomTerrainRefresh() {
        if (bloomZeroGeometryRefreshCooldown > 0) {
            bloomZeroGeometryRefreshCooldown--;
        }
        if (pendingBloomTerrainRefreshAttempts <= 0) {
            return;
        }
        if (pendingBloomTerrainRefreshDelay > 0) {
            pendingBloomTerrainRefreshDelay--;
            return;
        }

        pendingBloomTerrainRefreshAttempts--;
        pendingBloomTerrainRefreshDelay = 20;
        if (self().refreshBloomTerrainState(pendingBloomTerrainRefreshReason)
                && pendingBloomTerrainRefreshAttempts <= 0) {
            pendingBloomTerrainRefreshReason = "";
        }
    }

    public void runScheduledWorldLoadLightRecalculation() {
        if (pendingWorldLoadLightRecalculationAttempts <= 0) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        int dimension = mc != null && MinecraftReflectionCompat.world(mc) != null ? self().safeDimensionId(MinecraftReflectionCompat.world(mc)) : Integer.MIN_VALUE;
        if (pendingWorldLoadLightRecalculationDimension != Integer.MIN_VALUE
                && pendingWorldLoadLightRecalculationDimension != dimension) {
            self().clearScheduledWorldLoadLightRecalculation();
            return;
        }
        if (pendingWorldLoadLightRecalculationDelay > 0) {
            pendingWorldLoadLightRecalculationDelay--;
            return;
        }

        pendingWorldLoadLightRecalculationAttempts--;
        pendingWorldLoadLightRecalculationDelay = WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES;
        if (self().refreshWorldLoadLightState()) {
            pendingWorldLoadLightRecalculationAttempts = 0;
            pendingWorldLoadLightRecalculationDelay = 0;
            pendingWorldLoadLightRecalculationDimension = Integer.MIN_VALUE;
            MainMod.LOGGER.info("[Lighting] Refreshed scheduled world-load light state.");
        }
    }

    public void runRenderDistanceChangeCheck() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.gameSettings(mc) == null) {
            lastObservedRenderDistanceChunks = -1;
            return;
        }

        int renderDistanceChunks = MinecraftReflectionCompat.renderDistanceChunks(mc);
        if (renderDistanceChunks <= 0) {
            return;
        }
        if (lastObservedRenderDistanceChunks < 0) {
            lastObservedRenderDistanceChunks = renderDistanceChunks;
            return;
        }
        if (lastObservedRenderDistanceChunks == renderDistanceChunks) {
            return;
        }

        int previousRenderDistanceChunks = lastObservedRenderDistanceChunks;
        lastObservedRenderDistanceChunks = renderDistanceChunks;
        MainMod.LOGGER.info("[Pipeline] Render distance changed: old={} new={}; forcing terrain renderer reload.",
                previousRenderDistanceChunks,
                renderDistanceChunks);
        self().forceRenderDistanceTerrainReload(mc, previousRenderDistanceChunks, renderDistanceChunks);
        self().scheduleWorldLoadLightRecalculation();
    }

    protected void forceRenderDistanceTerrainReload(Minecraft mc, int previousRenderDistanceChunks, int renderDistanceChunks) {
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        self().clearScheduledWorldTerrainRefresh();
        self().clearPendingShaderChunkRefreshes();
        self().clearPendingClientChunkRenderRefreshes();
        deleteCachedVanillaTerrainRenderers();
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
        resetCameraFrustumSyncState();
        boolean nothiriumRecreated = NothiriumBypass.recreateRenderer();
        MinecraftReflectionCompat.loadRenderers(MinecraftReflectionCompat.renderGlobal(mc));
        rebuildMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), "render-distance-change");
        NothiriumBypass.markAllChanged();
        scheduleInactiveVanillaRecoveryFrame();
        MainMod.LOGGER.info("[Pipeline] Forced terrain renderer reload for render distance change: world={} old={} new={} nothiriumRecreated={}",
                self().safeDimensionId(MinecraftReflectionCompat.world(mc)),
                previousRenderDistanceChunks,
                renderDistanceChunks,
                nothiriumRecreated);
    }

    public void runScheduledWorldTerrainRefresh() {
        if (pendingWorldTerrainRefreshAttempts <= 0) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (BetterPortalsCompat.isMainViewSwapRecoveryActive() && mc != null) {
            BetterPortalsCompat.keepMainViewSwapRecoveryAlive(MinecraftReflectionCompat.world(mc));
        }
        if (pendingWorldTerrainRefreshDelay > 0) {
            logTerrainDiagnostic("run-world-terrain:delay",
                    mc != null ? MinecraftReflectionCompat.world(mc) : null,
                    "attempts=" + pendingWorldTerrainRefreshAttempts + ", delay=" + pendingWorldTerrainRefreshDelay);
            pendingWorldTerrainRefreshDelay--;
            return;
        }

        logTerrainDiagnostic("run-world-terrain:start",
                mc != null ? MinecraftReflectionCompat.world(mc) : null,
                "attempts=" + pendingWorldTerrainRefreshAttempts);
        if (self().refreshWorldTerrainState()) {
            pendingWorldTerrainRefreshAttempts--;
        }

        if (pendingWorldTerrainRefreshAttempts <= 0) {
            logTerrainDiagnostic("run-world-terrain:done",
                    mc != null ? MinecraftReflectionCompat.world(mc) : null,
                    "");
            self().clearScheduledWorldTerrainRefresh();
        } else {
            pendingWorldTerrainRefreshDelay = WORLD_LOAD_TERRAIN_REFRESH_REPEAT_DELAY_FRAMES;
            logTerrainDiagnostic("run-world-terrain:reschedule",
                    mc != null ? MinecraftReflectionCompat.world(mc) : null,
                    "attempts=" + pendingWorldTerrainRefreshAttempts + ", delay=" + pendingWorldTerrainRefreshDelay);
        }
    }

    protected boolean refreshBloomTerrainState(String reason) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.player(mc) == null) {
            return false;
        }
        if (!AusmBloomLayer.isAvailable() || !bloomRenderer.hasBloomResources()) {
            return false;
        }

        BlockPos center = new BlockPos(MinecraftReflectionCompat.player(mc));
        int radius = Math.clamp((MinecraftReflectionCompat.renderDistanceChunks(mc) * 16) + 16, 64, 512);
        runningBloomTerrainRefresh = true;
        try {
            MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(MinecraftReflectionCompat.world(mc),
                    MinecraftReflectionCompat.blockPosX(center) - radius,
                    0,
                    MinecraftReflectionCompat.blockPosZ(center) - radius,
                    MinecraftReflectionCompat.blockPosX(center) + radius,
                    255,
                    MinecraftReflectionCompat.blockPosZ(center) + radius
            );
        } finally {
            runningBloomTerrainRefresh = false;
        }
        boolean nothiriumDirty = NothiriumBypass.markAllChanged();

        return true;
    }

    protected boolean refreshWorldTerrainState() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.player(mc) == null) {
            return false;
        }

        int dimension = self().safeDimensionId(MinecraftReflectionCompat.world(mc));
        if (pendingWorldTerrainRefreshDimension != Integer.MIN_VALUE
                && pendingWorldTerrainRefreshDimension != dimension) {
            logTerrainDiagnostic("refresh-world-terrain:dimension-mismatch", MinecraftReflectionCompat.world(mc),
                    "pendingDim=" + pendingWorldTerrainRefreshDimension + ", currentDim=" + dimension);
            self().clearScheduledWorldTerrainRefresh();
            return false;
        }

        boolean rendererReset = pendingWorldTerrainRendererReset;
        boolean vanillaReload = pendingWorldTerrainVanillaReload;
        pendingWorldTerrainRendererReset = false;
        pendingWorldTerrainVanillaReload = false;

        if (pendingWorldTerrainFullRendererReset) {
            pendingWorldTerrainFullRendererReset = false;
            logTerrainDiagnostic("refresh-world-terrain:full-reset", MinecraftReflectionCompat.world(mc),
                    "rendererReset=" + rendererReset + ", vanillaReload=" + vanillaReload);
            if (rendererReset) {
                deleteCachedVanillaTerrainRenderers();
                vanillaViewFrustumStateStack.clear();
            }
            self().rebuildTerrainRenderers(self().updateNothiriumPipelineBlockFormatMode(), vanillaReload);
            scheduleInactiveVanillaRecoveryFrame();
            return true;
        }

        ensureVanillaTerrainRenderer(MinecraftReflectionCompat.world(mc), true);
        BlockPos center = new BlockPos(MinecraftReflectionCompat.player(mc));
        int radius = Math.clamp(MinecraftReflectionCompat.renderDistanceChunks(mc) * 16, 32, 128);
        logTerrainDiagnostic("refresh-world-terrain:range", MinecraftReflectionCompat.world(mc),
                "center=" + center + ", radius=" + radius + ", rendererReset=" + rendererReset + ", vanillaReload=" + vanillaReload);
        MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(MinecraftReflectionCompat.world(mc),
                MinecraftReflectionCompat.blockPosX(center) - radius,
                0,
                MinecraftReflectionCompat.blockPosZ(center) - radius,
                MinecraftReflectionCompat.blockPosX(center) + radius,
                255,
                MinecraftReflectionCompat.blockPosZ(center) + radius
        );
        if (isPipelineActive || NothiriumBypass.shouldBypass()) {
            NothiriumBypass.markAllChanged();
            scheduleInactiveVanillaRecoveryFrame();
        }
        return true;
    }

    protected boolean refreshWorldLoadLightState() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.player(mc) == null) {
            return false;
        }

        self().refreshVanillaLightmap(mc);
        if (!isPipelineActive) {
            return true;
        }
        BlockPos center = new BlockPos(MinecraftReflectionCompat.player(mc));
        int radius = WORLD_LOAD_LIGHT_REFRESH_RADIUS;
        MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(MinecraftReflectionCompat.world(mc),
                MinecraftReflectionCompat.blockPosX(center) - radius,
                Math.max(0, MinecraftReflectionCompat.blockPosY(center) - radius),
                MinecraftReflectionCompat.blockPosZ(center) - radius,
                MinecraftReflectionCompat.blockPosX(center) + radius,
                Math.min(255, MinecraftReflectionCompat.blockPosY(center) + radius),
                MinecraftReflectionCompat.blockPosZ(center) + radius
        );
        return true;
    }

    protected int forceChunkLightingRefresh(World world, int minX, int maxX, int minZ, int maxZ) {
        return PipelineLightingRefresh.refreshChunks(world, minX, maxX, minZ, maxZ);
    }

    protected int forceBlockLightingRefresh(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return PipelineLightingRefresh.refreshBlocks(world, minX, minY, minZ, maxX, maxY, maxZ,
                self()::refreshSyntheticLightCandidate);
    }

    protected void resetPipelineState() {
        self().resetPipelineState(null);
    }

    protected void resetPipelineState(Framebuffer preferredTarget) {
        activePass = null;
        activeShaderKey = null;
        activePhase = WorldRenderingPhase.NONE;
        overridePhase = null;
        worldFrameActive = false;
        shaderlessWorldPassActive = false;
        deferredPassesRenderedThisFrame = false;
        preparePassesRenderedBeforeShadowThisFrame = false;
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        renderingShadowMap = false;
        renderingGui = false;
        guiItemRenderDepth = 0;
        guiTargetContentFrame = Long.MIN_VALUE;
        currentWorldPassSerial = Long.MIN_VALUE;
        worldPassSerialStack.clear();
        nothiriumPipelineTranslucentFrameStack.clear();
        nothiriumPipelineTranslucentWorldPassSerialStack.clear();
        clearNothiriumPipelineTranslucentBridge();
        nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
        guiRenderDepth = 0;
        bloomLayerRenderedThisWorldPass = false;
        shaderlessStyleBloomRenderedThisWorldPass = false;
        pendingDeferredNativeBloom = false;
        bloomRenderer.clearPendingLayerBloom();
        passStack.clear();
        worldPassBypassStack.clear();
        untouchedBetterPortalsVanillaRendererStack.clear();
        currentEntityId = 0;
        currentEntityKey = null;
        currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        restoreTerrainCulling();
        MinecraftReflectionCompat.glUseProgram(0);
        self().resetShaderResourceBindings();
        // VAO bindings and fixed-function client arrays are not global state:
        // they survive glUseProgram(0) and are stored on the currently bound
        // VAO. Leaving a pipeline/DH VAO selected makes the first vanilla VBO
        // after shader disable (notably Botania's pixel-star VBO) reinterpret
        // whichever attribute pointers that VAO last contained.
        FixedFunctionGlState.resetClientArrayState(false);
        FixedFunctionGlState.resetVanillaTextureMatrices();
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        resetPortalMaskState();
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateDisableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        for (int i = 0; i < maxDrawBuffers(); i++) {
            setIndexedBlend(i, false);
        }
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        Framebuffer target = preferredTarget != null ? preferredTarget : mc != null ? MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
        if (target != null) {
            MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            MinecraftReflectionCompat.glStateViewport(0, 0, framebufferWidth(target, mc), framebufferHeight(target, mc));
        } else {
            MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), 0);
            GL11.glDrawBuffer(GL11.GL_BACK);
            GL11.glReadBuffer(GL11.GL_BACK);
        }
        externalWorldFramebufferTarget = null;
        self().restoreVanillaTextureBindingsAfterPipeline();
        self().refreshVanillaLightmap(mc);
        self().disableVanillaLightmap(mc);
        TextureBinder.restoreDefaultTextureUnit();
    }

    protected void resetShaderResourceBindings() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        TextureBinder.unbindAllTextureTargets();
        unbindShaderImages();
        self().unbindShaderStorageBuffers(true);
        disablePipelineVertexAttributes();
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
    }

    protected void restoreVanillaTextureBindingsAfterPipeline() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            TextureBinder.restoreDefaultTextureUnit();
            MinecraftReflectionCompat.glStateBindTexture(0);
            return;
        }

        self().restoreVanillaLightmapTexture(mc);

        TextureBinder.restoreDefaultTextureUnit();
        TextureManager textureManager = MinecraftReflectionCompat.textureManager(mc);
        if (textureManager != null) {
            MinecraftReflectionCompat.bindTexture(textureManager, MinecraftReflectionCompat.blocksTexture());
            ITextureObject atlasTexture = MinecraftReflectionCompat.texture(textureManager, MinecraftReflectionCompat.blocksTexture());
            if (atlasTexture != null) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, MinecraftReflectionCompat.glTextureId(atlasTexture));
            }
        } else {
            MinecraftReflectionCompat.glStateBindTexture(0);
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    protected void restoreVanillaLightmapTexture(Minecraft mc) {
        PipelineVanillaLightmapState.restore(mc);
    }

    protected void refreshVanillaLightmap(Minecraft mc) {
        PipelineVanillaLightmapState.refresh(mc);
    }

    protected void disableVanillaLightmap(Minecraft mc) {
        PipelineVanillaLightmapState.disable(mc);
    }

    protected void unbindShaderStorageBuffers() {
        self().unbindShaderStorageBuffers(false);
    }

    protected void unbindShaderStorageBuffers(boolean force) {
        if (!GLContext.getCapabilities().OpenGL43) {
            return;
        }
        if (!force && shaderStorageBuffersKnownUnbound()) {
            return;
        }

        if (force || !shaderStorageBuffers.active()) {
            int maxBindings = maxShaderStorageBufferBindings();
            for (int index = 0; index < maxBindings; index++) {
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, 0);
            }
        } else {
            for (int index : shaderStorageBuffers.bindingIndices()) {
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, 0);
            }
        }
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        markShaderStorageBuffersUnbound();
    }
}
