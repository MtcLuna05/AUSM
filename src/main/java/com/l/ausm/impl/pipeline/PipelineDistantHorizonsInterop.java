package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.mixin.pipeline.EntityRendererAccessor;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.pipeline.shader.PipelineProgram;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static com.l.ausm.impl.pipeline.PipelineGlState.disablePipelineVertexAttributes;
import static com.l.ausm.impl.pipeline.PipelineGlState.resetIndexedBlendState;
import static com.l.ausm.impl.pipeline.PipelinePresentationConstants.COMPOSITE_INVALID_FALLBACK_MAX_SNAPSHOT_AGE_FRAMES;
import static com.l.ausm.impl.pipeline.PipelinePresentationConstants.COMPOSITE_INVALID_FALLBACK_SOURCE;
import static com.l.ausm.impl.pipeline.PipelinePresentationConstants.ENABLE_SYNCHRONOUS_CENTER_DEPTH_READBACK;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_COMPOSITE_INVALID_RESTORE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_DISTANT_HORIZONS_DIAGNOSTIC_LOGS;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS;

abstract class PipelineDistantHorizonsInterop extends PipelineShadowEntityCulling {
    protected String sampleDistantHorizonsCompositeTarget(Framebuffer target) {
        if (distantHorizonsDiagnosticLogs >= MAX_DISTANT_HORIZONS_DIAGNOSTIC_LOGS) {
            return "skipped";
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        int framebuffer = target != null ? MinecraftReflectionCompat.framebufferObject(target) : GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readBuffer = target != null && MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0;
        int width = target != null ? framebufferWidth(target, mc) : pingPongManager.width();
        int height = target != null ? framebufferHeight(target, mc) : pingPongManager.height();
        if (width <= 0 || height <= 0) {
            return "invalid-size";
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(readBuffer);
            int[][] points = new int[][]{
                    {width / 2, height / 2},
                    {width / 2, Math.max(0, height / 4)},
                    {width / 2, Math.max(0, height * 3 / 4)}
            };
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < points.length; i++) {
                distantHorizonsReadbackPixel.clear();
                GL11.glReadPixels(points[i][0], points[i][1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, distantHorizonsReadbackPixel);
                int r = distantHorizonsReadbackPixel.get(0) & 0xFF;
                int g = distantHorizonsReadbackPixel.get(1) & 0xFF;
                int b = distantHorizonsReadbackPixel.get(2) & 0xFF;
                int a = distantHorizonsReadbackPixel.get(3) & 0xFF;
                if (i > 0) {
                    builder.append(';');
                }
                builder.append(points[i][0]).append(',').append(points[i][1])
                        .append('=').append(r).append('/').append(g).append('/').append(b).append('/').append(a);
            }
            return builder.toString();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    protected boolean clearDistantHorizonsFramebufferIfNeeded() {
        long frameKey = self().currentDistantHorizonsFrameKey();
        if (distantHorizonsFramebufferClearFrame == frameKey) {
            return false;
        }

        self().clearDistantHorizonsFramebuffer();
        distantHorizonsFramebufferClearFrame = frameKey;
        return true;
    }

    protected long currentDistantHorizonsFrameKey() {
        if (clientRenderFrameNanos != Long.MIN_VALUE) {
            return clientRenderFrameNanos;
        }
        return pipelineFrameId;
    }

    protected String distantHorizonsProbeState(Object renderParam) {
        String renderParamSummary = self().distantHorizonsRenderParamSummary(renderParam);
        return "pass=" + currentDistantHorizonsPass
                + ", override=" + self().shouldUseDistantHorizonsFramebufferOverride()
                + ", suppressApply=" + self().shouldSuppressDistantHorizonsMinecraftApply()
                + ", active=" + isPipelineActive
                + ", worldFrame=" + worldFrameActive
                + ", shadow=" + renderingShadowMap
                + ", gui=" + renderingGuiScreen()
                + ", pingpong=" + pingPongManager.isInitialized()
                + ", ausmFbo=" + self().currentPipelineWorldFramebufferId()
                + ", fallbackAttachment=" + fallbackColorAttachment()
                + ", size=" + pingPongManager.width() + "x" + pingPongManager.height()
                + ", storedColorTex=" + distantHorizonsColorTextureId
                + ", storedDepthTex=" + distantHorizonsDepthTextureId
                + ", activeColorTex=" + self().activeDistantHorizonsTextureId("getActiveColorTextureId")
                + ", activeDepthTex=" + self().activeDistantHorizonsTextureId("getActiveDepthTextureId")
                + ", pendingComposite=" + distantHorizonsFramebufferPendingComposite
                + ", frame=" + self().currentDistantHorizonsFrameKey()
                + ", renderParam=" + renderParamSummary
                + ", gl={" + self().distantHorizonsGlStateSummary() + "}";
    }

    protected String distantHorizonsRenderParamSummary(Object renderParam) {
        if (renderParam == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(renderParam.getClass().getName());
        try {
            Object renderPass = renderParam.getClass().getField("renderPass").get(renderParam);
            builder.append(":renderPass=").append(renderPass);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            Object worldYOffset = renderParam.getClass().getField("worldYOffset").get(renderParam);
            builder.append(":worldYOffset=").append(worldYOffset);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return builder.toString();
    }

    protected String distantHorizonsGlStateSummary() {
        try {
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
            int viewportX = viewportBuffer.get(0);
            int viewportY = viewportBuffer.get(1);
            int viewportWidth = viewportBuffer.get(2);
            int viewportHeight = viewportBuffer.get(3);
            return "drawFbo=" + GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                    + ", readFbo=" + GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                    + ", drawBuffer=" + GL11.glGetInteger(GL11.GL_DRAW_BUFFER)
                    + ", readBuffer=" + GL11.glGetInteger(GL11.GL_READ_BUFFER)
                    + ", program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                    + ", vao=" + GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
                    + ", arrayBuffer=" + GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
                    + ", depthTest=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                    + ", depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                    + ", depthFunc=" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
                    + ", blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                    + ", cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE)
                    + ", viewport=" + viewportX + "/" + viewportY + "/" + viewportWidth + "/" + viewportHeight;
        } catch (RuntimeException | LinkageError exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    protected void logDistantHorizonsDiagnostic(String stage, String detail) {
        // Diagnostic disabled.
    }

    public void prepareExternalWorldOverlayRender() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        if (worldFrameActive) {
            pingPongManager.bindForGbuffers(fallbackColorAttachment());
        }
        MinecraftReflectionCompat.glUseProgram(0);
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderStorageBuffers();
        TextureBinder.restoreDefaultTextureUnit();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Lets GlobalFacades join the active translucent terrain pass without
     * replacing AUSM's shader or framebuffer ownership. A null return tells
     * the caller to use its ordinary fixed-function overlay format instead.
     */
    public VertexFormat externalFacadeTerrainVertexFormat() {
        int program = -1;
        try {
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        } catch (RuntimeException | LinkageError ignored) {
        }

        boolean available = isPipelineActive
                && worldFrameActive
                && !renderingShadowMap
                && activePass == RenderPass.GBUFFERS_WATER
                && getPhase() == WorldRenderingPhase.TERRAIN_TRANSLUCENT
                && ExtendedVertexFormats.PIPELINE_BLOCK != null
                && program > 0;
        String probeKey = available
                + ":" + isPipelineActive
                + ":" + worldFrameActive
                + ":" + renderingShadowMap
                + ":" + activePass
                + ":" + getPhase()
                + ":" + program;
        if (EXTERNAL_FACADE_TERRAIN_PROBE_KEYS.add(probeKey)) {
            int call = EXTERNAL_FACADE_TERRAIN_PROBE_LOGS.incrementAndGet();
            if (call <= 16) {
                MainMod.LOGGER.info(
                        "[AUSMGlobalFacadesTerrainProbe] call={} available={} active={} worldFrame={} shadow={} pass={} phase={} program={} format={}",
                        call,
                        available,
                        isPipelineActive,
                        worldFrameActive,
                        renderingShadowMap,
                        activePass,
                        getPhase(),
                        program,
                        ExtendedVertexFormats.PIPELINE_BLOCK
                );
            }
        }
        return available ? ExtendedVertexFormats.PIPELINE_BLOCK : null;
    }

    /**
     * Supplies the per-block material payload used by the pipeline format.
     */
    public void beginExternalFacadeBlock(IBlockState state, IBlockAccess blockAccess,
                                         BlockPos pos, BlockRenderLayer layer) {
        if (self().externalFacadeTerrainVertexFormat() == null
                || state == null
                || blockAccess == null
                || pos == null) {
            return;
        }

        IBlockState actualState = actualBlockRenderState(state, blockAccess, pos);
        IBlockState contextState = effectiveBlockRenderState(state, actualState, blockAccess, pos);
        if (contextState == null) {
            contextState = state;
        }
        int emission = blockRenderEmission(state, blockAccess, pos);
        int packedLightmap = MinecraftReflectionCompat.blockAccessCombinedLight(blockAccess, pos, emission);
        BlockRenderContext.configureBlock(
                blockEntityIdForActualState(contextState, blockAccess, pos),
                (short) MinecraftReflectionCompat.stateRenderTypeOrdinal(contextState),
                blockMetadataForActualState(contextState),
                MinecraftReflectionCompat.blockPosX(pos),
                MinecraftReflectionCompat.blockPosY(pos),
                MinecraftReflectionCompat.blockPosZ(pos),
                blockAccess,
                pos,
                false,
                false,
                packedLightmap,
                emission,
                stateHasBloomLayerGeometry(contextState),
                blockRenderAlpha(state, blockAccess, pos),
                customLiquidTintColor(state, blockAccess, pos),
                shouldUseCrystalOnlyEmission(actualState),
                shouldSeparateBlockAo(contextState)
        );
    }

    public void finishExternalFacadeBlock() {
        BlockRenderContext.clear();
    }

    protected void restoreVanillaWorldTextureBindings() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc != null && MinecraftReflectionCompat.entityRenderer(mc) != null) {
            DynamicTexture lightmapTexture = ((EntityRendererAccessor) MinecraftReflectionCompat.entityRenderer(mc)).ausm$getLightmapTexture();
            self().restoreVanillaLightmapTexture(mc);
            if (lightmapTexture != null) {
                int irisLightmapTextureId = irisLightmapTexture.updateFrom(lightmapTexture);
                if (irisLightmapTextureId > 0) {
                    TextureBinder.bindIrisLightmap(irisLightmapTextureId);
                } else {
                    TextureBinder.bindIrisLightmap(MinecraftReflectionCompat.glTextureId(lightmapTexture));
                }
            } else {
                TextureBinder.mirrorVanillaLightmapToIrisUnit();
            }
        } else {
            TextureBinder.mirrorVanillaLightmapToIrisUnit();
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    public void renderPreparePass() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        if (shaderProperties.renderSettings().prepareBeforeShadow()) {
            self().runPreparePassesBeforeShadowIfRequested();
            return;
        }

        self().runFullscreenPasses(ProgramArrayId.PREPARE);
    }

    protected void runPreparePassesBeforeShadowIfRequested() {
        if (!isPipelineActive
                || !pingPongManager.isInitialized()
                || !shaderProperties.renderSettings().prepareBeforeShadow()
                || preparePassesRenderedBeforeShadowThisFrame) {
            return;
        }

        preparePassesRenderedBeforeShadowThisFrame = true;
        self().runFullscreenPasses(ProgramArrayId.PREPARE);
    }

    public void snapshotOpaqueTerrainDepth() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }
        self().logColorBufferProbe("after-opaque-terrain");
        self().logDeferredBoundaryProbe("after-opaque-terrain", "beforeDepthSnapshot=true");
        self().copyPreTranslucentDepth();
        self().logDeferredBoundaryProbe("after-pre-translucent-depth-copy", "preDepthCopied=" + preTranslucentDepthCopiedThisFrame);
        if (!ENABLE_SYNCHRONOUS_CENTER_DEPTH_READBACK) {
            return;
        }

        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        if (framebuffer == null) {
            return;
        }
        centerDepth = framebuffer.readCenterDepth();
        if (Float.isFinite(centerDepth)) {
            centerDepthSmooth += (centerDepth - centerDepthSmooth) * smoothingFactor(centerDepthHalfLife, currentFrameTime);
            if (Math.abs(centerDepth - centerDepthSmooth) < 0.00001f) {
                centerDepthSmooth = centerDepth;
            }
            self().updateCenterDepthSmoothTexture();
        }
    }

    protected void snapshotCompositeInvalidFallbackSource() {
        self().clearCompositeInvalidFallbackSnapshot();
    }

    protected boolean snapshotCompositeInvalidFallbackSource(DeferredFramebuffer framebuffer,
                                                             Attachment attachment,
                                                             boolean allowColorOnly,
                                                             String stage) {
        self().clearCompositeInvalidFallbackSnapshot();
        return false;
    }

    protected boolean hasCompositeInvalidFallbackSnapshot(DeferredFramebuffer framebuffer) {
        return false;
    }

    protected boolean recoveryColorSnapshotHasPresentableContent(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.hasRecoveryColorSnapshot()) {
            return false;
        }
        int width = Math.max(1, framebuffer.getRecoveryColorWidth());
        int height = Math.max(1, framebuffer.getRecoveryColorHeight());
        int presentable = 0;
        for (int[] point : self().compositeFallbackProbePoints(width, height)) {
            float[] color = self().safeReadRecoveryColor(framebuffer, point[0], point[1]);
            if (self().isRecoverableColorOnlySceneColor(color)) {
                presentable++;
            }
        }
        return presentable >= 2;
    }

    protected boolean isCompositeInvalidFallbackSnapshotRecent() {
        if (!compositeInvalidFallbackSnapshotHasScene) {
            return false;
        }
        long age = pipelineFrameId - compositeInvalidFallbackSnapshotFrame;
        if (age < 0L || age > COMPOSITE_INVALID_FALLBACK_MAX_SNAPSHOT_AGE_FRAMES) {
            return false;
        }
        return true;
    }

    protected void clearCompositeInvalidFallbackSnapshot() {
        compositeInvalidFallbackFrames = 0;
        compositeInvalidFallbackSnapshotFrame = Long.MIN_VALUE;
        compositeInvalidFallbackSnapshotHasScene = false;
        DeferredFramebuffer framebuffer = pingPongManager != null ? pingPongManager.getReadBuffer() : null;
        if (framebuffer != null) {
            framebuffer.clearRecoveryColorSnapshot();
        }
    }

    protected boolean restoreCompositeInvalidSnapshotToPresentationAttachment(DeferredFramebuffer framebuffer,
                                                                              Attachment attachment,
                                                                              String reason) {
        self().clearCompositeInvalidFallbackSnapshot();
        return false;
    }

    protected boolean shouldForceCompositeInvalidPresentation(String reason) {
        return reason != null
                && !self().shouldSuppressCompositeRecoveryForSparseNothiriumTerrain()
                && reason.contains("after-composite")
                && terrainOpaqueDrawCount >= HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS;
    }

    protected boolean shouldSuppressCompositeRecoveryForSparseNothiriumTerrain() {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || !PipelineWorldRenderScope.isNothiriumLoaded()) {
            return false;
        }
        if (self().hasSparseNothiriumMainTerrainEvidence()) {
            return true;
        }
        return self().shouldUseNothiriumMainTerrainBridge()
                && terrainOpaqueLayerCount >= 3
                && terrainOpaqueDrawCount < HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS;
    }

    protected boolean hasSparseNothiriumMainTerrainEvidence() {
        return self().isCurrentOrRecentSparseNothiriumMainTerrainFrame()
                || (terrainLayerCountFrame == pipelineFrameId
                && terrainOpaqueLayerCount > 0
                && terrainOpaqueDrawCount < HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS);
    }

    protected boolean isCurrentOrRecentSparseNothiriumMainTerrainFrame() {
        if (nothiriumSparseMainTerrainFrame == Long.MIN_VALUE) {
            return false;
        }
        long age = pipelineFrameId - nothiriumSparseMainTerrainFrame;
        return age >= 0L && age <= 2L;
    }

    protected void markSparseNothiriumMainTerrainFrame(boolean nothiriumMainTerrain) {
        if (nothiriumMainTerrain
                && !softVanillaTerrainRenderer
                && worldFrameActive
                && !renderingShadowMap
                && PipelineWorldRenderScope.isNothiriumLoaded()
                && terrainLayerCountFrame == pipelineFrameId
                && terrainOpaqueLayerCount > 0
                && terrainOpaqueDrawCount < HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS) {
            nothiriumSparseMainTerrainFrame = pipelineFrameId;
            self().clearCompositeInvalidFallbackSnapshot();
        }
    }

    protected boolean restoreCompositeInvalidFinalSourceAttachment(DeferredFramebuffer framebuffer,
                                                                   Attachment primaryAttachment,
                                                                   String reason) {
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (framebuffer == null
                || primaryAttachment == Attachment.COMPOSITE
                || finalProgram == null
                || !finalProgram.hasOwnProgram()
                || reason == null
                || !reason.contains("before-final")) {
            return false;
        }
        // Complementary's final pass reads colortex3/COMPOSITE. If composite
        // flattened that buffer, restoring only colortex0 leaves final sampling
        // the flat neutral/white buffer even though COLOR was recovered.
        if (self().deferredBufferHasSceneContent(framebuffer, Attachment.COMPOSITE)) {
            return false;
        }
        return pingPongManager.restoreRecoveryColorToReadAttachment(Attachment.COMPOSITE);
    }

    protected boolean shouldRestoreCompositeInvalidDepth(DeferredFramebuffer framebuffer) {
        return framebuffer != null
                && !self().deferredLiveDepthHasSceneContent(framebuffer)
                && self().deferredDepthSnapshotHasSceneContent(framebuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT);
    }

    protected boolean deferredLiveDepthHasSceneContent(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return false;
        }
        int width = Math.max(1, framebuffer.getWidth());
        int height = Math.max(1, framebuffer.getHeight());
        for (int[] point : self().compositeFallbackProbePoints(width, height)) {
            float depth = self().safeReadDeferredDepth(framebuffer, point[0], point[1], width, height);
            if (Float.isFinite(depth) && depth < 0.99999f) {
                return true;
            }
        }
        return false;
    }

    protected boolean deferredDepthSnapshotHasSceneContent(DeferredFramebuffer framebuffer, int snapshotIndex) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return false;
        }
        int width = Math.max(1, framebuffer.getWidth());
        int height = Math.max(1, framebuffer.getHeight());
        for (int[] point : self().compositeFallbackProbePoints(width, height)) {
            float depth = self().safeReadDeferredDepthSnapshot(framebuffer, snapshotIndex, point[0], point[1]);
            if (Float.isFinite(depth) && depth < 0.99999f) {
                return true;
            }
        }
        return false;
    }

    protected void logCompositeInvalidRestore(DeferredFramebuffer framebuffer, Attachment attachment, String reason, boolean depthRestored) {
        if (compositeInvalidRestoreLogs++ >= MAX_COMPOSITE_INVALID_RESTORE_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMCompositeRecovery] action=restore-cached-scene reason={} source={} target={} depthRestored={} currentColor={} preservedColor={} depth={} depthtex1={}",
                reason,
                COMPOSITE_INVALID_FALLBACK_SOURCE,
                attachment,
                depthRestored,
                deferredFramebufferColorSamples(framebuffer, attachment),
                deferredFramebufferRecoveryColorSamples(framebuffer),
                framebuffer != null ? framebufferIdDepthSamples(framebuffer.getFramebufferId(), framebuffer.getWidth(), framebuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0) : "none",
                self().deferredDepthSampleSummary(framebuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT)
        );
    }

    protected void logColorBufferProbe(String stage) {
        self().logColorBufferProbe(stage, false);
    }
}
