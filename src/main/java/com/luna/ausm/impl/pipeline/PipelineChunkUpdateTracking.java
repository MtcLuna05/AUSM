package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.mixin.pipeline.EntityRendererAccessor;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.compat.NothiriumBypass;
import com.luna.ausm.impl.pipeline.compat.NothiriumShadowRenderer;
import com.luna.ausm.impl.pipeline.matrix.MatrixState;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
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

import static com.luna.ausm.impl.pipeline.PipelineGlState.disablePipelineVertexAttributes;
import static com.luna.ausm.impl.pipeline.PipelineGlState.resetIndexedBlendState;
import static com.luna.ausm.impl.pipeline.PipelineGlState.unbindShaderImages;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_EXTERNAL_OVERLAY_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_SHADERLESS_BLOOM_HOOK_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineSkyConstants.CUSTOM_VOID_WORLD_OPTION;
import static com.luna.ausm.impl.pipeline.PipelineSkyConstants.SIMPLE_VOID_WORLD_DIMENSION_ID;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.MAX_SHADERLESS_BLOOM_LOCAL_CHUNK_REFRESHES_PER_UPDATE;
import static com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings.optionBoolean;

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
        // Complementary owns its overworld sky and cloud data across the
        // normal sky/deferred sequence. Drawing AUSM's extra full-screen sky
        // backing there overwrites those intermediate attachments. Void-world
        // support still needs the owned route because it has no vanilla sky.
        return self().isSimpleVoidWorld(world)
                || self().isOverworldShaderEnvironment(world)
                && !self().isComplementaryFinalColorSourceSensitivePack();
    }

    protected static boolean shouldRenderSyntheticBloomLayerWithRenderGlobal(BlockRenderLayer layer) {
        return layer != null && (!AusmBloomLayer.isBloomLayer(layer) || !PipelineContext.isNothiriumLoaded());
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
