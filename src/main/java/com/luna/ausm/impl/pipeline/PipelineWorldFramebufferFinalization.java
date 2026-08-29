package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.pack.ShaderAlphaTest;
import com.luna.ausm.api.pipeline.pack.ShaderBlendMode;
import com.luna.ausm.api.pipeline.pack.ShaderProgramDirectives;
import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.render.FullscreenQuad;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.pipeline.shader.ComputeProgram;
import com.luna.ausm.impl.pipeline.shader.FullscreenArrayProgram;
import com.luna.ausm.impl.pipeline.shader.FullscreenProgramArray;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.pipeline.shader.ShaderKey;
import com.luna.ausm.impl.pipeline.shader.ShaderProgram;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

import static com.luna.ausm.impl.pipeline.PipelineGlState.resetIndexedBlendState;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_SOFT_VANILLA_FRAME_TIMING_LOGS;

abstract class PipelineWorldFramebufferFinalization extends PipelineDeferredPresentation {
    protected void finishWorldFramebuffer(Framebuffer target, boolean externalTarget) {
        self().finishWorldFramebuffer(target, externalTarget, true);
    }

    protected void finishWorldFramebuffer(Framebuffer target, boolean externalTarget, boolean renderPostBloom) {
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:finish-world-before-reset external=" + externalTarget
                + " target=" + self().describeFramebufferTarget(target));
        self().logBetterPortalsPipeline("finish-before-reset", "external=" + externalTarget
                + ", target=" + self().describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + self().framebufferStatus(target)
                + ", postBloom=" + renderPostBloom);
        self().logSkyPresentationRouteProbe("finish-before-reset", target,
                pingPongManager == null ? null : pingPongManager.getReadBuffer(),
                programs.get(RenderPass.FINAL));
        MinecraftReflectionCompat.bindFramebuffer(target, false);
        PipelineFrameLayerCapture.capturePreBloomPresentation(target);
        if (renderPostBloom) {
            self().renderPostWorldBloom(target, externalTarget);
        }
        if (!externalTarget) {
            self().snapshotPresentationTargetForDirectPresentation(target, renderPostBloom ? "finish-world-post-bloom" : "finish-world-direct-color");
        }
        if (!externalTarget) {
            MinecraftReflectionCompat.glStateClearDepth(1.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }
        PipelineFrameLayerCapture.captureFinalPresentation(pipelineFrameId, target);
        self().logSoftVanillaFrameTimingProbe(externalTarget);
        self().resetPipelineState(target);
        drainPausedPostRenderGlErrors("world-finish");
        worldFrameActive = false;
        if (!externalTarget && worldLoadPresentationGuardFrames > 0) {
            worldLoadPresentationGuardFrames--;
        }
        self().logBetterPortalsPipeline("finish-after-reset", "external=" + externalTarget);
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:finish-world-after-reset external=" + externalTarget);
    }

    protected void logSoftVanillaFrameTimingProbe(boolean externalTarget) {
        if (!isComplementarySoftVanillaStartupFallbackActive()
                || softVanillaFrameTimingLogs >= MAX_SOFT_VANILLA_FRAME_TIMING_LOGS
                || currentWorldFrameStartNanos == Long.MIN_VALUE) {
            return;
        }
        long now = System.nanoTime();
        long ready = currentWorldFrameReadyNanos != Long.MIN_VALUE ? currentWorldFrameReadyNanos : currentWorldFrameStartNanos;
        long finishStart = currentWorldFrameFinishStartNanos != Long.MIN_VALUE ? currentWorldFrameFinishStartNanos : ready;
        long afterNativeBloom = currentWorldFrameAfterNativeBloomNanos != Long.MIN_VALUE ? currentWorldFrameAfterNativeBloomNanos : finishStart;
        long blitStart = currentWorldFrameBlitStartNanos != Long.MIN_VALUE ? currentWorldFrameBlitStartNanos : afterNativeBloom;
        double totalMs = PipelineContext.nanosToMillis(now - currentWorldFrameStartNanos);
        if (softVanillaFrameTimingLogs >= 8 && totalMs < 50.0D) {
            return;
        }
        softVanillaFrameTimingLogs++;
        MainMod.LOGGER.info(
                "[AUSMSoftVanillaFrameTiming] call={} frame={} frameTime={} totalMs={} beginMs={} worldRenderMs={} nativeBloomMs={} preBlitGapMs={} blitFinishMs={} external={} activePass={} phase={} terrainOpaqueLayers={} terrainOpaqueDraws={} presentationHold={} snapshotAge={} glProgram={}",
                softVanillaFrameTimingLogs,
                pipelineFrameId,
                PipelineContext.formatMillis(currentFrameTime * 1000.0D),
                PipelineContext.formatMillis(totalMs),
                PipelineContext.formatMillis(PipelineContext.nanosToMillis(ready - currentWorldFrameStartNanos)),
                PipelineContext.formatMillis(PipelineContext.nanosToMillis(finishStart - ready)),
                PipelineContext.formatMillis(PipelineContext.nanosToMillis(afterNativeBloom - finishStart)),
                PipelineContext.formatMillis(PipelineContext.nanosToMillis(blitStart - afterNativeBloom)),
                PipelineContext.formatMillis(PipelineContext.nanosToMillis(now - blitStart)),
                externalTarget,
                activePass,
                getPhase(),
                terrainOpaqueLayerCount,
                terrainOpaqueDrawCount,
                compositeInvalidFallbackFrames,
                compositeInvalidFallbackSnapshotHasScene ? String.valueOf(pipelineFrameId - compositeInvalidFallbackSnapshotFrame) : "none",
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        );
    }

    protected void clearPresentationTarget(Framebuffer target, String reason) {
        if (target == null || isExternalWorldFramebufferTarget(target)) {
            return;
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousScissor = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
        try {
            MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            GL11.glDepthMask(previousDepthMask);
            if (previousScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    protected void clearWorldLoadPresentationFramebuffer(Minecraft mc) {
        if (worldLoadPresentationGuardFrames <= 0 || mc == null) {
            return;
        }
        Framebuffer target = MinecraftReflectionCompat.minecraftFramebuffer(mc);
        if (target == null || isExternalWorldFramebufferTarget(target)) {
            return;
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            GL11.glDepthMask(previousDepthMask);
        }
    }

    public void clearWorldLoadWindowBackbuffer(Minecraft mc) {
        if (worldLoadPresentationGuardFrames <= 0 || mc == null) {
            return;
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL11.glDrawBuffer(GL11.GL_BACK);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            GL11.glDepthMask(previousDepthMask);
        }
    }

    protected void runFullscreenPasses(RenderPass[] passes) {
        for (RenderPass pass : passes) {
            PipelineProgram program = programs.get(pass);
            if (program != null && program.hasOwnProgram()) {
                self().runFullscreenPass(program);
            }
        }
    }

    protected void runFullscreenPasses(ProgramArrayId arrayId) {
        if (arrayId == ProgramArrayId.SHADOWCOMP) {
            self().runShadowCompPasses();
            return;
        }

        List<ComputeProgram> computes = computeProgramArrays.getOrDefault(arrayId, List.of());
        List<FullscreenArrayProgram> indexedPrograms = fullscreenArrayPrograms.getOrDefault(arrayId, List.of());
        FullscreenProgramArray array = fullscreenProgramArrays.get(arrayId);
        List<RenderPass> fixedPasses = array == null ? List.of() : array.fixedPasses();
        int maxIndex = Math.max(PipelineContext.maxComputeArrayIndex(computes), PipelineContext.maxFullscreenArrayProgramIndex(indexedPrograms));
        if (!fixedPasses.isEmpty()) {
            maxIndex = Math.max(maxIndex, fixedPasses.size() - 1);
        }

        RenderPass computeBindingPass = self().computeBindingPass(arrayId);
        for (int index = 0; index <= maxIndex; index++) {
            self().runComputeProgramsForArrayIndex(computes, index, computeBindingPass);

            if (index < fixedPasses.size()) {
                PipelineProgram program = programs.get(fixedPasses.get(index));
                if (program != null && program.hasOwnProgram()) {
                    self().runFullscreenPass(program);
                }
            }

            for (FullscreenArrayProgram program : indexedPrograms) {
                if (program.index() == index && program.hasProgram()) {
                    self().runFullscreenArrayProgram(program);
                }
            }
        }
    }

    protected void runShadowCompPasses() {
        int size = shadowFramebuffer != null ? shadowFramebuffer.resolution() : 1;
        List<ComputeProgram> computes = computeProgramArrays.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of());
        List<FullscreenArrayProgram> indexedPrograms = fullscreenArrayPrograms.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of());
        int maxIndex = Math.max(PipelineContext.maxComputeArrayIndex(computes), PipelineContext.maxFullscreenArrayProgramIndex(indexedPrograms));
        for (int index = 0; index <= maxIndex; index++) {
            self().runComputeProgramsForArrayIndex(computes, index, RenderPass.SHADOW, size, size);
            for (FullscreenArrayProgram program : indexedPrograms) {
                if (program.index() == index && program.hasProgram()) {
                    self().runShadowCompArrayProgram(program);
                }
            }
        }
    }

    protected void runComputeProgramsForArrayIndex(List<ComputeProgram> computes, int index, RenderPass bindingPass) {
        self().runComputeProgramsForArrayIndex(computes, index, bindingPass, -1, -1);
    }

    protected void runComputeProgramsForArrayIndex(List<ComputeProgram> computes, int index, RenderPass bindingPass, int width, int height) {
        if (computes == null || computes.isEmpty()) {
            return;
        }
        List<ComputeProgram> indexedComputes = new ArrayList<>();
        for (ComputeProgram compute : computes) {
            if (compute != null && compute.arrayIndex() == index) {
                indexedComputes.add(compute);
            }
        }
        if (indexedComputes.isEmpty()) {
            return;
        }
        if (width > 0 && height > 0) {
            self().runComputePrograms(indexedComputes, bindingPass, width, height);
        } else {
            self().runComputePrograms(indexedComputes, bindingPass);
        }
    }

    protected static int maxComputeArrayIndex(List<ComputeProgram> computes) {
        int max = -1;
        if (computes != null) {
            for (ComputeProgram compute : computes) {
                if (compute != null) {
                    max = Math.max(max, compute.arrayIndex());
                }
            }
        }
        return max;
    }

    protected static int maxFullscreenArrayProgramIndex(List<FullscreenArrayProgram> programs) {
        int max = -1;
        if (programs != null) {
            for (FullscreenArrayProgram program : programs) {
                if (program != null && program.hasProgram()) {
                    max = Math.max(max, program.index());
                }
            }
        }
        return max;
    }

    protected void runSetupComputesIfNeeded() {
        if (!setupComputePending) {
            return;
        }
        setupComputePending = false;
        self().runFullscreenPasses(ProgramArrayId.SETUP);
    }

    protected RenderPass computeBindingPass(ProgramArrayId arrayId) {
        if (arrayId == ProgramArrayId.SETUP || arrayId == ProgramArrayId.BEGIN || arrayId == ProgramArrayId.PREPARE) {
            return RenderPass.PREPARE;
        }
        if (arrayId == ProgramArrayId.DEFERRED) {
            return RenderPass.DEFERRED;
        }
        if (arrayId == ProgramArrayId.COMPOSITE) {
            return RenderPass.COMPOSITE;
        }
        if (arrayId == ProgramArrayId.SHADOWCOMP) {
            return RenderPass.SHADOW;
        }
        return RenderPass.FINAL;
    }

    protected void runFullscreenArrayProgram(FullscreenArrayProgram program) {
        List<Attachment> drawBuffers = program.drawBuffers();
        Attachment[] drawBufferArray = drawBuffers.toArray(new Attachment[0]);

        pingPongManager.copyReadToWrite(drawBufferArray);
        pingPongManager.bindForFullscreenWrite(drawBufferArray);
        self().generateReadMipmaps(program.directives());

        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        self().setupFullscreenState();
        try {
            self().applyFullscreenViewport(program.name(), program.directives(), drawBuffers);
            self().applyFullscreenArrayRenderState(program.directives(), drawBuffers);
            self().bindFullscreenArrayProgram(program);
            FullscreenQuad.draw();
        } finally {
            if (program.shaderProgram() != null) {
                program.shaderProgram().unbind();
            }
            self().restoreFullscreenState();
            activePass = previousPass;
            activeShaderKey = previousShaderKey;
            activePhase = previousPhase;
            activeProgramTessellated = previousProgramTessellated;
            activeProgramGeometric = previousProgramGeometric;
            TextureBinder.restoreDefaultTextureUnit();
        }

        Attachment[] flippedAttachments = program.directives().flippedAttachments(drawBuffers);
        pingPongManager.flipWrittenTextures(flippedAttachments);
        self().generateWrittenMipmaps(program.directives(), flippedAttachments);
        if (program.arrayId() == ProgramArrayId.COMPOSITE) {
            PipelineFrameLayerCapture.captureCompositeOutputs(
                    pipelineFrameId,
                    program.name(),
                    drawBuffers,
                    pingPongManager.getReadBuffer()
            );
        }
    }

    protected void bindFullscreenArrayProgram(FullscreenArrayProgram program) {
        ShaderProgram shaderProgram = program.shaderProgram();
        if (shaderProgram == null) {
            return;
        }

        RenderPass bindingPass = program.bindingPass();
        activePass = bindingPass;
        activeShaderKey = ShaderKey.fromRenderPass(bindingPass);
        activePhase = WorldRenderingPhase.NONE;
        activeProgramTessellated = shaderProgram.isTessellated();
        activeProgramGeometric = shaderProgram.isGeometric();
        TextureBinder.bindDeferredTextures();
        TextureBinder.bindShadowTextures(bindingPass);
        shaderProgram.bind();
        bindProgramResources(bindingPass, shaderProgram);
        customTextures.bind(program.arrayId(), program.index(), shaderProgram);
    }

    protected void applyFullscreenArrayRenderState(ShaderProgramDirectives directives, List<Attachment> drawBuffers) {
        ShaderAlphaTest alphaTest = directives.alphaTestOverride();
        if (alphaTest != null) {
            currentAlphaTestReference = alphaTest.reference();
            if (alphaTest.function() == GL11.GL_ALWAYS) {
                MinecraftReflectionCompat.glStateDisableAlpha();
            } else {
                MinecraftReflectionCompat.glStateEnableAlpha();
            }
            MinecraftReflectionCompat.glStateAlphaFunc(alphaTest.function(), alphaTest.reference());
        }

        ShaderBlendMode blendMode = directives.blendModeOverride();
        Map<Attachment, ShaderBlendMode> attachmentModes = directives.attachmentBlendModes();
        if (blendMode == null && attachmentModes.isEmpty()) {
            return;
        }
        if (blendMode != null && !blendMode.enabled()) {
            MinecraftReflectionCompat.glStateDisableBlend();
            resetIndexedBlendState();
            return;
        }

        MinecraftReflectionCompat.glStateEnableBlend();
        if (blendMode != null) {
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    blendMode.srcRgb(),
                    blendMode.dstRgb(),
                    blendMode.srcAlpha(),
                    blendMode.dstAlpha()
            );
        }
        for (int drawBufferIndex = 0; drawBufferIndex < drawBuffers.size(); drawBufferIndex++) {
            ShaderBlendMode attachmentMode = attachmentModes.get(drawBuffers.get(drawBufferIndex));
            if (attachmentMode != null) {
                applyIndexedBlendMode(drawBufferIndex, attachmentMode);
            }
        }
    }

    protected void runComputePrograms(List<ComputeProgram> computes, RenderPass bindingPass) {
        if (computes == null || computes.isEmpty()) {
            return;
        }
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        int width = framebuffer != null ? framebuffer.getWidth() : mc != null ? MinecraftReflectionCompat.displayWidth(mc) : 1;
        int height = framebuffer != null ? framebuffer.getHeight() : mc != null ? MinecraftReflectionCompat.displayHeight(mc) : 1;
        self().runComputePrograms(computes, bindingPass, width, height);
    }

    protected void runComputePrograms(List<ComputeProgram> computes, RenderPass bindingPass, int width, int height) {
        if (computes == null || computes.isEmpty()) {
            return;
        }
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        for (ComputeProgram compute : computes) {
            if (compute == null) {
                continue;
            }
            self().applyComputeMemoryBarrier(compute.hasIndirectPointer());
            compute.bind();
            TextureBinder.bindDeferredTextures();
            TextureBinder.bindShadowTextures(bindingPass);
            bindProgramResources(bindingPass, compute.program());
            if (compute.hasIndirectPointer()) {
                int bufferId = shaderStorageBuffers.glBufferId(compute.indirectBuffer());
                if (bufferId != 0) {
                    GL15.glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, bufferId);
                    GL43.glDispatchComputeIndirect(compute.indirectOffset());
                    GL15.glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, 0);
                } else {
                    MainMod.LOGGER.warn(
                            "[Pipeline] Skipping indirect compute '{}' because SSBO binding {} is unavailable",
                            compute.name(),
                            compute.indirectBuffer()
                    );
                }
            } else {
                int[] groups = compute.workGroups(safeWidth, safeHeight);
                GL43.glDispatchCompute(groups[0], groups[1], groups[2]);
            }
            self().applyComputeMemoryBarrier(false);
        }
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
    }

    protected void applyComputeMemoryBarrier(boolean indirectDispatch) {
        int barriers = 0;
        if (shaderProperties == null || !shaderProperties.renderSettings().allowConcurrentCompute()) {
            barriers |= GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                    | GL43.GL_SHADER_STORAGE_BARRIER_BIT
                    | GL42.GL_TEXTURE_FETCH_BARRIER_BIT
                    | GL42.GL_FRAMEBUFFER_BARRIER_BIT;
        }
        if (indirectDispatch) {
            barriers |= GL42.GL_COMMAND_BARRIER_BIT;
        }
        if (barriers != 0) {
            GL42.glMemoryBarrier(barriers);
        }
    }

    protected void applyShaderImageTextureBarrier() {
        if (shaderImages.active() && GLContext.getCapabilities().OpenGL42) {
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
        }
    }

    protected void runFullscreenPass(PipelineProgram program) {
        List<Attachment> drawBuffers = program.drawBuffers();
        Attachment[] drawBufferArray = drawBuffers.toArray(new Attachment[0]);

        PipelineFrameLayerCapture.beginIfRequested(pipelineFrameId, pingPongManager.getReadBuffer());
        // Older Complementary-style packs use colortex2 for water auxiliary
        // data during gbuffers_water, then use its alternate texture as TAA
        // history in COMPOSITE5.  Keep the water data available to the earlier
        // composite passes, but switch to the untouched history texture before
        // COMPOSITE5 copies it forward for COMPOSITE6.
        if (program.pass() == RenderPass.COMPOSITE5 && drawBuffers.contains(Attachment.NORMAL)) {
            pingPongManager.flipWrittenTextures(Attachment.NORMAL);
        }
        pingPongManager.copyReadToWrite(drawBufferArray);
        pingPongManager.bindForFullscreenWrite(drawBufferArray);
        self().generateReadMipmaps(program);

        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        self().setupFullscreenState();
        try {
            self().applyFullscreenViewport(program, drawBuffers);
            self().applyFullscreenArrayRenderState(program.directives(), drawBuffers);
            if (self().bindFullscreenPipelineProgram(program)) {
                FullscreenQuad.draw();
            }
        } finally {
            ShaderProgram shaderProgram = program.shaderProgram();
            if (shaderProgram != null) {
                shaderProgram.unbind();
            }
            self().restoreFullscreenState();
            activePass = previousPass;
            activeShaderKey = previousShaderKey;
            activePhase = previousPhase;
            activeProgramTessellated = previousProgramTessellated;
            activeProgramGeometric = previousProgramGeometric;
            TextureBinder.restoreDefaultTextureUnit();
        }

        Attachment[] flippedAttachments = program.directives().flippedAttachments(drawBuffers);
        pingPongManager.flipWrittenTextures(flippedAttachments);
        self().generateWrittenMipmaps(program, flippedAttachments);
        if (program.stage() == ProgramStage.COMPOSITE) {
            PipelineFrameLayerCapture.captureCompositeOutputs(
                    pipelineFrameId,
                    program.pass().name(),
                    drawBuffers,
                    pingPongManager.getReadBuffer()
            );
        }
    }

    protected boolean bindFullscreenPipelineProgram(PipelineProgram program) {
        if (program == null || program.shaderProgram() == null) {
            return false;
        }

        ShaderProgram shaderProgram = program.shaderProgram();
        activePass = program.pass();
        activeShaderKey = program.shaderKey();
        activePhase = WorldRenderingPhase.NONE;
        activeProgramTessellated = shaderProgram.isTessellated();
        activeProgramGeometric = shaderProgram.isGeometric();
        TextureBinder.bindDeferredTextures();
        TextureBinder.bindShadowTextures(program.pass());
        shaderProgram.bind();
        bindProgramResources(program.pass(), shaderProgram);
        self().logFullscreenSamplerProbe(program, shaderProgram);
        self().logLightShaftInputProbe(program, shaderProgram);
        return true;
    }
}
