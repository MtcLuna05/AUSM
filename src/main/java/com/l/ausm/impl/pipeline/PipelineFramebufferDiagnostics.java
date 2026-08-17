package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import static com.l.ausm.impl.pipeline.PipelineGlState.maxDrawBuffers;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_COMPOSITE_CHAIN_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_DEFERRED_BOUNDARY_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_FINAL_COLOR_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_POSITIVE_VANILLA_TERRAIN_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_TERRAIN_COLOR_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_TERRAIN_GRID_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.TERRAIN_GRID_PROBE_COLUMNS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.TERRAIN_GRID_PROBE_ROWS;

abstract class PipelineFrameLifecycle0 extends PipelineDistantHorizonsInterop {
    protected void logColorBufferProbe(String stage, boolean force) {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }
        boolean finalStage = stage != null && stage.contains("final");
        if (!force) {
            if (finalStage) {
                if (finalColorProbeLogs >= MAX_FINAL_COLOR_PROBE_LOGS) {
                    return;
                }
                finalColorProbeLogs++;
            } else {
                if (terrainColorProbeLogs >= MAX_TERRAIN_COLOR_PROBE_LOGS) {
                    return;
                }
                terrainColorProbeLogs++;
            }
        } else if (finalStage) {
            if (finalColorProbeLogs >= MAX_FINAL_COLOR_PROBE_LOGS + MAX_POSITIVE_VANILLA_TERRAIN_PROBE_LOGS) {
                return;
            }
            finalColorProbeLogs++;
        } else {
            if (terrainColorProbeLogs >= MAX_TERRAIN_COLOR_PROBE_LOGS + MAX_POSITIVE_VANILLA_TERRAIN_PROBE_LOGS) {
                return;
            }
            terrainColorProbeLogs++;
        }

        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        String color = deferredFramebufferColorSamples(readBuffer, fallbackColorAttachment());
        String normal = deferredFramebufferColorSamples(readBuffer, Attachment.NORMAL);
        String depth = readBuffer != null && readBuffer.isUsable()
                ? framebufferIdDepthSamples(readBuffer.getFramebufferId(), readBuffer.getWidth(), readBuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0)
                : "none";
        MainMod.LOGGER.info(
                "[AUSMColorProbe] stage={} color={} normal={} depth={} activePass={} phase={} safeVanilla={} reason='{}' read={} gl={}",
                stage,
                color,
                normal,
                depth,
                activePass,
                getPhase(),
                hardwareSafeVanillaTerrain,
                hardwareSafeVanillaTerrainReason,
                self().describeDeferredFramebuffer(readBuffer),
                glStateSummary()
        );
        self().logTerrainGridProbe(stage, readBuffer);
    }

    protected void logCompositeChainProbe(String stage, String detail) {
        if (!isPipelineActive || !pingPongManager.isInitialized()
                || compositeChainProbeLogs >= MAX_COMPOSITE_CHAIN_PROBE_LOGS) {
            return;
        }
        compositeChainProbeLogs++;

        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        MainMod.LOGGER.info(
                "[AUSMCompositeChainProbe] call={} stage={} detail={} read={} textures={} samples={} depth={} gl={}",
                compositeChainProbeLogs,
                stage,
                detail,
                self().describeDeferredFramebuffer(readBuffer),
                self().compositeChainTextureSummary(readBuffer),
                self().compositeChainSampleSummary(readBuffer),
                readBuffer != null && readBuffer.isUsable()
                        ? framebufferIdDepthSamples(readBuffer.getFramebufferId(), readBuffer.getWidth(), readBuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0)
                        : "none",
                glStateSummary()
        );
    }

    protected void logDeferredBoundaryProbe(String stage, String detail) {
        if (!isPipelineActive || !pingPongManager.isInitialized()
                || deferredBoundaryProbeLogs >= MAX_DEFERRED_BOUNDARY_PROBE_LOGS) {
            return;
        }
        deferredBoundaryProbeLogs++;

        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        MainMod.LOGGER.info(
                "[AUSMDeferredBoundaryProbe] call={} stage={} detail={} frame={} deferredRendered={} preDepthCopied={} activePass={} phase={} terrainCounts=opaque:{}/draw:{} read={} textures={} colors={} depth0={} depth1={} depth2={} gl={}",
                deferredBoundaryProbeLogs,
                stage,
                detail,
                pipelineFrameId,
                deferredPassesRenderedThisFrame,
                preTranslucentDepthCopiedThisFrame,
                activePass,
                getPhase(),
                terrainOpaqueLayerCount,
                terrainOpaqueDrawCount,
                self().describeDeferredFramebuffer(readBuffer),
                self().deferredBoundaryTextureSummary(readBuffer),
                self().deferredBoundaryColorSummary(readBuffer),
                self().deferredDepthSampleSummary(readBuffer, -1),
                self().deferredDepthSampleSummary(readBuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT),
                self().deferredDepthSampleSummary(readBuffer, DeferredFramebuffer.DEPTHTEX2_SNAPSHOT),
                glStateSummary()
        );
    }

    protected String deferredBoundaryTextureSummary(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        return "COLOR=" + framebuffer.getReadTexture(Attachment.COLOR) + "/" + framebuffer.getWriteTexture(Attachment.COLOR)
                + ";NORMAL=" + framebuffer.getReadTexture(Attachment.NORMAL) + "/" + framebuffer.getWriteTexture(Attachment.NORMAL)
                + ";COMPOSITE=" + framebuffer.getReadTexture(Attachment.COMPOSITE) + "/" + framebuffer.getWriteTexture(Attachment.COMPOSITE)
                + ";AUX2=" + framebuffer.getReadTexture(Attachment.AUX2) + "/" + framebuffer.getWriteTexture(Attachment.AUX2)
                + ";AUX6=" + framebuffer.getReadTexture(Attachment.AUX6) + "/" + framebuffer.getWriteTexture(Attachment.AUX6)
                + ";depth=" + framebuffer.getDepthTexture()
                + ";depth1=" + framebuffer.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX1_SNAPSHOT)
                + ";depth2=" + framebuffer.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX2_SNAPSHOT);
    }

    protected String deferredBoundaryColorSummary(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (Attachment attachment : self().deferredBoundaryProbeAttachments()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append("colortex")
                    .append(attachment.getIndex())
                    .append('=')
                    .append(deferredFramebufferColorSamples(framebuffer, attachment));
        }
        return builder.toString();
    }

    protected List<Attachment> deferredBoundaryProbeAttachments() {
        return List.of(
                Attachment.COLOR,
                Attachment.NORMAL,
                Attachment.COMPOSITE,
                Attachment.AUX2,
                Attachment.AUX6
        );
    }

    protected String deferredDepthSampleSummary(DeferredFramebuffer framebuffer, int snapshotIndex) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        int width = Math.max(1, framebuffer.getWidth());
        int height = Math.max(1, framebuffer.getHeight());
        int total = 0;
        int filled = 0;
        float minDepth = 1.0F;
        float maxDepth = 0.0F;
        StringBuilder samples = new StringBuilder();
        for (int[] point : self().compositeFallbackProbePoints(width, height)) {
            total++;
            float depth = snapshotIndex < 0
                    ? self().safeReadDeferredDepth(framebuffer, point[0], point[1], width, height)
                    : self().safeReadDeferredDepthSnapshot(framebuffer, snapshotIndex, point[0], point[1]);
            if (!Float.isFinite(depth)) {
                continue;
            }
            minDepth = Math.min(minDepth, depth);
            maxDepth = Math.max(maxDepth, depth);
            if (depth < 0.999F) {
                filled++;
            }
            if (samples.length() < 180) {
                if (samples.length() > 0) {
                    samples.append(';');
                }
                samples.append(point[0]).append(',').append(point[1]).append('=').append(PipelineWorldRenderScope.formatProbeFloat(depth));
            }
        }
        return "filled=" + filled + "/" + total
                + ",min=" + PipelineWorldRenderScope.formatProbeFloat(minDepth)
                + ",max=" + PipelineWorldRenderScope.formatProbeFloat(maxDepth)
                + ",samples=" + samples;
    }

    protected String compositeChainTextureSummary(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (Attachment attachment : self().compositeChainProbeAttachments()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(attachment)
                    .append('=')
                    .append(framebuffer.getReadTexture(attachment))
                    .append('/')
                    .append(framebuffer.getWriteTexture(attachment));
        }
        return builder.toString();
    }

    protected String compositeChainSampleSummary(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (Attachment attachment : self().compositeChainProbeAttachments()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append("colortex")
                    .append(attachment.getIndex())
                    .append('=')
                    .append(deferredFramebufferColorSamples(framebuffer, attachment));
        }
        return builder.toString();
    }

    protected List<Attachment> compositeChainProbeAttachments() {
        return List.of(
                Attachment.COLOR,
                Attachment.COMPOSITE,
                Attachment.AUX3,
                Attachment.AUX4,
                Attachment.AUX5,
                Attachment.AUX6
        );
    }

    protected void logTerrainGridProbe(String stage, DeferredFramebuffer readBuffer) {
        if (terrainGridProbeLogs >= MAX_TERRAIN_GRID_PROBE_LOGS || readBuffer == null || !readBuffer.isUsable()) {
            return;
        }
        terrainGridProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMTerrainGridProbe] stage={} activePass={} phase={} safeVanilla={} reason='{}' read={} textures={} gl={} grid={}",
                stage,
                activePass,
                getPhase(),
                hardwareSafeVanillaTerrain,
                hardwareSafeVanillaTerrainReason,
                self().describeDeferredFramebuffer(readBuffer),
                self().terrainProbeTextureSummary(readBuffer),
                self().terrainProbeGlStateSummary(),
                self().terrainGridProbeSummary(readBuffer)
        );
    }

    protected String terrainProbeTextureSummary(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        Attachment colorAttachment = fallbackColorAttachment();
        return "color=" + colorAttachment
                + ":" + framebuffer.getReadTexture(colorAttachment) + "/" + framebuffer.getWriteTexture(colorAttachment)
                + ", normal=" + framebuffer.getReadTexture(Attachment.NORMAL) + "/" + framebuffer.getWriteTexture(Attachment.NORMAL)
                + ", depth=" + framebuffer.getDepthTexture();
    }

    protected String terrainGridProbeSummary(DeferredFramebuffer framebuffer) {
        String errorBefore = self().drainGlErrorsForProbe();
        StringBuilder builder = new StringBuilder();
        builder.append("logical");
        Set<Attachment> attachments = new LinkedHashSet<>();
        attachments.add(fallbackColorAttachment());
        attachments.add(Attachment.AUX3);
        attachments.add(Attachment.AUX1);
        attachments.add(Attachment.NORMAL);
        attachments.add(Attachment.COMPOSITE);
        for (Attachment attachment : attachments) {
            builder.append(' ').append(attachment).append('=')
                    .append(self().terrainGridReadAttachmentSummary(framebuffer, attachment, attachment == fallbackColorAttachment()));
        }
        builder.append(",probeErr=").append(errorBefore).append('/').append(self().drainGlErrorsForProbe());
        return builder.toString();
    }

    protected String terrainGridReadAttachmentSummary(DeferredFramebuffer framebuffer, Attachment attachment, boolean includeDepth) {
        if (framebuffer == null || attachment == null) {
            return "invalid";
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int colorNonZero = 0;
        int alphaNonZero = 0;
        int depthFilled = 0;
        float minDepth = 1.0F;
        float maxDepth = 0.0F;
        StringBuilder examples = new StringBuilder();
        int sampleCount = 0;
        for (int row = 0; row < TERRAIN_GRID_PROBE_ROWS; row++) {
            int y = self().gridProbeCoordinate(row, TERRAIN_GRID_PROBE_ROWS, height);
            for (int column = 0; column < TERRAIN_GRID_PROBE_COLUMNS; column++) {
                int x = self().gridProbeCoordinate(column, TERRAIN_GRID_PROBE_COLUMNS, width);
                sampleCount++;
                float[] color = self().safeReadDeferredColor(framebuffer, attachment, x, y);
                if (!isFiniteColor(color)) {
                    continue;
                }
                int r = PipelineWorldRenderScope.probeColorByte(color[0]);
                int g = PipelineWorldRenderScope.probeColorByte(color[1]);
                int b = PipelineWorldRenderScope.probeColorByte(color[2]);
                int a = PipelineWorldRenderScope.probeColorByte(color[3]);
                if (r != 0 || g != 0 || b != 0) {
                    colorNonZero++;
                }
                if (a != 0) {
                    alphaNonZero++;
                }
                float depth = 1.0F;
                boolean filledDepth = false;
                if (includeDepth) {
                    depth = self().safeReadDeferredDepth(framebuffer, x, y, width, height);
                    if (Float.isFinite(depth)) {
                        minDepth = Math.min(minDepth, depth);
                        maxDepth = Math.max(maxDepth, depth);
                        filledDepth = depth < 0.999F;
                        if (filledDepth) {
                            depthFilled++;
                        }
                    }
                }
                if ((filledDepth || examples.length() == 0) && examples.length() < 160) {
                    if (examples.length() > 0) {
                        examples.append('|');
                    }
                    examples.append(x).append(',').append(y)
                            .append(":rgba(").append(r).append('/').append(g).append('/').append(b).append('/').append(a).append(')');
                    if (includeDepth) {
                        examples.append(":d=").append(PipelineWorldRenderScope.formatProbeFloat(depth));
                    }
                }
            }
        }
        return "nz=" + colorNonZero + "/" + sampleCount
                + ",a=" + alphaNonZero + "/" + sampleCount
                + (includeDepth
                ? ",depthFilled=" + depthFilled + "/" + sampleCount
                + ",depthRange=" + PipelineWorldRenderScope.formatProbeFloat(minDepth) + ".." + PipelineWorldRenderScope.formatProbeFloat(maxDepth)
                : "")
                + ",examples=" + (examples.length() > 0 ? examples : "none");
    }

    protected static int probeColorByte(float value) {
        if (!Float.isFinite(value)) {
            return 0;
        }
        return Math.clamp(Math.round(value * 255.0F), 0, 255);
    }

    protected String drainGlErrorsForProbe() {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR && count < 8) {
            if (builder.length() > 0) {
                builder.append('+');
            }
            builder.append(PipelineWorldRenderScope.glErrorName(error));
            count++;
        }
        if (error != GL11.GL_NO_ERROR) {
            builder.append("+more");
        }
        return builder.length() > 0 ? builder.toString() : "ok";
    }

    protected void drainPausedPostRenderGlErrors(String stage) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (!self().shouldDrainPausedPostRenderGlErrors(mc)) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        int count = 0;
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR && count < 16) {
            if (builder.length() > 0) {
                builder.append('+');
            }
            builder.append(PipelineWorldRenderScope.glErrorName(error));
            count++;
        }
        if (error != GL11.GL_NO_ERROR) {
            builder.append("+more");
        }
        if (count > 0 && pausedPostRenderGlErrorLogs < 8) {
            pausedPostRenderGlErrorLogs++;
            MainMod.LOGGER.info("[AUSMPausedGlDrain] stage={} errors={} screen={}",
                    stage,
                    builder,
                    self().pausedScreenName(mc));
        }
    }

    protected boolean shouldDrainPausedPostRenderGlErrors(Minecraft mc) {
        if (mc == null) {
            return false;
        }
        if (MinecraftReflectionCompat.isGamePaused(mc)) {
            return true;
        }
        GuiScreen screen = MinecraftReflectionCompat.currentScreen(mc);
        return screen != null || renderingGui;
    }

    protected String pausedScreenName(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.currentScreen(mc) == null) {
            return "none";
        }
        return MinecraftReflectionCompat.currentScreen(mc).getClass().getName();
    }

    protected int gridProbeCoordinate(int index, int count, int size) {
        if (size <= 1) {
            return 0;
        }
        int value = (int) (((long) (index + 1) * size) / (count + 1));
        return Math.clamp(value, 0, size - 1);
    }

    protected String terrainProbeGlStateSummary() {
        try {
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
            int viewportX = viewportBuffer.get(0);
            int viewportY = viewportBuffer.get(1);
            int viewportWidth = viewportBuffer.get(2);
            int viewportHeight = viewportBuffer.get(3);
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, viewportBuffer);
            int scissorX = viewportBuffer.get(0);
            int scissorY = viewportBuffer.get(1);
            int scissorWidth = viewportBuffer.get(2);
            int scissorHeight = viewportBuffer.get(3);
            terrainProbeBooleanBuffer.clear();
            GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, terrainProbeBooleanBuffer);
            return "drawFbo=" + GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                    + ", readFbo=" + GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                    + ", drawBuffer=" + GL11.glGetInteger(GL11.GL_DRAW_BUFFER)
                    + ", readBuffer=" + GL11.glGetInteger(GL11.GL_READ_BUFFER)
                    + ", drawBuffers=" + self().drawBuffersProbeSummary()
                    + ", program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                    + ", vao=" + GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
                    + ", arrayBuffer=" + GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
                    + ", elementArrayBuffer=" + GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING)
                    + ", activeTex=" + GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
                    + ", tex2d=" + GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                    + ", blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                    + ", alpha=" + GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
                    + ", depth=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                    + ", depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                    + ", depthFunc=" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
                    + ", colorMask=" + (terrainProbeBooleanBuffer.get(0) != 0)
                    + "/" + (terrainProbeBooleanBuffer.get(1) != 0)
                    + "/" + (terrainProbeBooleanBuffer.get(2) != 0)
                    + "/" + (terrainProbeBooleanBuffer.get(3) != 0)
                    + ", cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE)
                    + ", frontFace=" + GL11.glGetInteger(GL11.GL_FRONT_FACE)
                    + ", scissor=" + GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
                    + ":" + scissorX + "/" + scissorY + "/" + scissorWidth + "/" + scissorHeight
                    + ", polygonOffset=" + GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL)
                    + ", viewport=" + viewportX + "/" + viewportY + "/" + viewportWidth + "/" + viewportHeight;
        } catch (RuntimeException | LinkageError exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    protected String drawBuffersProbeSummary() {
        if (!GLContext.getCapabilities().OpenGL20) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int slots = Math.min(4, maxDrawBuffers());
        for (int i = 0; i < slots; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i));
        }
        return builder.toString();
    }

    protected static String framebufferStatusName(int status) {
        if (status == GL30.GL_FRAMEBUFFER_COMPLETE) {
            return "complete";
        }
        if (status == GL30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT) {
            return "incomplete-attachment";
        }
        if (status == GL30.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT) {
            return "missing-attachment";
        }
        if (status == GL30.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER) {
            return "incomplete-draw-buffer";
        }
        if (status == GL30.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER) {
            return "incomplete-read-buffer";
        }
        if (status == GL30.GL_FRAMEBUFFER_UNSUPPORTED) {
            return "unsupported";
        }
        return String.valueOf(status);
    }

    protected static String glErrorName(int error) {
        if (error == GL11.GL_NO_ERROR) {
            return "ok";
        }
        if (error == GL11.GL_INVALID_ENUM) {
            return "invalid-enum";
        }
        if (error == GL11.GL_INVALID_VALUE) {
            return "invalid-value";
        }
        if (error == GL11.GL_INVALID_OPERATION) {
            return "invalid-operation";
        }
        if (error == GL11.GL_STACK_OVERFLOW) {
            return "stack-overflow";
        }
        if (error == GL11.GL_STACK_UNDERFLOW) {
            return "stack-underflow";
        }
        if (error == GL11.GL_OUT_OF_MEMORY) {
            return "out-of-memory";
        }
        return String.valueOf(error);
    }

    protected void logDistantHorizonsColorProbe(String stage) {
        // Probe disabled.
    }

    protected void logDistantHorizonsPassColorProbe(String stage, RenderPass pass) {
        // Probe disabled.
    }

    protected static boolean isDistantHorizonsProbeMarker(float[] aux3) {
        return aux3 != null
                && aux3.length >= 3
                && aux3[2] > 0.5f
                && aux3[0] < 0.2f
                && aux3[1] < 0.2f;
    }
}
