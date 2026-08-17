package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.mixin.pipeline.EntityRendererAccessor;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.bloom.BloomExtractionPlan;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.compat.CeleritasCompat;
import com.l.ausm.impl.pipeline.compat.NothiriumBypass;
import com.l.ausm.impl.pipeline.compat.NothiriumShadowRenderer;
import com.l.ausm.impl.pipeline.matrix.MatrixState;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import static com.l.ausm.impl.pipeline.PipelineCompatConstants.isNothiriumLoadedCached;
import static com.l.ausm.impl.pipeline.PipelineGlState.disablePipelineVertexAttributes;
import static com.l.ausm.impl.pipeline.PipelineGlState.resetIndexedBlendState;
import static com.l.ausm.impl.pipeline.PipelineGlState.unbindShaderImages;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_EXTERNAL_OVERLAY_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_SHADERLESS_BLOOM_HOOK_LOGS;
import static com.l.ausm.impl.pipeline.PipelineSkyConstants.CUSTOM_VOID_WORLD_OPTION;
import static com.l.ausm.impl.pipeline.PipelineSkyConstants.SIMPLE_VOID_WORLD_DIMENSION_ID;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.MAX_SHADERLESS_BLOOM_LOCAL_CHUNK_REFRESHES_PER_UPDATE;
import static com.l.ausm.impl.pipeline.pack.PipelineShaderSettings.optionBoolean;

abstract class PipelineChunkUpdateTracking extends PipelineBotaniaSkyRendering {
    public void handleClientBlockRenderUpdateRange(World world, int minX, int minY, int minZ,
                                                   int maxX, int maxY, int maxZ) {
        if (!(world instanceof WorldClient worldClient)) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) != world) {
            return;
        }

        int startX = Math.min(minX, maxX) >> 4;
        int endX = Math.max(minX, maxX) >> 4;
        int startZ = Math.min(minZ, maxZ) >> 4;
        int endZ = Math.max(minZ, maxZ) >> 4;
        int queued = 0;
        for (int chunkX = startX; chunkX <= endX; chunkX++) {
            for (int chunkZ = startZ; chunkZ <= endZ; chunkZ++) {
                self().queueClientChunkRenderRefresh(worldClient, chunkX, chunkZ,
                        CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE);
                queued++;
            }
        }
        if (queued > 0) {
            MainMod.LOGGER.info("[AUSMClientChunkRefresh] queued render-update range chunks={}..{} x {}..{} count={} world={}",
                    startX, endX, startZ, endZ, queued, self().safeDimensionId(world));
        }
    }

    public void handleShaderlessBloomRenderUpdateRange(World world, int minX, int minY, int minZ,
                                                       int maxX, int maxY, int maxZ) {
        if (world == null) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) != world) {
            return;
        }

        // Generic render updates are not proof that shaderless bloom sources changed.
        // Keep extraction metadata stable until the requested rebuild publishes its
        // compile summary; actual block changes invalidate through the block-update path.
    }

    protected boolean renderUpdateRangeContainsShaderlessBloomSource(World world, int minX, int minY, int minZ,
                                                                     int maxX, int maxY, int maxZ) {
        int startX = Math.min(minX, maxX);
        int endX = Math.max(minX, maxX);
        int startY = Math.clamp(Math.min(minY, maxY), 0, 255);
        int endY = Math.clamp(Math.max(minY, maxY), 0, 255);
        int startZ = Math.min(minZ, maxZ);
        int endZ = Math.max(minZ, maxZ);
        long volume = (long) (endX - startX + 1) * (long) (endY - startY + 1) * (long) (endZ - startZ + 1);
        if (volume <= 0L || volume > 4096L) {
            return false;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int y = startY; y <= endY; y++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int x = startX; x <= endX; x++) {
                    MinecraftReflectionCompat.mutableBlockPosSet(mutablePos, x, y, z);
                    if (!MinecraftReflectionCompat.worldIsBlockLoaded(world, mutablePos, false)) {
                        continue;
                    }
                    try {
                        IBlockState state = MinecraftReflectionCompat.worldBlockState(world, mutablePos);
                        if (stateHasShaderlessBloomSource(state)) {
                            return true;
                        }
                    } catch (RuntimeException | LinkageError ignored) {
                    }
                }
            }
        }
        return false;
    }

    protected void queueShaderlessBloomClientChunkRefreshes(World world, int sectionMinX, int sectionMaxX,
                                                            int sectionMinZ, int sectionMaxZ) {
        int startX = Math.min(sectionMinX, sectionMaxX);
        int endX = Math.max(sectionMinX, sectionMaxX);
        int startZ = Math.min(sectionMinZ, sectionMaxZ);
        int endZ = Math.max(sectionMinZ, sectionMaxZ);
        int queued = 0;
        for (int sectionZ = startZ; sectionZ <= endZ; sectionZ++) {
            for (int sectionX = startX; sectionX <= endX; sectionX++) {
                self().queueShaderlessBloomClientChunkRefresh(world, sectionX, sectionZ);
                queued++;
                if (queued >= MAX_SHADERLESS_BLOOM_LOCAL_CHUNK_REFRESHES_PER_UPDATE) {
                    return;
                }
            }
        }
    }

    protected void queueShaderlessBloomClientChunkRefresh(World world, int chunkX, int chunkZ) {
        if (world instanceof WorldClient worldClient) {
            self().queueClientChunkRenderRefresh(worldClient, chunkX, chunkZ, CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM);
        }
    }

    protected boolean invalidateShaderlessBloomMetadataSection(int dimension, int sectionX, int sectionY, int sectionZ) {
        boolean hadBloomMetadata = false;
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (layer == null) {
                continue;
            }
            long key = BloomExtractionPlan.metadataKey(dimension, sectionX, sectionY, sectionZ, layer);
            hadBloomMetadata |= shaderlessBloomMetadataChunkLayers.remove(key);
            shaderlessBloomMetadataKnownChunkLayers.remove(key);
        }
        return hadBloomMetadata;
    }

    protected boolean hasShaderlessBloomMetadata() {
        return !shaderlessBloomMetadataChunkLayers.isEmpty();
    }

    public boolean isShaderlessBloomExtractionActive() {
        return shaderlessBloomExtractionActive;
    }

    public boolean shouldRenderShaderlessBloomChunkLayer(BlockRenderLayer layer, int chunkBlockX, int chunkBlockY, int chunkBlockZ) {
        return self().shouldRenderShaderlessBloomChunkLayer(
                layer,
                chunkBlockX,
                chunkBlockY,
                chunkBlockZ,
                self().shaderlessBloomExtractionDimensionId()
        );
    }

    public int shaderlessBloomExtractionDimensionId() {
        return shaderlessBloomExtractionActive ? self().currentClientDimensionId() : Integer.MIN_VALUE;
    }

    public boolean shouldRenderShaderlessBloomChunkLayer(BlockRenderLayer layer, int chunkBlockX, int chunkBlockY,
                                                         int chunkBlockZ, int dimension) {
        if (!shaderlessBloomExtractionActive) {
            return true;
        }
        if (layer == null) {
            return false;
        }
        if (AusmBloomLayer.isBloomLayer(layer) || shaderlessBloomExtractionBootstrapActive) {
            return true;
        }
        long key = BloomExtractionPlan.metadataKey(
                dimension,
                chunkBlockX >> 4,
                chunkBlockY >> 4,
                chunkBlockZ >> 4,
                layer
        );
        return shaderlessBloomMetadataChunkLayers.contains(key);
    }

    public void prepareShaderlessOptimizedBloomDraw() {
    }

    protected int currentClientDimensionId() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        WorldProvider provider = MinecraftReflectionCompat.worldProvider(world);
        return provider != null
                ? MinecraftReflectionCompat.providerDimension(provider)
                : Integer.MIN_VALUE;
    }

    protected boolean isSimpleVoidWorld(World world) {
        WorldProvider provider = MinecraftReflectionCompat.worldProvider(world);
        return provider != null
                && MinecraftReflectionCompat.providerDimension(provider) == SIMPLE_VOID_WORLD_DIMENSION_ID;
    }

    public boolean isCustomVoidWorldSkyEnabled(World world) {
        return isPipelineActive
                && self().isSimpleVoidWorld(world)
                && shaderProperties != null
                && optionBoolean(shaderProperties, CUSTOM_VOID_WORLD_OPTION, false);
    }

    public boolean shouldUseOwnedSkyOverrideWorld(World world) {
        return self().isSimpleVoidWorld(world) || self().isOverworldShaderEnvironment(world);
    }

    protected int renderShaderlessBloomExtractionGeometry(Minecraft mc, Entity viewEntity, boolean allowPipelineActive) {
        return self().renderBloomExtractionGeometry(mc, viewEntity, allowPipelineActive);
    }

    protected int renderBloomExtractionGeometry(Minecraft mc, Entity viewEntity, boolean allowPipelineActive) {
        if (mc == null || viewEntity == null) {
            return 0;
        }
        float partialTicks = MinecraftReflectionCompat.renderPartialTicks(mc);
        if (!isPipelineActive && MinecraftReflectionCompat.entityRenderer(mc) != null) {
            ((EntityRendererAccessor) MinecraftReflectionCompat.entityRenderer(mc)).ausm$setupCameraTransform(partialTicks, 2);
            MatrixState.captureGbufferMatrices();
        }
        return self().renderEmissiveExtractionTerrain(partialTicks, viewEntity, allowPipelineActive);
    }

    protected int renderEmissiveExtractionTerrain(float partialTicks, Entity viewEntity, boolean allowPipelineActive) {
        if ((!allowPipelineActive && isPipelineActive) || viewEntity == null) {
            return 0;
        }
        if (CeleritasCompat.installed()
                || !NothiriumShadowRenderer.isAvailable()
                || NothiriumBypass.shouldBypass()) {
            return self().renderVanillaEmissiveTerrain(partialTicks, viewEntity, allowPipelineActive);
        }
        return self().renderNothiriumEmissiveExtractionTerrain(partialTicks, viewEntity);
    }

    protected int renderNothiriumEmissiveExtractionTerrain(float partialTicks, Entity viewEntity) {
        double cameraX = interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity),
                MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity),
                MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity),
                MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        nothiriumShadowRenderer.drainUploads();

        WorldRenderingPhase previousPhase = activePhase;
        boolean previousShaderlessWorldPassActive = shaderlessWorldPassActive;
        if (!isPipelineActive) {
            shaderlessWorldPassActive = true;
        }
        try {
            activePhase = WorldRenderingPhase.TERRAIN_SOLID;
            int solid = self().renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.SOLID, cameraX, cameraY, cameraZ);
            activePhase = WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED;
            int cutoutMipped = self().renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.CUTOUT_MIPPED, cameraX, cameraY, cameraZ);
            activePhase = WorldRenderingPhase.TERRAIN_CUTOUT;
            int cutout = self().renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.CUTOUT, cameraX, cameraY, cameraZ);
            activePhase = WorldRenderingPhase.TERRAIN_TRANSLUCENT;
            int translucent = self().renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.TRANSLUCENT, cameraX, cameraY, cameraZ);
            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            int bloom = PipelineContext.shouldRenderSyntheticBloomLayerWithRenderGlobal(bloomLayer) && mc != null && MinecraftReflectionCompat.renderGlobal(mc) != null
                    ? self().renderShaderlessVanillaEmissiveLayerIfVisible(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, bloomLayer, partialTicks, viewEntity)
                    : 0;
            return solid + cutoutMipped + cutout + translucent + bloom;
        } finally {
            activePhase = previousPhase;
            shaderlessWorldPassActive = previousShaderlessWorldPassActive;
        }
    }

    protected int renderShaderlessNothiriumExtractionLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ) {
        if (!shouldRenderShaderlessExtractionLayer(layer)) {
            return 0;
        }
        boolean forceBloomLayerEmission = AusmBloomLayer.isBloomLayer(layer);
        bloomRenderer.setShaderlessForceEmission(forceBloomLayerEmission ? 1.0F : 0.0F);
        try {
            return PipelineContext.positiveCount(nothiriumShadowRenderer.renderVisibleLayer(layer, cameraX, cameraY, cameraZ, 0, (short) 0));
        } finally {
            if (forceBloomLayerEmission) {
                bloomRenderer.setShaderlessForceEmission(0.0F);
            }
        }
    }

    protected int renderVanillaEmissiveTerrain(float partialTicks, Entity viewEntity, boolean allowPipelineActive) {
        if (!allowPipelineActive && isPipelineActive) {
            return 0;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.renderGlobal(mc) == null || MinecraftReflectionCompat.world(mc) == null || viewEntity == null) {
            return 0;
        }

        WorldRenderingPhase previousPhase = activePhase;
        boolean previousShaderlessWorldPassActive = shaderlessWorldPassActive;
        if (!isPipelineActive) {
            shaderlessWorldPassActive = true;
        }
        try {
            int rendered = 0;
            for (BlockRenderLayer layer : BloomExtractionPlan.terrainLayers()) {
                rendered += self().renderShaderlessVanillaEmissiveLayerIfVisible(
                        mc, BloomExtractionPlan.phaseFor(layer), layer, partialTicks, viewEntity);
            }
            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            int bloom = PipelineContext.shouldRenderSyntheticBloomLayerWithRenderGlobal(bloomLayer)
                    ? self().renderShaderlessVanillaEmissiveLayerIfVisible(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, bloomLayer, partialTicks, viewEntity)
                    : 0;
            return rendered + bloom;
        } finally {
            activePhase = previousPhase;
            shaderlessWorldPassActive = previousShaderlessWorldPassActive;
        }
    }

    protected int renderShaderlessVanillaEmissiveLayerIfVisible(Minecraft mc, WorldRenderingPhase phase, BlockRenderLayer layer,
                                                                float partialTicks, Entity viewEntity) {
        if (!shouldRenderShaderlessExtractionLayer(layer)) {
            return 0;
        }
        return self().renderShaderlessVanillaEmissiveLayer(mc, phase, layer, partialTicks, viewEntity);
    }

    protected int renderShaderlessVanillaEmissiveLayer(Minecraft mc, WorldRenderingPhase phase, BlockRenderLayer layer,
                                                       float partialTicks, Entity viewEntity) {
        activePhase = phase;
        boolean forceBloomLayerEmission = AusmBloomLayer.isBloomLayer(layer);
        prepareShaderlessBlockLayerState(layer);
        bloomRenderer.setShaderlessForceEmission(forceBloomLayerEmission ? 1.0F : 0.0F);
        try {
            RenderGlobal renderGlobal = MinecraftReflectionCompat.renderGlobal(mc);
            return renderGlobal != null ? PipelineContext.positiveCount(MinecraftReflectionCompat.renderBlockLayer(renderGlobal, layer, partialTicks, 2, viewEntity)) : 0;
        } finally {
            if (forceBloomLayerEmission) {
                bloomRenderer.setShaderlessForceEmission(0.0F);
            }
            finishShaderlessBlockLayerState(layer);
            activePhase = WorldRenderingPhase.NONE;
        }
    }

    protected static boolean shouldRenderSyntheticBloomLayerWithRenderGlobal(BlockRenderLayer layer) {
        return BloomExtractionPlan.shouldRenderSyntheticLayer(layer, PipelineContext.isNothiriumLoaded());
    }

    protected static boolean isNothiriumLoaded() {
        return PipelineCompatConstants.isNothiriumLoadedCached();
    }

    /**
     * The framed host-copy route needs Nothirium's multi-layer region builder.
     * Other backends compile the synthetic BLOOM layer separately and use the
     * dispatcher cache instead.
     */
    public boolean hasNothiriumBloomBackend() {
        return PipelineContext.isNothiriumLoaded() && NothiriumShadowRenderer.isAvailable();
    }

    protected static int floorDouble(double value) {
        int truncated = (int) value;
        return value < (double) truncated ? truncated - 1 : truncated;
    }

    protected static int positiveCount(int count) {
        return Math.max(0, count);
    }

    protected void logShaderlessBloomHook(String detail) {
        if (shaderlessBloomHookLogs >= MAX_SHADERLESS_BLOOM_HOOK_LOGS) {
            return;
        }
        shaderlessBloomHookLogs++;
        MainMod.LOGGER.info("[AUSMBloom] Shaderless pre-GUI hook {}", detail);
    }

    protected String bloomMetadataSummary() {
        return "known=" + shaderlessBloomMetadataKnownChunkLayers.size()
                + ", bloom=" + shaderlessBloomMetadataChunkLayers.size()
                + ", extractionActive=" + shaderlessBloomExtractionActive
                + ", bootstrap=" + shaderlessBloomExtractionBootstrapActive;
    }

    protected static String glStateSummary() {
        return FixedFunctionGlState.summary();
    }

    public void prepareExternalOverlayRender(String source) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.minecraftFramebuffer(mc) == null) {
            return;
        }

        if (externalOverlayLogs < MAX_EXTERNAL_OVERLAY_LOGS) {
            externalOverlayLogs++;
            MainMod.LOGGER.info("[PipelineCompat] Preparing external overlay renderer: {} active={} worldFrame={} gui={} framebuffer={}",
                    source,
                    isPipelineActive,
                    worldFrameActive,
                    renderingGuiScreen(),
                    self().describeFramebufferTarget(MinecraftReflectionCompat.minecraftFramebuffer(mc)));
        }

        if (isPipelineActive
                && worldFrameActive
                && externalWorldFramebufferTarget == null
                && !self().isRenderingBetterPortalsNestedView()) {
            self().prepareFramebufferPresentation();
        }

        bindMinecraftFramebufferForGui(mc);
        if (MinecraftReflectionCompat.entityRenderer(mc) != null) {
            MinecraftReflectionCompat.disableLightmap(MinecraftReflectionCompat.entityRenderer(mc));
        }
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.glStateBindTexture(0);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableColorMaterial();
        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    public void finishExternalOverlayRender(String source) {
        self().restoreGuiSafeRenderState(source);
    }

    public void finishExternalWorldOverlayRender(String source) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (!isPipelineActive && (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.renderViewEntity(mc) == null)) {
            return;
        }
        self().restoreWorldSafeRenderState(source);
    }

    protected void restoreGuiSafeRenderState(String source) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        bindMinecraftFramebufferForGui(mc);
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderImages();
        self().unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateBindTexture(0);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableColorMaterial();
        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        if (mc != null && MinecraftReflectionCompat.currentScreen(mc) != null) {
            MinecraftReflectionCompat.glStateDisableDepth();
            MinecraftReflectionCompat.glStateDepthMask(false);
        }
    }

    protected void restoreWorldSafeRenderState(String source) {
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        self().unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateBindTexture(0);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        bindBlockAtlas();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        MinecraftReflectionCompat.glStateEnableCull();
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableColorMaterial();
        MinecraftReflectionCompat.glStateDisableBlend();
    }

    public void restoreActiveWorldPassAfterExternalShader() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || renderingGuiScreen()) {
            return;
        }
        if (BetterPortalsCompat.isRenderingRenderPass()
                || self().isRenderingBetterPortalsNestedView()) {
            return;
        }

        RenderPass pass = activePass;
        WorldRenderingPhase phase = activePhase;
        bindWorldFramebuffer();
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        self().unbindShaderStorageBuffers();
        TextureBinder.restoreDefaultTextureUnit();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindPass(pass);
        activePhase = phase;
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:restore-active-after-external pass=" + pass + " phase=" + phase);
    }
}
