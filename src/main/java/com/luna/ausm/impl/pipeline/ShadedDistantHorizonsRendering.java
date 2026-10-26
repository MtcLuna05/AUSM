package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.impl.client.DistantHorizonsCompat;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.pipeline.shader.ShaderProgram;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Locale;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Render-thread shaderpack state without a dependency on optional DH API classes. */
public final class ShadedDistantHorizonsRendering {
    public static final int DEPTH_UNIT = 60;
    public static final int OPAQUE_DEPTH_UNIT = 61;
    public static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    public static final FloatBuffer PROJECTION_INVERSE = BufferUtils.createFloatBuffer(16);
    public static final FloatBuffer PREVIOUS_PROJECTION = BufferUtils.createFloatBuffer(16);
    public static final FloatBuffer MODEL_VIEW = BufferUtils.createFloatBuffer(16);
    public static final FloatBuffer MODEL_VIEW_INVERSE = BufferUtils.createFloatBuffer(16);
    private static final float[] MODEL_OFFSET = new float[3];
    private static DistantHorizonsRenderTarget opaque;
    private static DistantHorizonsRenderTarget transparent;
    private static PipelineProgram activeProgram;
    private static int vertexArray;
    private static boolean opaquePending;
    private static boolean transparentPending;
    private static boolean renderingTransparent;
    public static float near;
    public static float far;
    public static int renderDistance;
    public static boolean shaderpackOverrideActive;
    private static String lastPrepareFailure;

    private ShadedDistantHorizonsRendering() { }

    public static boolean requestedFor(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return Boolean.parseBoolean(System.getProperty("ausm.dh.shaded", "true")) && DistantHorizonsCompat.installed()
                && ((lower.contains("complementaryunbound") || lower.contains("complementaryreimagined"))
                        && !lower.contains("euphoria")
                        || lower.startsWith("bsl") || lower.contains("/bsl")
                        || lower.contains("makeup-ultrafast") || lower.contains("makeup ultrafast"));
    }

    public static boolean requested() {
        return requestedFor(PipelineContext.getInstance().activePackName);
    }

    public static boolean prepare() {
        PipelineContext context = PipelineContext.getInstance();
        if (!requested()) return prepareFailure("pack-not-requested:" + context.activePackName);
        if (!context.canCaptureDistantHorizonsFrame()) return prepareFailure("frame-not-capturable");
        int textureUnits = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        if (textureUnits <= OPAQUE_DEPTH_UNIT) return prepareFailure("texture-units=" + textureUnits);
        PipelineProgram terrain = context.programs.get(RenderPass.DH_TERRAIN);
        PipelineProgram water = context.programs.get(RenderPass.DH_WATER);
        if (!supported(terrain)) return prepareFailure(programFailure("DH_TERRAIN", terrain));
        if (!supported(water)) return prepareFailure(programFailure("DH_WATER", water));
        lastPrepareFailure = null;
        DeferredFramebuffer scene = context.pingPongManager.getReadBuffer();
        if (opaque == null || opaque.width != scene.getWidth() || opaque.height != scene.getHeight()) {
            releaseTargets();
            opaque = new DistantHorizonsRenderTarget(scene.getWidth(), scene.getHeight());
            transparent = new DistantHorizonsRenderTarget(scene.getWidth(), scene.getHeight());
        }
        return true;
    }

    private static String programFailure(String name, PipelineProgram program) {
        if (program == null) return name + "-missing";
        if (!program.hasOwnProgram()) return name + "-not-owned";
        if (!program.drawBuffers().equals(List.of(Attachment.COLOR))) return name + "-draw-buffers=" + program.drawBuffers();
        int location = program.shaderProgram().getUniformLocation("ausmDhModelOffset");
        return name + "-model-offset-location=" + location;
    }

    private static boolean prepareFailure(String reason) {
        if (!reason.equals(lastPrepareFailure)) {
            lastPrepareFailure = reason;
            MainMod.LOGGER.warn("[AUSMDistantHorizons] Shader override unavailable: {}", reason);
        }
        return false;
    }

    private static boolean supported(PipelineProgram program) {
        List<Attachment> drawBuffers = program == null ? List.of() : program.drawBuffers();
        String packName = PipelineContext.getInstance().activePackName;
        String lowerPackName = packName == null ? "" : packName.toLowerCase(Locale.ROOT);
        boolean familyPrimaryColor = (lowerPackName.startsWith("bsl") || lowerPackName.contains("/bsl")
                || lowerPackName.contains("makeup-ultrafast") || lowerPackName.contains("makeup ultrafast"))
                && drawBuffers.size() == 2 && drawBuffers.get(0) == Attachment.COLOR;
        return program != null && program.hasOwnProgram()
                && (drawBuffers.equals(List.of(Attachment.COLOR)) || familyPrimaryColor)
                && program.shaderProgram().getUniformLocation("ausmDhModelOffset") >= 0;
    }

    public static void beginFrame() {
        opaquePending = false;
        transparentPending = false;
        renderingTransparent = false;
        if (opaque != null) {
            DistantHorizonsRenderTarget.clear(opaque);
            DistantHorizonsRenderTarget.clear(transparent);
            PREVIOUS_PROJECTION.clear();
            PREVIOUS_PROJECTION.put(PROJECTION.duplicate().rewind()).flip();
        }
    }

    public static int framebuffer() { return target().framebuffer; }

    private static DistantHorizonsRenderTarget target() { return renderingTransparent ? transparent : opaque; }

    public static void selectPass(boolean water) {
        renderingTransparent = water;
        activeProgram = PipelineContext.getInstance().programs.get(water ? RenderPass.DH_WATER : RenderPass.DH_TERRAIN);
    }

    public static void clearPass() {
        if (renderingTransparent) DistantHorizonsRenderTarget.copyDepth(opaque, transparent);
        else DistantHorizonsRenderTarget.clear(opaque);
        target().bind();
    }

    public static int programId() { return activeProgram.shaderProgram().getId(); }

    public static void bind() {
        PipelineContext context = PipelineContext.getInstance();
        ShaderProgram shader = activeProgram.shaderProgram();
        TextureBinder.bindDeferredTextures();
        TextureBinder.bindShadowTextures(activeProgram.pass());
        TextureBinder.bindMaterialFallbackTextures();
        target().bind();
        shader.bind();
        context.bindProgramResources(activeProgram.pass(), shader);
        // Water reflections read opaque DH depth, never their own attachment.
        if (renderingTransparent) TextureBinder.bindRawTexture(DEPTH_UNIT, opaque.depth);
        matrix(shader, "ausmDhProjection", PROJECTION);
        matrix(shader, "ausmDhProjectionInverse", PROJECTION_INVERSE);
        matrix(shader, "ausmDhModelView", MODEL_VIEW);
        matrix(shader, "ausmDhModelViewInverse", MODEL_VIEW_INVERSE);
        GL20.glUniform3f(shader.getUniformLocation("ausmDhModelOffset"), MODEL_OFFSET[0], MODEL_OFFSET[1], MODEL_OFFSET[2]);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        if (vertexArray == 0) vertexArray = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vertexArray);
    }

    private static void matrix(ShaderProgram shader, String name, FloatBuffer data) {
        GL20.glUniformMatrix4(shader.getUniformLocation(name), false, data);
    }

    public static void modelOffset(float x, float y, float z) {
        MODEL_OFFSET[0] = x;
        MODEL_OFFSET[1] = y;
        MODEL_OFFSET[2] = z;
        GL20.glUniform3f(activeProgram.shaderProgram().getUniformLocation("ausmDhModelOffset"), x, y, z);
    }

    public static void bindVertexBuffer(int buffer) {
        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glEnableVertexAttribArray(2);
        GL30.glVertexAttribIPointer(0, 4, GL11.GL_UNSIGNED_SHORT, 16, 0L);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, 16, 8L);
        GL30.glVertexAttribIPointer(2, 4, GL11.GL_UNSIGNED_BYTE, 16, 12L);
    }

    public static void captured() {
        if (renderingTransparent) transparentPending = true;
        else opaquePending = true;
    }

    public static void bindDepthTextures() {
        if (!requested() || opaque == null) return;
        // Before deferred water runs, depthtex0 must already contain opaque
        // LODs. The water target is still cleared at the deferred/cloud pass.
        TextureBinder.bindRawTexture(DEPTH_UNIT, renderingTransparent ? transparent.depth : opaque.depth);
        TextureBinder.bindRawTexture(OPAQUE_DEPTH_UNIT, opaque.depth);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    public static void merge(boolean water) {
        if (water && requested()) DistantHorizonsCompat.renderDeferred();
        if (water ? !transparentPending : !opaquePending) return;
        if (water) transparentPending = false;
        else opaquePending = false;
        DeferredFramebuffer scene = PipelineContext.getInstance().pingPongManager.getReadBuffer();
        DistantHorizonsRenderTarget.merge(water ? transparent : opaque, scene, water);
    }

    private static void releaseTargets() {
        if (opaque != null) opaque.delete();
        if (transparent != null) transparent.delete();
        opaque = null;
        transparent = null;
    }

    public static void release() {
        releaseTargets();
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray);
        vertexArray = 0;
        opaquePending = false;
        transparentPending = false;
        activeProgram = null;
        DistantHorizonsRenderTarget.release();
    }
}
