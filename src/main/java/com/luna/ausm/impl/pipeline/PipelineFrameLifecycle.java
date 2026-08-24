package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.client.ThaumcraftParticleBridge;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelineGlState.disablePipelineVertexAttributes;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_TEMPORAL_HISTORY_RESET_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_TERRAIN_HISTORY_CLEAR_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.TEMPORAL_HISTORY_ACCUMULATED_PITCH_RESET;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.TEMPORAL_HISTORY_ACCUMULATED_YAW_RESET;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.PARTICLE_DIMENSION_RECOVERY_FRAMES;

abstract class PipelineFrameLifecycle extends PipelineWorldRenderScopeBase {
    public void beginFrame() {
        if (!isPipelineActive) {
            externalWorldFramebufferTarget = null;
            return;
        }
        currentWorldFrameStartNanos = System.nanoTime();
        currentWorldFrameReadyNanos = Long.MIN_VALUE;
        currentWorldFrameFinishStartNanos = Long.MIN_VALUE;
        currentWorldFrameAfterNativeBloomNanos = Long.MIN_VALUE;
        currentWorldFrameBlitStartNanos = Long.MIN_VALUE;

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            externalWorldFramebufferTarget = null;
            return;
        }
        worldFrameActive = true;
        forensicTrace("frame-begin", "target-pending");
        externalWorldFramebufferTarget = BetterPortalsCompat.currentShaderRenderPassFramebuffer();
        boolean betterPortalsExternalTarget = isBetterPortalsExternalWorldTarget();
        int targetWidth = worldTargetWidth(mc);
        int targetHeight = worldTargetHeight(mc);
        self().observePresentationBeforeWorldRendering(mc);
        self().clearWorldLoadPresentationFramebuffer(mc);
        self().logBetterPortalsPipeline("begin-frame:target", "target=" + targetWidth + "x" + targetHeight
                + ", external=" + betterPortalsExternalTarget);
        if (pingPongManager.width() != targetWidth || pingPongManager.height() != targetHeight) {
            self().logBetterPortalsPipeline("begin-frame:resize", "old=" + pingPongManager.width() + "x" + pingPongManager.height()
                    + ", new=" + targetWidth + "x" + targetHeight);
            self().resizeFramebuffer(targetWidth, targetHeight, true);
        }

        if (betterPortalsExternalTarget) {
            currentFrameTime = 0.0f;
        } else {
            long now = System.nanoTime();
            currentFrameTime = Math.clamp((now - lastPipelineFrameNanos) / 1_000_000_000.0f, 0.001f, 1.0f);
            lastPipelineFrameNanos = now;
            pipelineFrameId++;
            frameTimeCounter += currentFrameTime;
            if (frameTimeCounter >= 3600.0f) {
                frameTimeCounter = 0.0f;
            }
        }
        deferredPassesRenderedThisFrame = false;
        preparePassesRenderedBeforeShadowThisFrame = false;
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        self().clearDirectRecoveredWindowSource();
        if (nothiriumShadowSuppressedFrames > 0) {
            nothiriumShadowSuppressedFrames--;
        }
        if (invalidShadowTerrainSuppressedFrames > 0) {
            invalidShadowTerrainSuppressedFrames--;
        }
        clearShaderedNothiriumGlobalBypassState(false);
        self().updateCameraPosition(mc);
        logHeldColoredLightProbe(mc);
        self().refreshHardwareSafeVanillaTerrainForCamera(mc);
        boolean resetTemporalHistory = self().shouldResetTemporalHistory(mc, betterPortalsExternalTarget);
        if (betterPortalsExternalTarget) {
            System.arraycopy(cameraPosition, 0, previousCameraPosition, 0, 3);
            System.arraycopy(cameraPositionUnshifted, 0, previousCameraPositionUnshifted, 0, 3);
        } else {
            updateSmoothedFrameTime();
            updateSmoothedEyeBrightness(mc);
            updateSmoothedWetness(mc);
            updateEndFlashState(mc);
        }
        pingPongManager.beginFrameWithInitialTarget(fallbackColorAttachment(), self().frameClearAttachments(resetTemporalHistory));
        self().logTemporalHistoryResetIfNeeded(resetTemporalHistory);
        self().runSetupComputesIfNeeded();
        self().runFullscreenPasses(ProgramArrayId.BEGIN);
        PipelineFrameLayerCapture.capturePresentationBoundary(
                pipelineFrameId,
                "90-window-before-world-pipeline",
                MinecraftReflectionCompat.minecraftFramebuffer(mc)
        );
        self().bindWorldFramebuffer();
        PipelineFrameLayerCapture.captureDeferredBoundary(
                pipelineFrameId,
                "91-deferred-initial-before-gbuffer",
                pingPongManager.getReadBuffer()
        );
        currentWorldFrameReadyNanos = System.nanoTime();
        self().logBetterPortalsPipeline("begin-frame:ready");
    }

    public void beginClientRenderFrame(long frameNanos) {
        boolean newFrame = frameNanos != clientRenderFrameNanos;
        if (newFrame) {
            clientRenderFrameNanos = frameNanos;
            if (!isPipelineActive) {
                pipelineFrameId++;
            }
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            shaderlessCustomSkyBackingThisFrame = self().shouldRenderShaderlessCustomSkyBackingNow(mc);
            bloomLayerRenderedThisWorldFrame = false;
            shaderlessStyleBloomRenderedThisWorldFrame = false;
            shaderlessBloomRenderedThisWorldFrame = false;
            self().resetShaderlessTerrainLayerCounts();
            if (vanillaParticleRecoveryFrames > 0) {
                vanillaParticleRecoveryFrames--;
            }
            if (nothiriumHybridVanillaMaintenanceFrames > 0) {
                nothiriumHybridVanillaMaintenanceFrames--;
                if (nothiriumHybridVanillaMaintenanceFrames == 0) {
                    nothiriumHybridVanillaMaintenanceReason = "";
                }
            }
            if (nothiriumMainVanillaDrawPathFrames > 0) {
                nothiriumMainVanillaDrawPathFrames--;
                if (nothiriumMainVanillaDrawPathFrames == 0) {
                    nothiriumMainVanillaDrawPathReason = "";
                }
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
        int targetFramebuffer = MinecraftReflectionCompat.framebufferObject(target);
        try {
            MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(targetFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
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

    public void beginWorldPassRendering(int pass, float partialTicks) {
        if (clientRenderFrameNanos == Long.MIN_VALUE) {
            self().beginClientRenderFrame(System.nanoTime());
        }
        refreshShaderlessVoidWorldSkyLightEligibility();
        self().beginWorldPassDuplicateTracking();
        currentWorldPass = pass;
        currentWorldPartialTicks = partialTicks;
        bloomLayerRenderedThisWorldPass = bloomLayerRenderedThisWorldFrame;
        shaderlessStyleBloomRenderedThisWorldPass = shaderlessStyleBloomRenderedThisWorldFrame;
        shaderlessBloomRenderedThisWorldPass = shaderlessBloomRenderedThisWorldFrame;
        boolean bypass = self().computeShouldBypassWorldPassRendering();
        worldPassBypassStack.push(bypass);
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:world-pass-begin bypass=" + bypass);
        self().logBetterPortalsPipeline("world-pass-begin", "pass=" + pass + ", bypass=" + bypass);
        if (bypass) {
            self().prepareBypassedWorldPassRendering();
            return;
        }

        if (!isPipelineActive) {
            self().beginShaderlessWorldPassRendering();
            return;
        }

        self().beginFrame();
    }

    public void finishWorldPassRendering() {
        boolean bypass = worldPassBypassStack.isEmpty()
                ? self().computeShouldBypassWorldPassRendering()
                : worldPassBypassStack.pop();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:world-pass-finish bypass=" + bypass);
        self().logBetterPortalsPipeline("world-pass-finish", "bypass=" + bypass);
        try {
            if (bypass) {
                self().finishBypassedWorldPassRendering();
                return;
            }

            if (!isPipelineActive) {
                self().finishShaderlessWorldPassRendering();
                return;
            }

            currentWorldFrameFinishStartNanos = System.nanoTime();
            self().renderNativeBloomLayerIfNeeded();
            currentWorldFrameAfterNativeBloomNanos = System.nanoTime();
            self().blitWorldFramebufferToMinecraft();
        } finally {
            self().finishWorldPassDuplicateTracking();
        }
    }

    protected void beginShaderlessWorldPassRendering() {
        self().prepareInactiveVanillaFrame();
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        self().clearWorldLoadPresentationFramebuffer(mc);
        shaderlessWorldPassActive = true;
        self().restoreVanillaWorldPassState(true, true);
    }

    protected void finishShaderlessWorldPassRendering() {
        // Shaderless frames have no deferred-frame finish path. Render the
        // resource-pack BLOOM VBO before restoring vanilla world state.
        self().renderNativeBloomLayerIfNeeded();
        self().sealShaderlessWorldFramebufferAlpha("shaderless-world-pass-finish");
        self().restoreVanillaWorldPassState(false, true);
        shaderlessWorldPassActive = false;
        if (worldLoadPresentationGuardFrames > 0) {
            worldLoadPresentationGuardFrames--;
        }
    }

    protected void sealShaderlessWorldFramebufferAlpha(String stage) {
    }

    protected void updateCameraPosition(Minecraft mc) {
        System.arraycopy(cameraPosition, 0, previousCameraPosition, 0, 3);
        System.arraycopy(cameraPositionUnshifted, 0, previousCameraPositionUnshifted, 0, 3);

        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity == null) {
            cameraPosition[0] = 0.0f;
            cameraPosition[1] = 0.0f;
            cameraPosition[2] = 0.0f;
            cameraPositionUnshifted[0] = 0.0;
            cameraPositionUnshifted[1] = 0.0;
            cameraPositionUnshifted[2] = 0.0;
            return;
        }

        float partialTicks = MinecraftReflectionCompat.renderPartialTicks(mc);
        Vec3d eyePosition = MinecraftReflectionCompat.positionEyes(viewEntity, partialTicks);
        double x = MinecraftReflectionCompat.vecX(eyePosition);
        double y = MinecraftReflectionCompat.vecY(eyePosition);
        double z = MinecraftReflectionCompat.vecZ(eyePosition);
        cameraPositionUnshifted[0] = x;
        cameraPositionUnshifted[1] = y;
        cameraPositionUnshifted[2] = z;
        self().updateCameraOffset(viewEntity, x, y, z);

        cameraPosition[0] = (float) (x + cameraShiftX);
        cameraPosition[1] = (float) y;
        cameraPosition[2] = (float) (z + cameraShiftZ);
    }

    protected void updateCameraOffset(Entity viewEntity, double x, double y, double z) {
        double adjustedX = x + cameraShiftX;
        double adjustedZ = z + cameraShiftZ;
        double adx = Math.abs(adjustedX - previousCameraPosition[0]);
        double adz = Math.abs(adjustedZ - previousCameraPosition[2]);
        double apx = Math.abs(adjustedX);
        double apz = Math.abs(adjustedZ);
        double shiftX = PipelineWorldRenderScope.irisCameraShift(adjustedX, adx, apx);
        double shiftZ = PipelineWorldRenderScope.irisCameraShift(adjustedZ, adz, apz);
        if (shiftX != 0.0 || shiftZ != 0.0) {
            cameraShiftX += shiftX;
            cameraShiftZ += shiftZ;
            previousCameraPosition[0] += (float) shiftX;
            previousCameraPosition[2] += (float) shiftZ;
        }
        if (Math.abs(MinecraftReflectionCompat.posX(viewEntity) - x) > 1000.0 || Math.abs(MinecraftReflectionCompat.posZ(viewEntity) - z) > 1000.0) {
            previousCameraPosition[0] = (float) (x + cameraShiftX);
            previousCameraPosition[1] = (float) y;
            previousCameraPosition[2] = (float) (z + cameraShiftZ);
        }
    }

    public void prepareInactiveVanillaFrame() {
        if (isPipelineActive || vanillaRecoveryFrames <= 0) {
            return;
        }
        vanillaRecoveryFrames--;
        self().resetPipelineState();
    }

    protected void scheduleInactiveVanillaRecoveryFrame() {
        if (!isPipelineActive) {
            vanillaRecoveryFrames = Math.max(vanillaRecoveryFrames, 1);
        }
    }

    protected void restoreVanillaWorldPassState(boolean bindMinecraftFramebuffer, boolean resetPortalMasks) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (bindMinecraftFramebuffer && mc != null && MinecraftReflectionCompat.minecraftFramebuffer(mc) != null) {
            bindMinecraftFramebufferForGui(mc);
        }

        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();

        // Keep GlStateManager's cache and the driver synchronized at the GUI ->
        // world boundary. GUI teardown intentionally leaves cached depth writes
        // disabled. Repairing only the raw GL mask makes a following sky
        // renderer's depthMask(false) look redundant to GlStateManager, so the
        // sky writes depth and rejects terrain rendered later in the frame.
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        GL11.glColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

        if (resetPortalMasks) {
            PipelineWorldRenderScope.resetPortalMaskState();
        }

        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        self().restoreVanillaFixedFunctionTextureState(mc);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableCull();
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableColorMaterial();
        MinecraftReflectionCompat.glStateDisableBlend();
    }

    public void prepareVanillaParticleRendering() {
        if (isPipelineActive && !shouldBypassWorldPassRendering()) {
            return;
        }
        self().prepareVanillaParticleRenderingState();
    }

    public boolean shouldRenderParticlesWithVanillaState() {
        return vanillaParticleRecoveryFrames > 0;
    }

    public void clearClientParticles(String reason) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.field(mc, Object.class, null, "field_71452_i", "effectRenderer") == null) {
            return;
        }
        MinecraftReflectionCompat.invoke(MinecraftReflectionCompat.field(mc, Object.class, null, "field_71452_i", "effectRenderer"), new String[]{"func_78870_a", "clearEffects"}, new Class<?>[]{World.class}, MinecraftReflectionCompat.world(mc));
        ThaumcraftParticleBridge.clearParticles(reason);
        vanillaParticleRecoveryFrames = 0;
        self().logTerrainDiagnostic("particles:clear", MinecraftReflectionCompat.world(mc), "reason=" + reason);
    }

    public void prepareVanillaParticleRenderingState() {
        self().restoreVanillaWorldPassState(false, true);
        // ParticleManager reuses the global Tessellator. Never allow terrain
        // compilation/replay metadata to reach BufferBuilder's particle
        // position/UV/color/lightmap format on the client render thread.
        BlockRenderContext.clear();
        FixedFunctionGlState.resetClientArrayState(false);
        FixedFunctionGlState.resetVanillaTextureMatrices();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    protected void startVanillaParticleRecovery() {
        vanillaParticleRecoveryFrames = Math.max(vanillaParticleRecoveryFrames, PARTICLE_DIMENSION_RECOVERY_FRAMES);
    }

    protected void restoreVanillaFixedFunctionTextureState(Minecraft mc) {
        if (mc != null && MinecraftReflectionCompat.entityRenderer(mc) != null) {
            MinecraftReflectionCompat.enableLightmap(MinecraftReflectionCompat.entityRenderer(mc));
        } else {
            TextureBinder.restoreDefaultTextureUnit();
        }
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        MinecraftReflectionCompat.glStateEnableTexture2D();
        bindBlockAtlas();
        TextureBinder.disableShaderOnlyFixedFunctionTextureUnits();
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
    }

    protected static void restoreShaderlessTerrainClientTextureArrays() {
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(previousClientTexture);
    }

    protected static double irisCameraShift(double adjusted, double delta, double absoluteAdjusted) {
        return PipelineFrameValues.irisCameraShift(adjusted, delta, absoluteAdjusted);
    }

    protected void resizeFramebuffer(int width, int height, boolean preservePersistentAttachments) {
        if (width <= 0 || height <= 0) {
            return;
        }

        self().clearCompositeInvalidFallbackSnapshot();
        if (preservePersistentAttachments) {
            pingPongManager.resize(width, height, packDirectives.renderTargets().clearDisabled());
        } else {
            pingPongManager.resize(width, height);
        }
        shaderImages.resize(width, height);
        shaderStorageBuffers.resize(width, height);
        setupComputePending = true;
    }

    protected Attachment[] frameClearAttachments(boolean forcePersistentClear) {
        Set<Attachment> clearDisabled = packDirectives.renderTargets().clearDisabled();
        List<Attachment> attachments = new ArrayList<>();
        for (Attachment attachment : Attachment.values()) {
            if (forcePersistentClear || !clearDisabled.contains(attachment)) {
                attachments.add(attachment);
            }
        }
        return attachments.toArray(new Attachment[0]);
    }

    protected boolean shouldResetTemporalHistory(Minecraft mc, boolean betterPortalsExternalTarget) {
        temporalHistoryResetReason = "";
        temporalHistoryResetVelocity = 0.0f;
        temporalHistoryResetYaw = 0.0f;
        temporalHistoryResetPitch = 0.0f;
        if (betterPortalsExternalTarget || mc == null || MinecraftReflectionCompat.world(mc) == null || !pingPongManager.isInitialized()) {
            return false;
        }

        World world = renderWorld(mc);
        int dimensionId = safeDimensionId(world);
        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity == null) {
            self().resetTemporalHistoryTracking(dimensionId);
            temporalHistoryResetReason = "missing-view-entity";
            return true;
        }

        float yaw = PipelineWorldRenderScope.interpolateAngle(MinecraftReflectionCompat.prevRotationYaw(viewEntity), MinecraftReflectionCompat.rotationYaw(viewEntity), currentWorldPartialTicks);
        float pitch = MinecraftReflectionCompat.prevRotationPitch(viewEntity) + (MinecraftReflectionCompat.rotationPitch(viewEntity) - MinecraftReflectionCompat.prevRotationPitch(viewEntity)) * currentWorldPartialTicks;
        float velocity = self().cameraVelocityMagnitude();

        if (!temporalHistoryInitialized) {
            self().resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            temporalHistoryResetReason = "initial";
            return true;
        }

        float yawDelta = Math.abs(PipelineWorldRenderScope.wrapDegrees(yaw - previousTemporalYaw));
        float pitchDelta = Math.abs(pitch - previousTemporalPitch);
        accumulatedTemporalYaw += yawDelta;
        accumulatedTemporalPitch += pitchDelta;

        previousTemporalYaw = yaw;
        previousTemporalPitch = pitch;
        temporalHistoryResetVelocity = velocity;
        temporalHistoryResetYaw = accumulatedTemporalYaw;
        temporalHistoryResetPitch = accumulatedTemporalPitch;

        if (dimensionId != temporalHistoryDimensionId) {
            self().resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            self().clearCompositeInvalidFallbackSnapshot();
            temporalHistoryResetReason = "dimension";
            return true;
        }
        int recoveryDimensionId = BetterPortalsCompat.mainViewSwapRecoveryDimensionId();
        if (recoveryDimensionId != Integer.MIN_VALUE
                && recoveryDimensionId != mainViewSwapTemporalResetDimensionId) {
            mainViewSwapTemporalResetDimensionId = recoveryDimensionId;
            self().resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            temporalHistoryResetReason = "betterportals-main-view-recovery";
            return true;
        }
        if (recoveryDimensionId == Integer.MIN_VALUE) {
            mainViewSwapTemporalResetDimensionId = Integer.MIN_VALUE;
        }
        if (accumulatedTemporalYaw > TEMPORAL_HISTORY_ACCUMULATED_YAW_RESET
                || accumulatedTemporalPitch > TEMPORAL_HISTORY_ACCUMULATED_PITCH_RESET) {
            // Normal mouse-look should not clear persistent shader history.
            // Clearing temporal/deferred attachments during rotation looks like
            // distant terrain/chunk flicker on packs that keep persistent history.
            accumulatedTemporalYaw = 0.0f;
            accumulatedTemporalPitch = 0.0f;
        }
        return false;
    }

    protected void resetTemporalHistoryTracking(int dimensionId) {
        self().resetTemporalHistoryTracking(dimensionId, 0.0f, 0.0f);
    }

    protected void resetTemporalHistoryTracking(int dimensionId, float yaw, float pitch) {
        temporalHistoryInitialized = true;
        temporalHistoryDimensionId = dimensionId;
        previousTemporalYaw = yaw;
        previousTemporalPitch = pitch;
        accumulatedTemporalYaw = 0.0f;
        accumulatedTemporalPitch = 0.0f;
    }

    protected float cameraVelocityMagnitude() {
        float x = cameraPosition[0] - previousCameraPosition[0];
        float y = cameraPosition[1] - previousCameraPosition[1];
        float z = cameraPosition[2] - previousCameraPosition[2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    protected float cameraVerticalDelta() {
        return cameraPosition[1] - previousCameraPosition[1];
    }

    protected float cameraHorizontalVelocityMagnitude() {
        float x = cameraPosition[0] - previousCameraPosition[0];
        float z = cameraPosition[2] - previousCameraPosition[2];
        return (float) Math.sqrt(x * x + z * z);
    }

    protected static float interpolateAngle(float previous, float current, float partialTicks) {
        return PipelineTemporalMath.interpolateAngle(previous, current, partialTicks);
    }

    protected static float wrapDegrees(float value) {
        return PipelineTemporalMath.wrapDegrees(value);
    }

    protected void logTemporalHistoryResetIfNeeded(boolean resetTemporalHistory) {
        if (!resetTemporalHistory || temporalHistoryResetLogs >= MAX_TEMPORAL_HISTORY_RESET_LOGS) {
            return;
        }
        temporalHistoryResetLogs++;
        MainMod.LOGGER.info("[Pipeline] Reset temporal history: reason={} dimension={} velocity={} accumulatedYaw={} accumulatedPitch={} persistentAttachments={}",
                temporalHistoryResetReason,
                temporalHistoryDimensionId,
                temporalHistoryResetVelocity,
                temporalHistoryResetYaw,
                temporalHistoryResetPitch,
                packDirectives.renderTargets().clearDisabled());
    }

    protected void requestPersistentHistoryClear(String reason) {
        if (packDirectives.renderTargets().clearDisabled().isEmpty()) {
            return;
        }
        pendingPersistentHistoryClear = true;
        pendingPersistentHistoryClearReason = reason == null || reason.isBlank() ? "unspecified" : reason;
    }

    protected void clearPendingPersistentHistoryIfNeeded() {
        if (!pendingPersistentHistoryClear || !pingPongManager.isInitialized()) {
            return;
        }

        Attachment[] attachments = self().persistentHistoryAttachments();
        pendingPersistentHistoryClear = false;
        String reason = pendingPersistentHistoryClearReason;
        pendingPersistentHistoryClearReason = "";
        if (attachments.length == 0) {
            return;
        }

        pingPongManager.clear(attachments);
        pingPongManager.clearWrite(attachments);
        self().bindWorldFramebuffer();
        if (persistentHistoryClearLogs < MAX_TERRAIN_HISTORY_CLEAR_LOGS) {
            persistentHistoryClearLogs++;
            MainMod.LOGGER.info("[Pipeline] Cleared persistent history before deferred passes: reason={} attachments={}",
                    reason,
                    Arrays.toString(attachments));
        }
    }
}
