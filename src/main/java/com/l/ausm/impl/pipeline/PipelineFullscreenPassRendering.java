package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.pack.ShaderProgramDirectives;
import com.l.ausm.api.pipeline.pack.ShaderViewportScale;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.pipeline.pack.ShaderBlockLayerOverrides;
import com.l.ausm.impl.pipeline.render.FullscreenQuad;
import com.l.ausm.impl.pipeline.render.ShaderSamplerState;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.pipeline.resource.ShaderImageSet;
import com.l.ausm.impl.pipeline.resource.ShaderStorageBufferSet;
import com.l.ausm.impl.pipeline.shader.FullscreenArrayProgram;
import com.l.ausm.impl.pipeline.shader.PipelineProgram;
import com.l.ausm.impl.pipeline.shader.ShaderKey;
import com.l.ausm.impl.pipeline.shader.ShaderProgram;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.IntBuffer;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static com.l.ausm.impl.pipeline.PipelineProbeLimits.DEBUG_PROBES_ENABLED;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_FULLSCREEN_SAMPLER_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_SAFE_TERRAIN_FALLBACKS;

abstract class PipelineFullscreenPassRendering extends PipelineWorldFramebufferFinalization {
    protected void logFullscreenSamplerProbe(PipelineProgram program, ShaderProgram shaderProgram) {
        if (program == null || shaderProgram == null
                || fullscreenSamplerProbeLogs >= MAX_FULLSCREEN_SAMPLER_PROBE_LOGS) {
            return;
        }
        RenderPass pass = program.pass();
        if (pass != RenderPass.DEFERRED1
                && pass != RenderPass.COMPOSITE1
                && pass != RenderPass.COMPOSITE3
                && pass != RenderPass.COMPOSITE5) {
            return;
        }

        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int liveColorTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(previousActiveTexture);
        int location = shaderProgram.getUniformLocation("colortex0");
        int samplerUnit = -1;
        if (location >= 0) {
            IntBuffer samplerValue = BufferUtils.createIntBuffer(1);
            GL20.glGetUniform(shaderProgram.getId(), location, samplerValue);
            samplerUnit = samplerValue.get(0);
        }
        fullscreenSamplerProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMFullscreenSamplerProbe] call={} pass={} expectedColor={} writeColor={} liveUnit0={} samplerUnit={} program={} drawFbo={} drawBuffers={}",
                fullscreenSamplerProbeLogs,
                pass,
                framebuffer != null ? framebuffer.getReadTexture(Attachment.COLOR) : -1,
                framebuffer != null ? framebuffer.getWriteTexture(Attachment.COLOR) : -1,
                liveColorTexture,
                samplerUnit,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                drawBuffersProbeSummary()
        );
    }

    protected void applyViewportScale(PipelineProgram program, int width, int height) {
        self().applyViewportScale(program.directives().viewportScale(), width, height);
    }

    protected void applyViewportScale(ShaderViewportScale scale, int width, int height) {
        MinecraftReflectionCompat.glStateViewport(scale.x(width), scale.y(height), scale.width(width), scale.height(height));
    }

    protected void applyFullscreenViewport(PipelineProgram program, List<Attachment> drawBuffers) {
        self().applyFullscreenViewport(program.pass().getProgramName(), program.directives(), drawBuffers);
    }

    protected void applyFullscreenViewport(String programName, ShaderProgramDirectives directives, List<Attachment> drawBuffers) {
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        if (framebuffer == null) {
            return;
        }
        int width = framebuffer.getWidth();
        int height = framebuffer.getHeight();
        if (!drawBuffers.isEmpty()) {
            Attachment first = drawBuffers.get(0);
            width = framebuffer.getAttachmentWidth(first);
            height = framebuffer.getAttachmentHeight(first);
            for (Attachment attachment : drawBuffers) {
                if (framebuffer.getAttachmentWidth(attachment) != width || framebuffer.getAttachmentHeight(attachment) != height) {
                    MainMod.LOGGER.warn("[Pipeline] Pass {} writes differently sized buffers; using {} size {}x{} for viewport",
                            programName, first, width, height);
                    break;
                }
            }
        }
        self().applyViewportScale(directives.viewportScale(), width, height);
    }

    protected void renderFinalPass(Framebuffer target) {
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (target == null || readBuffer == null || finalProgram == null) {
            self().logBetterPortalsPipeline("final-pass-skip", "target=" + self().describeFramebufferTargetDetailed(target)
                    + ", read=" + self().describeDeferredFramebuffer(readBuffer)
                    + ", final=" + self().describePipelineProgram(finalProgram));
            return;
        }

        self().logBetterPortalsPipeline("final-pass-start", "target=" + self().describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + self().framebufferStatus(target)
                + ", read=" + self().describeDeferredFramebuffer(readBuffer)
                + ", final=" + self().describePipelineProgram(finalProgram));
        logColorBufferProbe("before-final");
        logCompositeChainProbe("before-final-pass", "final=" + self().describePipelineProgram(finalProgram)
                + ", finalDrawBuffers=" + finalProgram.drawBuffers()
                + ", directivesDrawBuffers=" + finalProgram.directives().drawBuffers());
        logShaderedVoidSkyTargetProbe("final-before-clear", target);
        self().clearPresentationTarget(target, "final-pass");
        readBuffer.blitDepthTo(
                MinecraftReflectionCompat.framebufferObject(target),
                MinecraftReflectionCompat.framebufferWidth(target),
                MinecraftReflectionCompat.framebufferHeight(target)
        );

        MinecraftReflectionCompat.bindFramebuffer(target, false);
        GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        GL11.glColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateViewport(0, 0, MinecraftReflectionCompat.framebufferWidth(target), MinecraftReflectionCompat.framebufferHeight(target));
        self().generateReadMipmaps(finalProgram);

        self().setupFullscreenState();
        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        try {
            self().applyViewportScale(finalProgram, MinecraftReflectionCompat.framebufferWidth(target), MinecraftReflectionCompat.framebufferHeight(target));
            self().applyFullscreenArrayRenderState(finalProgram.directives(), finalProgram.drawBuffers());
            if (self().bindFullscreenPipelineProgram(finalProgram)) {
                self().logFinalSkyRepairProbe(finalProgram);
                FullscreenQuad.draw();
            }
        } finally {
            ShaderProgram shaderProgram = finalProgram.shaderProgram();
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

        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.glStateViewport(0, 0, MinecraftReflectionCompat.framebufferWidth(target), MinecraftReflectionCompat.framebufferHeight(target));
        logShaderedVoidSkyTargetProbe("final-after-draw", target);
        self().logBetterPortalsPipeline("final-pass-end", "target=" + self().describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + self().framebufferStatus(target));
    }

    protected void logFinalSkyRepairProbe(PipelineProgram finalProgram) {
        if (!DEBUG_PROBES_ENABLED || finalSkyRepairProbeLogs++ >= 48 || finalProgram == null) {
            return;
        }
        ShaderProgram shader = finalProgram.shaderProgram();
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        MainMod.LOGGER.info(
                "[AUSMFinalSkyRepairProbe] pass={} shader={} skybox={} ui={} simpleVoid={} screen={} hideGui={} paused={} locations={}/{}/{}/{}",
                finalProgram.pass(),
                shader == null ? "null" : shader.getName(),
                shouldRepairCurrentSkybox(mc),
                shouldForceUiSkyboxRepair(mc),
                self().isSimpleVoidWorld(renderWorld(mc)),
                MinecraftReflectionCompat.currentScreen(mc) != null,
                MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(mc)),
                MinecraftReflectionCompat.isGamePaused(mc),
                shader == null ? -2 : shader.getUniformLocation("ausmSkyboxRepair"),
                shader == null ? -2 : shader.getUniformLocation("ausmUiSkyRepair"),
                shader == null ? -2 : shader.getUniformLocation("depthtex0"),
                shader == null ? -2 : shader.getUniformLocation("colortex0")
        );
    }

    protected void logSkyPresentationRouteProbe(String route, Framebuffer target,
                                                DeferredFramebuffer readBuffer,
                                                PipelineProgram finalProgram) {
        if (!DEBUG_PROBES_ENABLED || skyPresentationRouteProbeLogs++ >= 64) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        ShaderProgram shader = finalProgram == null ? null : finalProgram.shaderProgram();
        MainMod.LOGGER.info(
                "[AUSMSkyRouteProbe] route={} final={} hasOwnFinal={} shader={} skybox={} ui={} simpleVoid={} screen={} hideGui={} target={} read={} locations={}/{}",
                route,
                self().describePipelineProgram(finalProgram),
                finalProgram != null && finalProgram.hasOwnProgram(),
                shader == null ? "null" : shader.getName(),
                shouldRepairCurrentSkybox(mc),
                shouldForceUiSkyboxRepair(mc),
                self().isSimpleVoidWorld(renderWorld(mc)),
                MinecraftReflectionCompat.currentScreen(mc) != null,
                MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(mc)),
                self().describeFramebufferTargetDetailed(target),
                self().describeDeferredFramebuffer(readBuffer),
                shader == null ? -2 : shader.getUniformLocation("ausmSkyboxRepair"),
                shader == null ? -2 : shader.getUniformLocation("ausmUiSkyRepair")
        );
    }

    public void logExternalSkyPresentationRouteProbe(String route, Framebuffer target) {
        self().logSkyPresentationRouteProbe(route, target, pingPongManager.getReadBuffer(), programs.get(RenderPass.FINAL));
    }

    protected void generateReadMipmaps(PipelineProgram program) {
        if (program != null) {
            self().generateReadMipmaps(program.directives());
        }
    }

    protected void generateReadMipmaps(ShaderProgramDirectives directives) {
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        if (directives != null && readBuffer != null && !directives.mipmappedBuffers().isEmpty()) {
            readBuffer.generateMipmaps(directives.mipmappedBuffers());
            TextureBinder.restoreDefaultTextureUnit();
        }
    }

    protected void generateWrittenMipmaps(PipelineProgram program, Attachment[] flippedAttachments) {
        if (program == null) {
            return;
        }
        self().generateWrittenMipmaps(program.directives(), flippedAttachments);
    }

    protected void runShadowCompArrayProgram(FullscreenArrayProgram program) {
        if (shadowFramebuffer == null) {
            return;
        }

        List<Attachment> drawBuffers = program.drawBuffers();
        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        shadowFramebuffer.bindForProgramWrite(drawBuffers.toArray(new Attachment[0]));
        self().setupFullscreenState();
        try {
            self().applyViewportScale(program.directives().viewportScale(), shadowFramebuffer.resolution(), shadowFramebuffer.resolution());
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

        self().applyShaderImageTextureBarrier();
        shadowFramebuffer.generateShadowColorMipmaps();
    }

    protected void generateWrittenMipmaps(ShaderProgramDirectives directives, Attachment[] flippedAttachments) {
        if (directives == null || flippedAttachments.length == 0 || directives.mipmappedBuffers().isEmpty()) {
            return;
        }
        EnumSet<Attachment> mipmappedWrittenAttachments = EnumSet.noneOf(Attachment.class);
        for (Attachment attachment : flippedAttachments) {
            if (directives.mipmappedBuffers().contains(attachment)) {
                mipmappedWrittenAttachments.add(attachment);
            }
        }
        if (!mipmappedWrittenAttachments.isEmpty()) {
            DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
            if (readBuffer != null) {
                readBuffer.generateMipmaps(mipmappedWrittenAttachments);
            }
            TextureBinder.restoreDefaultTextureUnit();
        }
    }

    protected void setupFullscreenState() {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL13.glActiveTexture(previousActiveTexture);

        MinecraftReflectionCompat.glStateDisableDepth();
        MinecraftReflectionCompat.glStateDepthMask(false);
        MinecraftReflectionCompat.glStateDisableBlend();
        MinecraftReflectionCompat.glStateDisableAlpha();
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glColorMask(true, true, true, true);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, 1.0, 0.0, 1.0, 0.0, 1.0);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
    }

    protected void restoreFullscreenState() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL13.glActiveTexture(previousActiveTexture);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        MinecraftReflectionCompat.glStateDepthMask(true);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateEnableAlpha();
    }

    public void cleanup() {
        self().cleanupRuntimeState(true, true);
    }

    protected void cleanupRuntimeState(boolean deleteActiveCompiledPrograms, boolean deleteCachedCompiledPrograms) {
        self().cleanupRuntimeState(deleteActiveCompiledPrograms, deleteCachedCompiledPrograms, true);
    }

    protected void cleanupRuntimeState(boolean deleteActiveCompiledPrograms, boolean deleteCachedCompiledPrograms, boolean deleteVanillaTerrainRenderers) {
        self().resetPipelineState();
        MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), 0);

        pingPongManager.cleanup();
        if (shadowFramebuffer != null) {
            shadowFramebuffer.delete();
            shadowFramebuffer = null;
        }
        shadowMapPopulated = false;
        shadowMapUsable = false;
        shadowMapSparseForSampling = false;
        shadowMapCoverageStableFrames = 0;
        shadowMapCoverageRegressionLogs = 0;
        nothiriumShadowInvalidFrames = 0;
        nothiriumShadowSuppressedFrames = 0;
        resetShadowRenderCache();
        deleteCenterDepthSmoothTexture();
        deleteNoiseTexture();
        bloomRenderer.delete();
        customTextures.delete();
        if (deleteVanillaTerrainRenderers) {
            deleteCachedVanillaTerrainRenderers();
            vanillaViewFrustumStateStack.clear();
        }
        shaderImages.delete();
        shaderImages = ShaderImageSet.empty();
        shaderStorageBuffers.delete();
        shaderStorageBuffers = ShaderStorageBufferSet.empty();
        if (deleteActiveCompiledPrograms) {
            deleteComputePrograms();
            deleteFullscreenArrayPrograms();
            for (PipelineProgram program : programs.values()) {
                program.delete();
            }
        }
        if (deleteCachedCompiledPrograms) {
            deleteCachedCompiledPipelines();
        }
        programs.clear();
        programSet = null;
        shaderMap = null;
        shaderProperties = PipelineContext.emptyShaderProperties();
        ShaderBlockLayerOverrides.clear();
        ShaderSamplerState.setBreaksAnisotropy(false);
        fullscreenProgramArrays.clear();
        fullscreenArrayPrograms.clear();
        computeProgramArrays.clear();
        shadowComputePrograms = List.of();
        finalComputePrograms = List.of();
        setupComputePending = false;
        syntheticLightCandidates.clear();
        packDirectives = PipelineContext.emptyShaderProperties().packDirectives();
        isPipelineActive = false;
        activePackName = "(internal)";
        activePass = null;
        activeShaderKey = null;
        activeCompiledPipelineCacheKey = null;
        activePhase = WorldRenderingPhase.NONE;
        overridePhase = null;
        worldFrameActive = false;
        self().clearDirectRecoveredWindowSource();
        self().deleteDirectPresentationSnapshot();
        currentEntityId = 0;
        currentEntityKey = null;
        currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        currentAlphaTestReference = 0.1f;
        centerDepthHalfLife = 1.0f;
        centerDepth = 1.0f;
        centerDepthSmooth = 1.0f;
        pipelineFrameId = 0L;
        nextWorldPassSerial = 0L;
        currentWorldPassSerial = Long.MIN_VALUE;
        worldPassSerialStack.clear();
        nothiriumPipelineTranslucentFrameStack.clear();
        nothiriumPipelineTranslucentWorldPassSerialStack.clear();
        clearNothiriumPipelineTranslucentBridge();
        nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
        resetChunkFadeState(false);
        frameTimeCounter = 0.0f;
        currentFrameTime = 0.016f;
        frameTimeSmooth = 0.016f;
        frameTimeSmoothInitialized = false;
        resetEndFlashState();
        cameraShiftX = 0.0;
        cameraShiftZ = 0.0;
        temporalHistoryInitialized = false;
        temporalHistoryDimensionId = Integer.MIN_VALUE;
        previousTemporalYaw = 0.0f;
        previousTemporalPitch = 0.0f;
        accumulatedTemporalYaw = 0.0f;
        accumulatedTemporalPitch = 0.0f;
        mainViewSwapTemporalResetDimensionId = Integer.MIN_VALUE;
        temporalHistoryResetReason = "";
        temporalHistoryResetVelocity = 0.0f;
        temporalHistoryResetYaw = 0.0f;
        temporalHistoryResetPitch = 0.0f;
        pendingPersistentHistoryClear = false;
        pendingPersistentHistoryClearReason = "";
        terrainLayerCountFrame = Long.MIN_VALUE;
        terrainOpaqueLayerCount = 0;
        terrainOpaqueDrawCount = 0;
        sparseStartupPresentationHoldFrames = 0;
        sparseOpaqueTerrainFrames = 0;
        shaderedNothiriumGlobalBypass = false;
        shaderedNothiriumGlobalBypassReason = "";
        shaderedNothiriumGlobalBypassPrimedWorld = null;
        shaderedNothiriumGlobalBypassPrimedRenderGlobal = null;
        positiveVanillaTerrainProbeLogs = 0;
        positiveNothiriumTerrainProbeLogs = 0;
        terrainGridProbeLogs = 0;
        nothiriumSparseMainRepairFrame = Long.MIN_VALUE;
        nothiriumSparseMainRepairLogs = 0;
        nothiriumSparseMainProviderDrawUntilFrame = Long.MIN_VALUE;
        nothiriumSparseMainProviderDrawLogs = 0;
        nothiriumMainVanillaDrawPathFrames = 0;
        nothiriumMainVanillaDrawPathReason = "";
        nothiriumHybridVanillaMaintenanceFrames = 0;
        nothiriumHybridVanillaMaintenanceReason = "";
        hardwareSafeVanillaTerrainRefreshCooldown = 0;
        lastHardwareSafeVanillaTerrainRefreshWorld = null;
        lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
        for (int i = 0; i < 3; i++) {
            cameraPosition[i] = 0.0f;
            previousCameraPosition[i] = 0.0f;
            cameraPositionUnshifted[i] = 0.0;
            previousCameraPositionUnshifted[i] = 0.0;
        }
        eyeBrightnessHalfLife = 3.0f;
        wetnessHalfLife = 600.0f;
        drynessHalfLife = 200.0f;
        eyeBrightnessSmooth[0] = 0.0f;
        eyeBrightnessSmooth[1] = 0.0f;
        eyeBrightnessSmoothInitialized = false;
        wetnessSmooth = 0.0f;
        wetnessSmoothInitialized = false;
        passStack.clear();
        worldPassBypassStack.clear();
        clientRenderFrameNanos = Long.MIN_VALUE;
        shaderlessCustomSkyBackingThisFrame = false;
        bloomLayerRenderedThisWorldFrame = false;
        shaderlessStyleBloomRenderedThisWorldFrame = false;
        shaderlessBloomRenderedThisWorldFrame = false;
        shaderlessWorldPassActive = false;
        shaderlessStyleBloomRenderedThisWorldPass = false;
        shaderlessBloomRenderedThisWorldPass = false;
        lastTerrainTransitionWorld = null;
        lastTerrainTransitionDimension = Integer.MIN_VALUE;
        lastTerrainTransitionMillis = 0L;
        lastBetterPortalsPortalBlockRefreshWorld = null;
        lastBetterPortalsPortalBlockRefreshPos = null;
        lastBetterPortalsPortalBlockRefreshDimension = Integer.MIN_VALUE;
        lastBetterPortalsPortalBlockRefreshMillis = 0L;
        scheduleInactiveVanillaRecoveryFrame();
    }

    public boolean isActive() {
        return isPipelineActive;
    }

    public boolean shouldSuppressSuffocationOverlay() {
        return isPipelineActive || shaderlessWorldPassActive;
    }

    public boolean shouldForceVanillaTerrainRenderer() {
        return isPipelineActive
                && (!shouldUseNothiriumMainTerrainBridge()
                || (ENABLE_SAFE_TERRAIN_FALLBACKS
                && (hardwareSafeVanillaTerrain
                || softVanillaTerrainRenderer
                // Safe fallbacks remain opt-in for the normal bridge path.
        )));
    }

    public boolean shouldApplyShaderBlockLayerOverrides() {
        return isPipelineActive
                && !(ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain)
                && !shouldSkipAllMainGbufferRendering()
                && hasUsableShaderTerrainProgram();
    }

    public boolean isRenderingBetterPortalsExternalWorldFrame() {
        return BetterPortalsCompat.isInstalled()
                && worldFrameActive
                && externalWorldFramebufferTarget != null;
    }

    public boolean isRenderingBetterPortalsNestedView() {
        return BetterPortalsCompat.isRenderingNestedView();
    }

    public boolean isRenderingBetterPortalsRenderPass() {
        return BetterPortalsCompat.isRenderingRenderPass();
    }

    public boolean shouldRenderBetterPortalsNestedViewWithShaders() {
        return isPipelineActive
                && BetterPortalsCompat.shouldRenderNestedViewWithShaders()
                && BetterPortalsCompat.currentShaderRenderPassFramebuffer() != null;
    }

    public boolean shouldBypassWorldPassRendering() {
        if (!worldPassBypassStack.isEmpty()) {
            return worldPassBypassStack.peek();
        }
        return self().computeShouldBypassWorldPassRendering();
    }
}
