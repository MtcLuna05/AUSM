package com.l.ausm.impl.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import static com.l.ausm.impl.pipeline.PipelineGlState.disablePipelineVertexAttributes;
import static com.l.ausm.impl.pipeline.PipelineGlState.resetIndexedBlendState;
import static com.l.ausm.impl.pipeline.PipelineGlState.restoreVanillaClientRenderState;
import static com.l.ausm.impl.pipeline.PipelineGlState.setIndexedBlend;
import static com.l.ausm.impl.pipeline.PipelineGlState.unbindShaderImages;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_GUI_RECOVERED_BACKGROUND_LOGS;

abstract class PipelineDeferredPassOrchestration0 extends PipelineChunkUpdateTracking {
    public void prepareFramebufferPresentation() {
        if (!isPipelineActive) {
            if (externalWorldFramebufferTarget == null && !self().isRenderingBetterPortalsNestedView()) {
                Minecraft mc = MinecraftReflectionCompat.minecraft();
                if (mc != null && MinecraftReflectionCompat.world(mc) != null && MinecraftReflectionCompat.renderViewEntity(mc) != null) {
                    MinecraftReflectionCompat.glUseProgram(0);
                    TextureBinder.restoreDefaultTextureUnit();
                    MinecraftReflectionCompat.glStateBindTexture(0);
                    MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
                    MinecraftReflectionCompat.glStateEnableTexture2D();
                    MinecraftReflectionCompat.glStateEnableAlpha();
                    MinecraftReflectionCompat.glStateEnableBlend();
                    MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                            GL11.GL_SRC_ALPHA,
                            GL11.GL_ONE_MINUS_SRC_ALPHA,
                            GL11.GL_ONE,
                            GL11.GL_ZERO
                    );
                    MinecraftReflectionCompat.glStateEnableDepth();
                    GL11.glDepthMask(true);
                    GL11.glDepthFunc(GL11.GL_LEQUAL);
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                    MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
                }
            }
            return;
        }

        if (externalWorldFramebufferTarget != null || self().isRenderingBetterPortalsNestedView()) {
            return;
        }

        if (worldFrameActive) {
            self().renderDeferredNativeBloomIfNeeded();
            self().blitWorldFramebufferToMinecraft();
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc != null && MinecraftReflectionCompat.entityRenderer(mc) != null) {
            MinecraftReflectionCompat.disableLightmap(MinecraftReflectionCompat.entityRenderer(mc));
        }
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.glStateBindTexture(0);
        disablePipelineVertexAttributes();
        unbindShaderImages();
        self().unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
    }

    public void prepareGuiRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || self().isRenderingBetterPortalsNestedView()) {
            return;
        }

        renderingGui = true;
        self().bindGuiTarget();
        self().prepareGuiState();
    }

    public void prepareGuiFramebuffer() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || self().isRenderingBetterPortalsNestedView()) {
            return;
        }

        self().bindGuiTarget();
        self().prepareGuiState();
    }

    public void prepareGuiWorldBackgroundFromRecoveredSource() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || self().isRenderingBetterPortalsNestedView()) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        Framebuffer target = mc != null ? MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
        if (target == null || MinecraftReflectionCompat.currentScreen(mc) == null) {
            return;
        }
        boolean refreshed = self().refreshMinecraftFramebufferFromDirectPresentationTexture(target, true)
                || self().refreshMinecraftFramebufferFromDirectRecoveredWindowSource(target);
        self().logGuiRecoveredBackground(refreshed, target);
        self().bindGuiTarget();
        self().prepareGuiState();
    }

    public void prepareShaderlessGuiScreenRendering() {
        if (isPipelineActive) {
            return;
        }
        self().prepareDirectGuiScreenRenderingState(false);
    }

    public void prepareBypassedGuiScreenRendering() {
        self().prepareDirectGuiScreenRenderingState(isPipelineActive);
        self().prepareVanillaGuiScreenOverlayState();
    }

    public void prepareBypassedGuiScreenDrawState() {
        self().prepareDirectGuiScreenRenderingState(false);
    }

    /**
     * Vanilla GuiScreen backgrounds and widgets are drawn over the already-presented
     * world.  The HUD is submitted first and its 3-D item models leave nearer depth
     * behind than a container's flat background.  Start the screen in a fresh GUI
     * depth domain so those old hotbar fragments cannot reject the later container,
     * while preserving the hotbar colour underneath for screens that do not cover it.
     * Keep depth testing and writes enabled after the clear: vanilla and modded GUI
     * renderers use their zLevel values to order overlapping controls and item layers.
     */
    protected void prepareVanillaGuiScreenOverlayState() {
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
    }

    protected void prepareDirectGuiScreenRenderingState(boolean flushPipelineWorld) {
        if (flushPipelineWorld && externalWorldFramebufferTarget == null && !self().isRenderingBetterPortalsNestedView()) {
            if (worldFrameActive) {
                self().renderDeferredNativeBloomIfNeeded();
                self().blitWorldFramebufferToMinecraft();
            }
        }
        renderingGui = false;
        guiRenderDepth = 0;
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderImages();
        self().unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc != null && MinecraftReflectionCompat.minecraftFramebuffer(mc) != null) {
            Framebuffer framebuffer = MinecraftReflectionCompat.minecraftFramebuffer(mc);
            MinecraftReflectionCompat.bindFramebuffer(framebuffer, false);
            restoreDrawBufferForFramebuffer(MinecraftReflectionCompat.framebufferObject(framebuffer), GL30.GL_COLOR_ATTACHMENT0);
            restoreReadBufferForFramebuffer(MinecraftReflectionCompat.framebufferObject(framebuffer), GL30.GL_COLOR_ATTACHMENT0);
            MinecraftReflectionCompat.glStateViewport(0, 0, MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc));
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
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
    }

    public void beginGuiRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || self().isRenderingBetterPortalsNestedView()) {
            return;
        }

        boolean outermostGui = guiRenderDepth == 0;
        guiRenderDepth++;
        if (outermostGui) {
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            Framebuffer target = mc != null ? MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
            if (target != null) {
                self().refreshMinecraftFramebufferFromDirectPresentationTexture(target, true);
            }
        }
        self().prepareGuiRendering();
    }

    public void beginGuiScreenRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || self().isRenderingBetterPortalsNestedView()) {
            return;
        }

        boolean preserveCompletedGui = guiRenderDepth == 0 && guiTargetContentFrame == pipelineFrameId;
        guiRenderDepth++;
        renderingGui = true;
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        Framebuffer target = mc != null ? MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
        if (target != null && !preserveCompletedGui) {
            self().refreshMinecraftFramebufferFromDirectPresentationTexture(target, true);
        }
        self().bindGuiTarget();
        self().prepareGuiState();
    }

    public void finishGuiScreenRendering() {
        self().finishGuiRendering();
    }

    public void finishGuiRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || self().isRenderingBetterPortalsNestedView()) {
            return;
        }
        boolean completedGui = guiRenderDepth > 0;
        if (completedGui) {
            guiRenderDepth--;
        }
        if (guiRenderDepth == 0) {
            if (completedGui) {
                guiTargetContentFrame = pipelineFrameId;
            }
            renderingGui = false;
            self().restoreGuiSafeRenderState("gui-finish");
            drainPausedPostRenderGlErrors("gui-finish");
        }
    }

    protected void bindGuiTarget() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return;
        }
        if (MinecraftReflectionCompat.minecraftFramebuffer(mc) != null) {
            bindMinecraftFramebufferForGui(mc);
        }
    }

    protected void prepareGuiState() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc != null && MinecraftReflectionCompat.entityRenderer(mc) != null) {
            MinecraftReflectionCompat.disableLightmap(MinecraftReflectionCompat.entityRenderer(mc));
        }
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.glStateBindTexture(0);
        disablePipelineVertexAttributes();
        unbindShaderImages();
        self().unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateDisableDepth();
        GL11.glDepthMask(false);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableColorMaterial();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        setIndexedBlend(0, true);
    }

    public boolean shouldDirectPresentFramebuffer() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        return (isPipelineActive || shouldUseShaderlessHiddenGuiPresentation())
                && mc != null
                && MinecraftReflectionCompat.world(mc) != null
                && MinecraftReflectionCompat.gameSettings(mc) != null
                && externalWorldFramebufferTarget == null
                && !self().isRenderingBetterPortalsNestedView();
    }

    protected void snapshotPresentationTargetForDirectPresentation(Framebuffer target, String reason) {
        if (target == null) {
            directPresentationValid = false;
            return;
        }
        if (worldLoadPresentationGuardFrames > 0) {
            directPresentationValid = false;
            return;
        }
        int sourceFramebuffer = MinecraftReflectionCompat.framebufferObject(target);
        int width = Math.max(1, MinecraftReflectionCompat.framebufferWidth(target));
        int height = Math.max(1, MinecraftReflectionCompat.framebufferHeight(target));
        if (!self().ensureDirectPresentationTexture(width, height)) {
            directPresentationValid = false;
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, directPresentationFbo);
            GL11.glReadBuffer(sourceFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
            directPresentationValid = true;
            directPresentationFrame = pipelineFrameId;
            directPresentationReason = reason;
        } catch (RuntimeException | LinkageError e) {
            directPresentationValid = false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
        }
    }

    protected boolean ensureDirectPresentationTexture(int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (directPresentationTexture <= 0) {
            directPresentationTexture = GL11.glGenTextures();
        }
        if (directPresentationFbo <= 0) {
            directPresentationFbo = GL30.glGenFramebuffers();
        }
        if (directPresentationTexture <= 0 || directPresentationFbo <= 0) {
            return false;
        }

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, directPresentationTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            if (directPresentationWidth != width || directPresentationHeight != height) {
                GL11.glTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_RGBA8,
                        width,
                        height,
                        0,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        (ByteBuffer) null
                );
                directPresentationWidth = width;
                directPresentationHeight = height;
                directPresentationValid = false;
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, directPresentationFbo);
            GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    directPresentationTexture,
                    0
            );
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        }
    }

    protected boolean refreshMinecraftFramebufferFromDirectPresentationTexture(Framebuffer target, boolean allowStaleForGui) {
        if (target == null
                || !directPresentationValid
                || directPresentationTexture <= 0
                || directPresentationFbo <= 0
                || directPresentationWidth <= 0
                || directPresentationHeight <= 0) {
            return false;
        }
        long age = pipelineFrameId - directPresentationFrame;
        if (directPresentationFrame == Long.MIN_VALUE || age != 0L) {
            return false;
        }

        int targetFramebuffer = MinecraftReflectionCompat.framebufferObject(target);
        int targetWidth = Math.max(1, MinecraftReflectionCompat.framebufferWidth(target));
        int targetHeight = Math.max(1, MinecraftReflectionCompat.framebufferHeight(target));
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousScissor = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, directPresentationFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(targetFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(true, true, true, true);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    directPresentationWidth,
                    directPresentationHeight,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
            MinecraftReflectionCompat.bindFramebuffer(target, false);
            MinecraftReflectionCompat.glStateViewport(0, 0, targetWidth, targetHeight);
            return true;
        } catch (RuntimeException | LinkageError e) {
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            if (previousScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    protected void logGuiRecoveredBackground(boolean refreshed, Framebuffer target) {
        if (guiRecoveredBackgroundLogs++ >= MAX_GUI_RECOVERED_BACKGROUND_LOGS) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        MainMod.LOGGER.info(
                "[AUSMGuiRecoveredBackground] refreshed={} screen={} target={} targetColor={} snapshotValid={} snapshotReason={} snapshotFrame={} currentFrame={} rawSourceFrame={} gl={}",
                refreshed,
                mc != null && MinecraftReflectionCompat.currentScreen(mc) != null
                        ? MinecraftReflectionCompat.currentScreen(mc).getClass().getName()
                        : "none",
                self().describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                directPresentationValid,
                directPresentationReason,
                directPresentationFrame,
                pipelineFrameId,
                directRecoveredWindowFrame,
                PipelineContext.glStateSummary()
        );
    }

    public void presentFramebufferDirectly(Framebuffer target, int width, int height) {
        if ((!isPipelineActive && !shouldUseShaderlessHiddenGuiPresentation())
                || externalWorldFramebufferTarget != null
                || self().isRenderingBetterPortalsNestedView()) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || target == null || target != MinecraftReflectionCompat.minecraftFramebuffer(mc)) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousScissor = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
        FixedFunctionGlState.resetClientArrayState(true);
        MinecraftReflectionCompat.glUseProgram(0);
        MinecraftReflectionCompat.glStateDisableDepth();
        MinecraftReflectionCompat.glStateDepthMask(false);
        MinecraftReflectionCompat.glStateDisableBlend();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, false);
        MinecraftReflectionCompat.glStateViewport(0, 0, width, height);
        boolean screenOpen = MinecraftReflectionCompat.currentScreen(mc) != null;
        GL11.glColorMask(true, true, true, false);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, MinecraftReflectionCompat.framebufferObject(target));
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(GL11.GL_BACK);
        GL30.glBlitFramebuffer(
                0,
                0,
                MinecraftReflectionCompat.framebufferWidth(target),
                MinecraftReflectionCompat.framebufferHeight(target),
                0,
                0,
                width,
                height,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
        );
        self().logDirectF1WindowPresent(target, width, height);
        self().logDirectWindowPresent(target, width, height, false);
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glColorMask(true, true, true, true);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
        if (previousScissor) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        TextureBinder.restoreDefaultTextureUnit();
        FixedFunctionGlState.resetClientArrayState(true);
    }
}
