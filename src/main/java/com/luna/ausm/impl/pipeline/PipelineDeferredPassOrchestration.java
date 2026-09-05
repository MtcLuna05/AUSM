package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.pack.ShaderEnvironmentDefines;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelinePresentationConstants.COMPOSITE_INVALID_FALLBACK_HOLD_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelinePresentationConstants.ENABLE_COMPOSITE_INVALID_PRESENTATION_RECOVERY;
import static com.luna.ausm.impl.pipeline.PipelinePresentationConstants.ENABLE_FLAT_COMPOSITE_SKY_ONLY_FINISH;
import static com.luna.ausm.impl.pipeline.PipelinePresentationConstants.ENABLE_SPARSE_STARTUP_PRESENTATION_HOLD;
import static com.luna.ausm.impl.pipeline.PipelinePresentationConstants.SPARSE_STARTUP_PRESENTATION_HOLD_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelinePresentationConstants.SPARSE_STARTUP_PRESENTATION_MIN_TERRAIN_DRAWS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_FLAT_FOLIAGE_HIGHLIGHT_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_LILY_PAD_LIGHTING_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_SPARSE_STARTUP_PRESENTATION_HOLD_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_SAFE_TERRAIN_FALLBACKS;
import static com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings.optionValue;

abstract class PipelineDeferredPassOrchestration extends PipelineContextBase {
    protected void logFlatFoliageHighlightProbe(DeferredFramebuffer framebuffer) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        World world = minecraft != null ? renderWorld(minecraft) : null;
        BlockPos pos = currentSelectedBlockPosition(minecraft);
        if (minecraft == null || world == null || pos == null || framebuffer == null || !framebuffer.isUsable()) {
            return;
        }

        IBlockState state = MinecraftReflectionCompat.worldBlockState(world, pos);
        int materialId = blockEntityId(state, world, pos);
        if (materialId != 10005 && materialId != 10021) {
            return;
        }

        int call = FLAT_FOLIAGE_HIGHLIGHT_PROBE_LOGS.incrementAndGet();
        if (call > MAX_FLAT_FOLIAGE_HIGHLIGHT_PROBE_LOGS) {
            return;
        }

        Attachment attachment = fallbackColorAttachment();
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int x = width / 2;
        int y = height / 2;
        MainMod.LOGGER.info(
                "[AUSMFlatFoliageHighlightProbe] call={} pos={} id={} registry={} state={} layer={} renderType={} material={} light={} emission={} worldTime={} celestial={} rain={} source={} sourceDepth={} lightHighlight={} frame={} pass={} phase={}",
                call,
                pos,
                materialId,
                registryName(state),
                state,
                safeRenderLayer(state),
                safeRenderType(state),
                MinecraftReflectionCompat.stateMaterial(state),
                safeLightValue(state, world, pos),
                blockRenderEmission(state, world, pos),
                MinecraftReflectionCompat.worldTime(world),
                MinecraftReflectionCompat.worldCelestialAngle(world, currentWorldPartialTicks),
                MinecraftReflectionCompat.worldRainStrength(world, currentWorldPartialTicks),
                Arrays.toString(self().safeReadDeferredColor(framebuffer, attachment, x, y)),
                self().safeReadDeferredDepth(framebuffer, x, y, width, height),
                optionValue(shaderProperties, "LIGHT_HIGHLIGHT"),
                pipelineFrameId,
                activePass,
                getPhase()
        );
    }

    protected void logLilyPadLightingProbe(DeferredFramebuffer framebuffer) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        World world = minecraft != null ? renderWorld(minecraft) : null;
        BlockPos pos = currentSelectedBlockPosition(minecraft);
        if (minecraft == null || world == null || pos == null || framebuffer == null || !framebuffer.isUsable()) {
            return;
        }

        IBlockState state = MinecraftReflectionCompat.worldBlockState(world, pos);
        if (blockEntityId(state, world, pos) != 10489) {
            return;
        }
        forensicTrace("lily-selected-lighting", "pos=" + pos + ", state=" + state
                + ", blockLight=" + MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.BLOCK, pos)
                + ", skyLight=" + MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.SKY, pos));

        int call = LILY_PAD_LIGHTING_PROBE_LOGS.incrementAndGet();
        if (call > MAX_LILY_PAD_LIGHTING_PROBE_LOGS) {
            return;
        }

        Attachment attachment = fallbackColorAttachment();
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int x = width / 2;
        int y = height / 2;
        MainMod.LOGGER.info(
                "[AUSMLilyPadLightingProbe] call={} pos={} registry={} state={} layer={} renderType={} material={} blockLight={} skyLight={} emission={} source={} sourceDepth={} wavingLilyPad={} pixelatedShadows={} shadowQuality={} frame={} pass={} phase={}",
                call,
                pos,
                registryName(state),
                state,
                safeRenderLayer(state),
                safeRenderType(state),
                MinecraftReflectionCompat.stateMaterial(state),
                MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.BLOCK, pos),
                MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.SKY, pos),
                blockRenderEmission(state, world, pos),
                Arrays.toString(self().safeReadDeferredColor(framebuffer, attachment, x, y)),
                self().safeReadDeferredDepth(framebuffer, x, y, width, height),
                optionValue(shaderProperties, "WAVING_LILY_PAD"),
                optionValue(shaderProperties, "PIXELATED_SHADOWS"),
                optionValue(shaderProperties, "SHADOW_QUALITY"),
                pipelineFrameId,
                activePass,
                getPhase()
        );
    }

    public void beginTranslucents() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }
        if (deferredPassesRenderedThisFrame) {
            return;
        }

        clearPendingPersistentHistoryIfNeeded();
        logDeferredBoundaryProbe("begin-translucents-entry", "beforeDepthCopy=true");
        copyPreTranslucentDepth();
        logDeferredBoundaryProbe("before-deferred", "preDepthCopied=" + preTranslucentDepthCopiedThisFrame);
        DeferredFramebuffer preDeferredBuffer = pingPongManager.getReadBuffer();
        self().logFlatFoliageHighlightProbe(preDeferredBuffer);
        self().logLilyPadLightingProbe(preDeferredBuffer);
        preDeferredColorSnapshotThisFrame = false;
        if (ENABLE_SAFE_TERRAIN_FALLBACKS
                && (self().deferredBufferHasColorContent(preDeferredBuffer, fallbackColorAttachment())
                || self().deferredBufferHasSceneContent(preDeferredBuffer, fallbackColorAttachment()))) {
            preDeferredColorSnapshotThisFrame = pingPongManager.snapshotReadAttachmentToRecoveryColor(fallbackColorAttachment());
        }
        self().runFullscreenPasses(ProgramArrayId.DEFERRED);
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        self().restorePreDeferredColorIfDeferredBlackened(readBuffer, fallbackColorAttachment(), "after-deferred");
        deferredPassesRenderedThisFrame = true;
        logDeferredBoundaryProbe("after-deferred", "deferredRendered=true");
        bindWorldFramebuffer();
    }

    protected void compositeLatestDistantHorizonsTexture(Framebuffer target) {
        if (shouldUseDistantHorizonsFramebufferOverride()) {
            distantHorizonsFramebufferPendingComposite = false;
            distantHorizonsColorTextureId = 0;
            distantHorizonsDepthTextureId = 0;
            return;
        }
        if (distantHorizonsColorTextureId == 0 || distantHorizonsDepthTextureId == 0) {
            return;
        }
        distantHorizonsFramebufferWidth = Math.max(1, pingPongManager.width());
        distantHorizonsFramebufferHeight = Math.max(1, pingPongManager.height());
        distantHorizonsFramebufferPendingComposite = true;
        compositeDistantHorizonsFramebuffer(target);
    }

    public boolean shouldRunDeferredBeforeParticlePhase(WorldRenderingPhase phase) {
        if (!isPipelineActive || phase == null) {
            return false;
        }

        String ordering = shaderProperties.renderSettings().particlesOrdering();
        if (ordering == null || ordering.isBlank() || "auto".equalsIgnoreCase(ordering)) {
            ordering = self().hasDeferredPrograms() ? "after" : "mixed";
        }

        return switch (ordering.trim().toLowerCase(Locale.ROOT)) {
            case "before" -> false;
            case "mixed" -> phase == WorldRenderingPhase.PARTICLES_TRANSLUCENT;
            case "after" -> true;
            default -> self().hasDeferredPrograms();
        };
    }

    protected boolean hasDeferredPrograms() {
        for (RenderPass pass : RenderPass.DEFERRED_PASSES) {
            PipelineProgram program = programs.get(pass);
            if (program != null && program.hasOwnProgram()) {
                return true;
            }
        }
        return !fullscreenArrayPrograms.getOrDefault(ProgramArrayId.DEFERRED, List.of()).isEmpty()
                || !computeProgramArrays.getOrDefault(ProgramArrayId.DEFERRED, List.of()).isEmpty();
    }

    public void beginHand() {
        self().beginTranslucents();
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        // OptiFine/Iris compress first-person geometry into a dedicated near
        // depth domain. Shader packs use MC_HAND_DEPTH (and Euphoria's 0.56
        // cutoff) to recognize those pixels during later post processing.
        // MC_HAND_DEPTH scales NDC Z around zero, not window depth around
        // zero. The equivalent window-depth interval is centred on 0.5.
        GL11.glDepthRange(0.5D - ShaderEnvironmentDefines.HAND_DEPTH * 0.5D,
                0.5D + ShaderEnvironmentDefines.HAND_DEPTH * 0.5D);
        if (preHandDepthCopiedThisFrame) {
            return;
        }

        // depthtex2 excludes both translucent and hand geometry in OptiFine/Iris.
        // The live depth buffer can already contain water here, so copy the same
        // pre-translucent snapshot instead of sampling the current depth.
        pingPongManager.copyPreTranslucentDepthToPreHandDepth();
        preHandDepthCopiedThisFrame = true;
    }

    public void finishHand() {
        if (!isPipelineActive) {
            return;
        }
        try {
            if (pingPongManager.isInitialized()) {
                // The opaque snapshot must contain the solid hand for volumetric
                // occlusion, but not translucent terrain. Keep depthtex2 hand-free.
                com.luna.ausm.impl.pipeline.fbo.HandDepthSnapshot.merge(pingPongManager.getReadBuffer());
            }
        } finally {
            GL11.glDepthRange(0.0D, 1.0D);
        }
    }

    public void blitWorldFramebufferToMinecraft() {
        if (!isPipelineActive || !pingPongManager.isInitialized() || !worldFrameActive) {
            return;
        }

        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        if (readBuffer == null) {
            self().resetPipelineState();
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        Framebuffer target = currentWorldFramebufferTarget(mc);
        if (target == null) {
            self().resetPipelineState();
            return;
        }
        long worldBlitStartNanos = System.nanoTime();
        currentWorldFrameBlitStartNanos = worldBlitStartNanos;
        long afterTranslucentsNanos = worldBlitStartNanos;
        boolean externalTarget = isExternalWorldFramebufferTarget(target);
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:world-blit-start external=" + externalTarget
                + " target=" + self().describeFramebufferTarget(target)
                + " read=" + self().describeDeferredFramebuffer(readBuffer));
        self().logSkyPresentationRouteProbe("world-blit-start", target, readBuffer, programs.get(RenderPass.FINAL));
        self().logBetterPortalsPipeline("blit-start", "target=" + self().describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + self().framebufferStatus(target));
        self().beginTranslucents();
        afterTranslucentsNanos = System.nanoTime();
        self().logBetterPortalsPipeline("after-translucents");
        readBuffer = pingPongManager.getReadBuffer();
        if (readBuffer == null) {
            self().logBetterPortalsPipeline("abort-null-read-after-translucents");
            self().resetPipelineState(target);
            return;
        }
        Attachment presentationAttachment = fallbackColorAttachment();
        if (!externalTarget && self().shouldHoldSparseNothiriumStartupPresentation(readBuffer, presentationAttachment)) {
            self().holdSparseStartupPresentation(target, "sparse-nothirium-startup");
            return;
        }
        if (ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain) {
            self().blitReadBufferToPresentationTarget(readBuffer, target, mc,
                    externalTarget ? "choose-external-hardware-safe-pre-composite-blit" : "choose-hardware-safe-pre-composite-blit",
                    externalTarget, true, true);
            return;
        }
        if (externalTarget) {
            logDeferredBoundaryProbe("before-composite", "external=true");
            self().runFullscreenPasses(ProgramArrayId.COMPOSITE);
            logDeferredBoundaryProbe("after-composite", "external=true");
            self().logBetterPortalsPipeline("after-external-composite");
            readBuffer = pingPongManager.getReadBuffer();
            if (readBuffer == null) {
                self().logBetterPortalsPipeline("abort-null-read-after-external-composite");
                self().resetPipelineState(target);
                return;
            }

            self().runComputePrograms(finalComputePrograms, RenderPass.FINAL);
            self().logBetterPortalsPipeline("after-external-final-compute");

            PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
            if (finalProgram != null && finalProgram.hasOwnProgram()) {
                self().logBetterPortalsPipeline("choose-external-final-pass");
                self().renderFinalPass(target);
                self().finishWorldFramebuffer(target, true);
                return;
            }

            self().blitReadBufferToPresentationTarget(readBuffer, target, mc,
                    "choose-external-composite-blit", true, false, false);
            return;
        }

        if (ENABLE_COMPOSITE_INVALID_PRESENTATION_RECOVERY
                && !shouldPresentPreCompositeForSoftVanillaStartupPack()
                && compositeInvalidFallbackFrames > 0) {
            compositeInvalidFallbackFrames = 0;
        }

        if (ENABLE_COMPOSITE_INVALID_PRESENTATION_RECOVERY
                && !shouldSuppressCompositeRecoveryForSparseNothiriumTerrain()) {
            snapshotCompositeInvalidFallbackSource(readBuffer, presentationAttachment, true,
                    "after-translucents-composite-invalid-snapshot");
        }

        boolean preCompositePresentation = shouldPresentPreCompositeForSoftVanillaStartupPack()
                || shouldPresentPreCompositeForNothiriumCompositeLoss();
        if (preCompositePresentation) {
            compositeInvalidFallbackFrames = COMPOSITE_INVALID_FALLBACK_HOLD_FRAMES;
            boolean currentHasScene = self().deferredBufferHasSceneContent(readBuffer, presentationAttachment)
                    || self().deferredBufferHasColorContent(readBuffer, presentationAttachment);
            boolean cachedSnapshot = hasCompositeInvalidFallbackSnapshot(readBuffer);
            if (currentHasScene) {
                if (pingPongManager.snapshotReadAttachmentToRecoveryColor(presentationAttachment)) {
                    compositeInvalidFallbackSnapshotFrame = pipelineFrameId;
                    compositeInvalidFallbackSnapshotHasScene = true;
                }
                self().logSoftVanillaPresentationProbe("soft-branch", readBuffer, presentationAttachment,
                        true, true, "composite-current", worldBlitStartNanos, afterTranslucentsNanos);
                if (self().presentPreCompositeWithFinalPassIfNeeded(target, mc, externalTarget,
                        "choose-nothirium-pre-composite-final-pass")) {
                    return;
                }
                self().blitReadBufferAttachmentToPresentationTarget(readBuffer, presentationAttachment, target, mc,
                        "choose-nothirium-pre-composite-blit", externalTarget, true, true);
                return;
            } else if (cachedSnapshot) {
                self().logSoftVanillaPresentationProbe("soft-branch", readBuffer, presentationAttachment,
                        false, true, "cached-no-current", worldBlitStartNanos, afterTranslucentsNanos);
                if (restoreCompositeInvalidSnapshotToPresentationAttachment(readBuffer, presentationAttachment, "soft-vanilla-cached-pre-composite")) {
                    if (self().presentPreCompositeWithFinalPassIfNeeded(target, mc, externalTarget,
                            "choose-soft-vanilla-cached-pre-composite-final-pass")) {
                        return;
                    }
                    self().blitReadBufferAttachmentToPresentationTarget(readBuffer, presentationAttachment, target, mc,
                            "choose-soft-vanilla-cached-pre-composite-blit", externalTarget, true, true);
                    return;
                }
            } else {
                self().logSoftVanillaPresentationProbe("soft-branch", readBuffer, presentationAttachment,
                        false, false, "composite-no-snapshot", worldBlitStartNanos, afterTranslucentsNanos);
            }
        }

        logDeferredBoundaryProbe("before-composite", "external=false");
        self().runFullscreenPasses(ProgramArrayId.COMPOSITE);
        self().logBetterPortalsPipeline("after-composite");
        logColorBufferProbe("after-composite");
        logDeferredBoundaryProbe("after-composite", "external=false");
        logShaderedVoidSkyTargetProbe("after-composite-before-final", target);
        readBuffer = pingPongManager.getReadBuffer();
        if (readBuffer == null) {
            self().logBetterPortalsPipeline("abort-null-read-after-composite");
            self().resetPipelineState(target);
            return;
        }
        if (ENABLE_SAFE_TERRAIN_FALLBACKS
                && preCompositePresentation
                && hasCompositeInvalidFallbackSnapshot(readBuffer)) {
            boolean compositeHasRenderableColor = self().deferredBufferHasSceneContent(readBuffer, presentationAttachment)
                    || self().deferredBufferHasColorContent(readBuffer, presentationAttachment);
            if (!compositeHasRenderableColor) {
                compositeInvalidFallbackFrames = COMPOSITE_INVALID_FALLBACK_HOLD_FRAMES;
                self().logSoftVanillaPresentationProbe("after-composite", readBuffer, presentationAttachment,
                        false, true, "cached-after-composite-lost-scene", worldBlitStartNanos, afterTranslucentsNanos);
                if (restoreCompositeInvalidSnapshotToPresentationAttachment(readBuffer, presentationAttachment, "soft-vanilla-after-composite")) {
                    if (self().presentPreCompositeWithFinalPassIfNeeded(target, mc, false,
                            "choose-soft-vanilla-after-composite-cached-pre-composite-final-pass")) {
                        return;
                    }
                    self().blitReadBufferAttachmentToPresentationTarget(readBuffer, presentationAttachment, target, mc,
                            "choose-soft-vanilla-after-composite-cached-pre-composite-blit", false, true, true);
                    return;
                }
            } else {
                self().logSoftVanillaPresentationProbe("after-composite", readBuffer, presentationAttachment,
                        true, true, "composite-current-color", worldBlitStartNanos, afterTranslucentsNanos);
            }
        }
        if (ENABLE_FLAT_COMPOSITE_SKY_ONLY_FINISH
                && self().deferredBufferLooksFlatWhiteOrClear(readBuffer, presentationAttachment)) {
            self().finishFlatCompositeSkyOnlyFrame(target, "flat-clear-after-composite");
            return;
        }
        if (self().restorePreDeferredColorIfDeferredBlackened(readBuffer, presentationAttachment, "after-composite")) {
            self().blitReadBufferAttachmentToPresentationTarget(readBuffer, presentationAttachment, target, mc,
                    "choose-pre-deferred-color-after-composite-black", false, true, true, false);
            return;
        }
        if (self().shouldPresentColorBeforeFinal(readBuffer, presentationAttachment)) {
            self().blitReadBufferAttachmentToPresentationTarget(readBuffer, presentationAttachment, target, mc,
                    "choose-color-before-final-invalid-source", false, true, true, false);
            return;
        }
        DeferredFramebuffer preFinalReadBuffer = readBuffer;
        Attachment preFinalPresentationAttachment = presentationAttachment;
        boolean canRecoverFromPreFinalColor = self().shouldPresentPreFinalDirectlyForNothirium(preFinalReadBuffer, preFinalPresentationAttachment, mc);
        self().runComputePrograms(finalComputePrograms, RenderPass.FINAL);
        self().logBetterPortalsPipeline("after-final-compute");
        logShaderedVoidSkyTargetProbe("after-final-compute", target);

        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (finalProgram != null && finalProgram.hasOwnProgram()) {
            self().logSkyPresentationRouteProbe("choose-final-pass", target, readBuffer, finalProgram);
            self().logBetterPortalsPipeline("choose-final-pass");
            self().renderFinalPass(target);
            logShaderedVoidSkyTargetProbe("after-final-pass", target);
            if (canRecoverFromPreFinalColor && self().framebufferTargetLooksBlackOrClear(target)) {
                self().logPreFinalDirectPresent(preFinalReadBuffer, preFinalPresentationAttachment, target);
                self().blitReadBufferAttachmentToPresentationTarget(preFinalReadBuffer, preFinalPresentationAttachment, target, mc,
                        "choose-nothirium-pre-final-after-final-black", false, true, true, false);
                return;
            }
            self().finishWorldFramebuffer(target, externalTarget);
            return;
        }

        self().blitReadBufferToPresentationTarget(readBuffer, target, mc,
                "choose-direct-blit", externalTarget, true, true);
    }

    protected boolean shouldHoldSparseNothiriumStartupPresentation(DeferredFramebuffer readBuffer, Attachment attachment) {
        return readBuffer != null
                && attachment != null
                && ENABLE_SPARSE_STARTUP_PRESENTATION_HOLD
                && isPipelineActive
                && worldFrameActive
                && !renderingShadowMap
                && !renderingGuiScreen()
                && shouldUseNothiriumMainTerrainBridge()
                && sparseStartupPresentationHoldFrames < SPARSE_STARTUP_PRESENTATION_HOLD_FRAMES
                && terrainOpaqueDrawCount < SPARSE_STARTUP_PRESENTATION_MIN_TERRAIN_DRAWS
                && hasSparseNothiriumMainTerrainEvidence();
    }

    protected void holdSparseStartupPresentation(Framebuffer target, String reason) {
        int holdFrame = ++sparseStartupPresentationHoldFrames;
        if (sparseStartupPresentationHoldLogs++ < MAX_SPARSE_STARTUP_PRESENTATION_HOLD_LOGS) {
            MainMod.LOGGER.info(
                    "[AUSMSparseStartupPresentation] action=hold reason={} hold={}/{} frame={} terrainCounts=opaque:{}/draw:{} sparseFrameAge={} target={} read={} color={} gl={}",
                    reason,
                    holdFrame,
                    SPARSE_STARTUP_PRESENTATION_HOLD_FRAMES,
                    pipelineFrameId,
                    terrainOpaqueLayerCount,
                    terrainOpaqueDrawCount,
                    nothiriumSparseMainTerrainFrame == Long.MIN_VALUE ? "none" : String.valueOf(pipelineFrameId - nothiriumSparseMainTerrainFrame),
                    self().describeFramebufferTargetDetailed(target),
                    self().describeDeferredFramebuffer(pingPongManager != null ? pingPongManager.getReadBuffer() : null),
                    deferredFramebufferColorSamples(pingPongManager != null ? pingPongManager.getReadBuffer() : null, fallbackColorAttachment()),
                    PipelineContext.glStateSummary()
            );
        }
        compositeInvalidFallbackFrames = 0;
        clearCompositeInvalidFallbackSnapshot();
        self().clearSparseStartupSkyOnlyTarget(target);
        self().logSoftVanillaFrameTimingProbe(false);
        self().resetPipelineState(target);
        drainPausedPostRenderGlErrors("world-finish-sparse-startup-hold");
        worldFrameActive = false;
        self().logBetterPortalsPipeline("finish-sparse-startup-hold", "reason=" + reason);
    }

    protected void clearSparseStartupSkyOnlyTarget(Framebuffer target) {
        if (target == null || isExternalWorldFramebufferTarget(target)) {
            return;
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        FloatBuffer previousClearColor = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);
        float[] color = self().sparseStartupSkyOnlyColor(MinecraftReflectionCompat.minecraft());
        try {
            MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
            MinecraftReflectionCompat.glStateDepthMask(true);
            MinecraftReflectionCompat.glStateClearDepth(1.0);
            GL11.glClearColor(PipelineContext.clampColorChannel(color[0]), PipelineContext.clampColorChannel(color[1]), PipelineContext.clampColorChannel(color[2]), 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL11.glClearColor(
                    previousClearColor.get(0),
                    previousClearColor.get(1),
                    previousClearColor.get(2),
                    previousClearColor.get(3)
            );
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
        }
    }

    protected void finishFlatCompositeSkyOnlyFrame(Framebuffer target, String reason) {
        compositeInvalidFallbackFrames = 0;
        clearCompositeInvalidFallbackSnapshot();
        self().clearSparseStartupSkyOnlyTarget(target);
        self().logSoftVanillaFrameTimingProbe(false);
        self().resetPipelineState(target);
        drainPausedPostRenderGlErrors("world-finish-flat-composite-sky-only");
        worldFrameActive = false;
        self().logBetterPortalsPipeline("finish-flat-composite-sky-only", "reason=" + reason);
    }

    protected static float clampColorChannel(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.clamp(value, 0.0F, 1.0F);
    }

    protected float[] sparseStartupSkyOnlyColor(Minecraft mc) {
        World world = renderWorld(mc);
        if (self().isSimpleVoidWorld(world)) {
            return new float[]{0.45F, 0.62F, 0.86F};
        }
        float[] color = skyColor(mc);
        float maxChannel = Math.max(color[0], Math.max(color[1], color[2]));
        if (maxChannel < 0.08F) {
            return new float[]{0.45F, 0.62F, 0.86F};
        }
        return color;
    }

    protected static float ausmOfficialNightFactor(World world) {
        long time = world != null ? MinecraftReflectionCompat.worldTime(world) % 24000L : 6000L;
        float timeAngle = (float) time / 24000.0F;
        return Math.max((float) Math.sin(timeAngle * -6.28318530718F), 0.0F);
    }
}
