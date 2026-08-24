package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelinePresentationConstants.COMPOSITE_INVALID_FALLBACK_SOURCE;
import static com.luna.ausm.impl.pipeline.PipelinePresentationConstants.COMPOSITE_RECOVERY_COLOR_MIN_LUMA;
import static com.luna.ausm.impl.pipeline.PipelinePresentationConstants.COMPOSITE_RECOVERY_COLOR_MIN_MAX_CHANNEL;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_DIRECT_COLOR_PRESENT_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_PRE_DEFERRED_COLOR_RESTORE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_PRE_FINAL_DIRECT_PRESENT_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_SOFT_VANILLA_PRESENTATION_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_SAFE_TERRAIN_FALLBACKS;

abstract class PipelineDeferredPresentation extends PipelineDeferredPassOrchestration {
    protected boolean presentPreCompositeWithFinalPassIfNeeded(Framebuffer target,
                                                               Minecraft mc,
                                                               boolean externalTarget,
                                                               String reason) {
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (target == null
                || finalProgram == null
                || !finalProgram.hasOwnProgram()
                || !self().isSimpleVoidWorld(renderWorld(mc))) {
            return false;
        }
        self().logBetterPortalsPipeline(reason);
        self().logSkyPresentationRouteProbe(reason, target, pingPongManager.getReadBuffer(), finalProgram);
        self().renderFinalPass(target);
        logShaderedVoidSkyTargetProbe("after-" + reason, target);
        self().finishWorldFramebuffer(target, externalTarget);
        return true;
    }

    protected void blitReadBufferToPresentationTarget(DeferredFramebuffer readBuffer,
                                                      Framebuffer target,
                                                      Minecraft mc,
                                                      String reason,
                                                      boolean externalTarget,
                                                      boolean clearPresentation,
                                                      boolean probeTarget) {
        self().blitReadBufferAttachmentToPresentationTarget(readBuffer, fallbackColorAttachment(), target, mc, reason, externalTarget,
                clearPresentation, probeTarget);
    }

    protected void blitReadBufferAttachmentToPresentationTarget(DeferredFramebuffer readBuffer,
                                                                Attachment sourceAttachment,
                                                                Framebuffer target,
                                                                Minecraft mc,
                                                                String reason,
                                                                boolean externalTarget,
                                                                boolean clearPresentation,
                                                                boolean probeTarget) {
        self().blitReadBufferAttachmentToPresentationTarget(readBuffer, sourceAttachment, target, mc, reason,
                externalTarget, clearPresentation, probeTarget, true);
    }

    protected void blitReadBufferAttachmentToPresentationTarget(DeferredFramebuffer readBuffer,
                                                                Attachment sourceAttachment,
                                                                Framebuffer target,
                                                                Minecraft mc,
                                                                String reason,
                                                                boolean externalTarget,
                                                                boolean clearPresentation,
                                                                boolean probeTarget,
                                                                boolean renderPostBloom) {
        self().blitReadBufferAttachmentToPresentationTarget(readBuffer, sourceAttachment, target, mc, reason,
                externalTarget, clearPresentation, probeTarget, renderPostBloom, 1.0F);
    }

    protected void blitReadBufferAttachmentToPresentationTarget(DeferredFramebuffer readBuffer,
                                                                Attachment sourceAttachment,
                                                                Framebuffer target,
                                                                Minecraft mc,
                                                                String reason,
                                                                boolean externalTarget,
                                                                boolean clearPresentation,
                                                                boolean probeTarget,
                                                                boolean renderPostBloom,
                                                                float directPresentColorScale) {
        self().logBetterPortalsPipeline(reason);
        self().logSkyPresentationRouteProbe(reason, target, readBuffer, programs.get(RenderPass.FINAL));
        if (clearPresentation) {
            self().clearPresentationTarget(target, reason);
        }
        readBuffer.blitTo(
                sourceAttachment,
                MinecraftReflectionCompat.framebufferObject(target),
                MinecraftReflectionCompat.framebufferWidth(target),
                MinecraftReflectionCompat.framebufferHeight(target)
        );

        MinecraftReflectionCompat.bindFramebuffer(target, false);
        MinecraftReflectionCompat.glStateViewport(0, 0, framebufferWidth(target, mc), framebufferHeight(target, mc));
        if (probeTarget) {
            logShaderedVoidSkyTargetProbe("after-" + reason, target);
        }
        if (!renderPostBloom) {
            self().markDirectRecoveredWindowSource(readBuffer, sourceAttachment, target, directPresentColorScale);
            self().logDirectColorPresent(reason, readBuffer, sourceAttachment, target);
        }
        self().finishWorldFramebuffer(target, externalTarget, renderPostBloom);
    }

    protected void markDirectRecoveredWindowSource(DeferredFramebuffer readBuffer,
                                                   Attachment sourceAttachment,
                                                   Framebuffer target,
                                                   float colorScale) {
        if (readBuffer == null || !readBuffer.isUsable() || sourceAttachment == null || target == null) {
            self().clearDirectRecoveredWindowSource();
            return;
        }
        directRecoveredWindowSource = readBuffer;
        directRecoveredWindowAttachment = sourceAttachment;
        directRecoveredWindowFrame = pipelineFrameId;
        directRecoveredWindowTargetWidth = Math.max(1, MinecraftReflectionCompat.framebufferWidth(target));
        directRecoveredWindowTargetHeight = Math.max(1, MinecraftReflectionCompat.framebufferHeight(target));
        directRecoveredWindowColorScale = Float.isFinite(colorScale) ? Math.clamp(colorScale, 0.0F, 1.0F) : 1.0F;
    }

    protected void clearDirectRecoveredWindowSource() {
        directRecoveredWindowSource = null;
        directRecoveredWindowAttachment = null;
        directRecoveredWindowFrame = Long.MIN_VALUE;
        directRecoveredWindowTargetWidth = 0;
        directRecoveredWindowTargetHeight = 0;
        directRecoveredWindowColorScale = 1.0F;
    }

    public void invalidateWorldLoadPresentationState() {
        self().clearDirectRecoveredWindowSource();
        self().deleteDirectPresentationSnapshot();
        worldLoadPresentationGuardFrames = Math.max(worldLoadPresentationGuardFrames, 8);
        guiTargetContentFrame = Long.MIN_VALUE;
    }

    protected void deleteDirectPresentationSnapshot() {
        directPresentationValid = false;
        directPresentationFrame = Long.MIN_VALUE;
        directPresentationReason = "";
        directPresentationWidth = 0;
        directPresentationHeight = 0;
        if (directPresentationFbo > 0) {
            GL30.glDeleteFramebuffers(directPresentationFbo);
            directPresentationFbo = -1;
        }
        if (directPresentationTexture > 0) {
            GL11.glDeleteTextures(directPresentationTexture);
            directPresentationTexture = -1;
        }
    }

    protected void logDirectColorPresent(String reason,
                                         DeferredFramebuffer readBuffer,
                                         Attachment sourceAttachment,
                                         Framebuffer target) {
        if (directColorPresentLogs++ >= MAX_DIRECT_COLOR_PRESENT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDirectColorPresent] reason={} source={} sourceColor={} sourceDepth={} target={} targetColor={} targetDepth={} frame={} postBloomSkipped=true gl={}",
                reason,
                sourceAttachment,
                deferredFramebufferColorSamples(readBuffer, sourceAttachment),
                readBuffer != null ? framebufferIdDepthSamples(readBuffer.getFramebufferId(), readBuffer.getWidth(), readBuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0) : "none",
                self().describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                framebufferDepthSamples(target),
                pipelineFrameId,
                PipelineContext.glStateSummary()
        );
    }

    protected boolean deferredBufferHasSceneContent(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        for (int[] point : self().compositeFallbackProbePoints(width, height)) {
            float[] color = self().safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (!self().isFiniteColor(color) || self().isClearColor(color)) {
                continue;
            }
            float depth = self().safeReadDeferredDepth(framebuffer, point[0], point[1], width, height);
            if (Float.isFinite(depth) && depth < 0.99999f && !self().isFlatWhiteColor(color)) {
                return true;
            }
        }
        return false;
    }

    protected boolean deferredBufferHasPresentableTerrainColor(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int presentable = 0;
        for (int[] point : self().compositeFallbackProbePoints(width, height)) {
            float[] color = self().safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (self().isRecoverableColorOnlySceneColor(color)) {
                presentable++;
            }
        }
        return presentable >= 2;
    }

    protected boolean deferredBufferHasColorContent(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        for (int[] point : self().compositeFallbackProbePoints(width, height)) {
            float[] color = self().safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (self().isRecoverableColorOnlySceneColor(color)) {
                return true;
            }
        }
        return false;
    }

    protected boolean deferredBufferLooksFlatWhiteOrClear(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int total = 0;
        int flat = 0;
        int clearDepth = 0;
        for (int[] point : self().compositeFallbackProbePoints(width, height)) {
            total++;
            float[] color = self().safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (!self().isFiniteColor(color)) {
                continue;
            }
            if (self().isFlatWhiteColor(color) || self().isClearColor(color)) {
                flat++;
            }
            float depth = self().safeReadDeferredDepth(framebuffer, point[0], point[1], width, height);
            if (depth >= 0.99999f || !Float.isFinite(depth)) {
                clearDepth++;
            }
        }
        return total > 0 && flat == total && clearDepth >= Math.max(1, total - 1);
    }

    protected boolean shouldPresentColorBeforeFinal(DeferredFramebuffer framebuffer, Attachment colorAttachment) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS
                || framebuffer == null
                || colorAttachment == null
                || !self().deferredBufferHasColorContent(framebuffer, colorAttachment)
                || !self().isComplementaryFinalColorSourceSensitivePack()) {
            return false;
        }
        return self().deferredBufferLooksBlackOrClear(framebuffer, Attachment.COMPOSITE)
                || self().deferredBufferLooksNeutralGrayOrClear(framebuffer, Attachment.COMPOSITE);
    }

    protected boolean shouldPresentPreFinalDirectlyForNothirium(DeferredFramebuffer framebuffer,
                                                                Attachment colorAttachment,
                                                                Minecraft mc) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS
                || framebuffer == null
                || colorAttachment == null
                || !framebuffer.isUsable()
                || !isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || renderingGuiScreen()
                || externalWorldFramebufferTarget != null
                || self().isRenderingBetterPortalsNestedView()
                || !self().isComplementaryFinalColorSourceSensitivePack()
                || !shouldUseNothiriumMainTerrainBridge()
                || !self().deferredBufferHasColorContent(framebuffer, colorAttachment)) {
            return false;
        }
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (finalProgram == null || !finalProgram.hasOwnProgram()) {
            return false;
        }
        return hasSparseNothiriumMainTerrainEvidence();
    }

    protected void logPreFinalDirectPresent(DeferredFramebuffer framebuffer,
                                            Attachment colorAttachment,
                                            Framebuffer target) {
        if (preFinalDirectPresentLogs++ >= MAX_PRE_FINAL_DIRECT_PRESENT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMPreFinalDirectPresent] reason=nothirium-presentable-color source={} attachment={} sourceColor={} target={} frame={} sparseEvidence={} simpleVoid={} terrainCounts=opaque:{}/draw:{} gl={}",
                self().describeDeferredFramebuffer(framebuffer),
                colorAttachment,
                deferredFramebufferColorSamples(framebuffer, colorAttachment),
                self().describeFramebufferTargetDetailed(target),
                pipelineFrameId,
                hasSparseNothiriumMainTerrainEvidence(),
                self().isSimpleVoidWorld(renderWorld(MinecraftReflectionCompat.minecraft())),
                terrainOpaqueLayerCount,
                terrainOpaqueDrawCount,
                PipelineContext.glStateSummary()
        );
    }

    protected boolean isComplementaryFinalColorSourceSensitivePack() {
        String name = activePackName != null ? activePackName.toLowerCase(Locale.ROOT) : "";
        return name.contains("complementary")
                || name.contains("complimentary")
                || name.contains("entree")
                || name.contains("entrée");
    }

    protected boolean deferredBufferLooksNeutralGrayOrClear(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int total = 0;
        int neutralOrClear = 0;
        for (int[] point : self().compositeFallbackProbePoints(width, height)) {
            total++;
            float[] color = self().safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (!self().isFiniteColor(color) || self().isClearColor(color) || self().isNeutralGrayColor(color)) {
                neutralOrClear++;
            }
        }
        return total > 0 && neutralOrClear == total;
    }

    protected boolean deferredBufferLooksBlackOrClear(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int total = 0;
        int blackOrClear = 0;
        for (int[] point : self().compositeFallbackProbePoints(width, height)) {
            total++;
            float[] color = self().safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (!self().isFiniteColor(color) || self().isClearColor(color)) {
                blackOrClear++;
            }
        }
        return total > 0 && blackOrClear == total;
    }

    protected boolean framebufferTargetLooksBlackOrClear(Framebuffer target) {
        if (target == null) {
            return false;
        }
        int framebuffer = MinecraftReflectionCompat.framebufferObject(target);
        int width = Math.max(1, MinecraftReflectionCompat.framebufferWidth(target));
        int height = Math.max(1, MinecraftReflectionCompat.framebufferHeight(target));
        int readBuffer = framebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0;
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int total = 0;
        int blackOrClear = 0;
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(readBuffer);
            for (int[] point : self().compositeFallbackProbePoints(width, height)) {
                total++;
                terrainProbeColorPixel.clear();
                GL11.glReadPixels(
                        Math.clamp(point[0], 0, width - 1),
                        Math.clamp(point[1], 0, height - 1),
                        1,
                        1,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        terrainProbeColorPixel
                );
                int r = terrainProbeColorPixel.get(0) & 0xFF;
                int g = terrainProbeColorPixel.get(1) & 0xFF;
                int b = terrainProbeColorPixel.get(2) & 0xFF;
                int a = terrainProbeColorPixel.get(3) & 0xFF;
                if (a <= 2 || (r <= 2 && g <= 2 && b <= 2)) {
                    blackOrClear++;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
        return total > 0 && blackOrClear == total;
    }

    protected boolean restorePreDeferredColorIfDeferredBlackened(DeferredFramebuffer framebuffer,
                                                                 Attachment attachment,
                                                                 String reason) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS
                || !preDeferredColorSnapshotThisFrame
                || framebuffer == null
                || attachment == null
                || !framebuffer.hasRecoveryColorSnapshot()
                || !recoveryColorSnapshotHasPresentableContent(framebuffer)
                || !self().deferredBufferLooksBlackOrClear(framebuffer, attachment)) {
            return false;
        }
        boolean restored = pingPongManager.restoreRecoveryColorToReadAttachment(attachment);
        if (restored) {
            self().logPreDeferredColorRestore(framebuffer, attachment, reason);
        }
        return restored;
    }

    protected void logPreDeferredColorRestore(DeferredFramebuffer framebuffer, Attachment attachment, String reason) {
        if (preDeferredColorRestoreLogs++ >= MAX_PRE_DEFERRED_COLOR_RESTORE_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMPreDeferredColorRestore] reason={} target={} currentColor={} preservedColor={} depth={} depthtex1={} frame={}",
                reason,
                attachment,
                deferredFramebufferColorSamples(framebuffer, attachment),
                deferredFramebufferRecoveryColorSamples(framebuffer),
                framebuffer != null ? framebufferIdDepthSamples(framebuffer.getFramebufferId(), framebuffer.getWidth(), framebuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0) : "none",
                deferredDepthSampleSummary(framebuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT),
                pipelineFrameId
        );
    }

    protected int[][] compositeFallbackProbePoints(int width, int height) {
        int maxX = Math.max(0, width - 1);
        int maxY = Math.max(0, height - 1);
        return new int[][]{
                {width / 2, height / 2},
                {width / 4, height / 2},
                {(width * 3) / 4, height / 2},
                {width / 2, height / 4},
                {width / 2, (height * 3) / 4},
                {Math.min(maxX, width / 3), Math.min(maxY, height / 3)}
        };
    }

    protected float[] safeReadDeferredColor(DeferredFramebuffer framebuffer, Attachment attachment, int x, int y) {
        try {
            return framebuffer.readColorAt(attachment, x, y);
        } catch (RuntimeException | LinkageError e) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        }
    }

    protected float[] safeReadRecoveryColor(DeferredFramebuffer framebuffer, int x, int y) {
        try {
            return framebuffer.readRecoveryColorAt(x, y);
        } catch (RuntimeException | LinkageError e) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        }
    }

    protected float safeReadDeferredDepth(DeferredFramebuffer framebuffer, int x, int y, int colorWidth, int colorHeight) {
        try {
            int depthX = Math.clamp(
                    Math.round(x * (framebuffer.getWidth() - 1) / (float) Math.max(1, colorWidth - 1)),
                    0,
                    framebuffer.getWidth() - 1);
            int depthY = Math.clamp(
                    Math.round(y * (framebuffer.getHeight() - 1) / (float) Math.max(1, colorHeight - 1)),
                    0,
                    framebuffer.getHeight() - 1);
            return framebuffer.readDepthAtPixel(depthX, depthY);
        } catch (RuntimeException | LinkageError e) {
            return Float.NaN;
        }
    }

    protected float safeReadDeferredDepthSnapshot(DeferredFramebuffer framebuffer, int snapshotIndex, int x, int y) {
        try {
            return framebuffer.readDepthSamplerAtPixel(
                    snapshotIndex,
                    Math.clamp(x, 0, framebuffer.getWidth() - 1),
                    Math.clamp(y, 0, framebuffer.getHeight() - 1)
            );
        } catch (RuntimeException | LinkageError e) {
            return Float.NaN;
        }
    }

    protected boolean isFiniteColor(float[] color) {
        return color != null
                && color.length >= 4
                && Float.isFinite(color[0])
                && Float.isFinite(color[1])
                && Float.isFinite(color[2])
                && Float.isFinite(color[3]);
    }

    protected boolean isFlatWhiteColor(float[] color) {
        return color[0] >= 0.985f && color[1] >= 0.985f && color[2] >= 0.985f && color[3] >= 0.985f;
    }

    protected boolean isNeutralGrayColor(float[] color) {
        if (!self().isFiniteColor(color) || color[3] <= 0.001f) {
            return false;
        }
        float max = Math.max(color[0], Math.max(color[1], color[2]));
        float min = Math.min(color[0], Math.min(color[1], color[2]));
        return max >= 0.45f && max <= 0.95f && max - min <= 0.035f;
    }

    protected boolean isRecoverableColorOnlySceneColor(float[] color) {
        if (!self().isFiniteColor(color) || self().isClearColor(color) || self().isFlatWhiteColor(color)) {
            return false;
        }
        float maxChannel = Math.max(color[0], Math.max(color[1], color[2]));
        float luma = color[0] * 0.2126F + color[1] * 0.7152F + color[2] * 0.0722F;
        return maxChannel >= COMPOSITE_RECOVERY_COLOR_MIN_MAX_CHANNEL
                && luma >= COMPOSITE_RECOVERY_COLOR_MIN_LUMA;
    }

    protected boolean isClearColor(float[] color) {
        return color[3] <= 0.001f
                || (Math.max(color[0], Math.max(color[1], color[2])) <= 0.001f && color[3] >= 0.999f);
    }

    protected void logSoftVanillaPresentationProbe(String stage, DeferredFramebuffer framebuffer, Attachment attachment,
                                                   boolean currentHasScene, boolean cachedSnapshot, String selected,
                                                   long worldBlitStartNanos, long afterTranslucentsNanos) {
        if (!isComplementarySoftVanillaStartupFallbackActive()
                || softVanillaPresentationProbeLogs >= MAX_SOFT_VANILLA_PRESENTATION_PROBE_LOGS) {
            return;
        }
        softVanillaPresentationProbeLogs++;
        long now = System.nanoTime();
        long snapshotAge = compositeInvalidFallbackSnapshotHasScene
                ? pipelineFrameId - compositeInvalidFallbackSnapshotFrame
                : Long.MAX_VALUE;
        MainMod.LOGGER.info(
                "[AUSMSoftVanillaPresentationProbe] call={} stage={} selected={} pack={} currentHasScene={} cachedSnapshot={} snapshotAge={} holdFrames={} source={} preserved={} beginTranslucentsMs={} totalBeforeDecisionMs={} frame={} frameTime={} read={} currentColor={} preservedColor={} currentDepth={} glProgram={}",
                softVanillaPresentationProbeLogs,
                stage,
                selected,
                activePackName,
                currentHasScene,
                cachedSnapshot,
                snapshotAge == Long.MAX_VALUE ? "none" : String.valueOf(snapshotAge),
                compositeInvalidFallbackFrames,
                attachment,
                COMPOSITE_INVALID_FALLBACK_SOURCE,
                PipelineContext.formatMillis(PipelineContext.nanosToMillis(afterTranslucentsNanos - worldBlitStartNanos)),
                PipelineContext.formatMillis(PipelineContext.nanosToMillis(now - worldBlitStartNanos)),
                pipelineFrameId,
                PipelineContext.formatMillis(currentFrameTime * 1000.0D),
                self().describeDeferredFramebuffer(framebuffer),
                deferredFramebufferColorSamples(framebuffer, attachment),
                deferredFramebufferRecoveryColorSamples(framebuffer),
                framebuffer != null ? framebufferIdDepthSamples(framebuffer.getFramebufferId(), framebuffer.getWidth(), framebuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0) : "none",
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        );
    }

    protected static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    protected static String formatMillis(double millis) {
        return String.format(Locale.ROOT, "%.3f", millis);
    }
}
