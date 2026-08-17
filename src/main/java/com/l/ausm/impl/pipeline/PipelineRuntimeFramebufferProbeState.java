package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.pipeline.shader.FullscreenArrayProgram;
import com.l.ausm.impl.pipeline.shader.FullscreenProgramArray;
import com.l.ausm.impl.pipeline.shader.PipelineProgram;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import static com.l.ausm.impl.pipeline.PipelineGlState.maxDrawBuffers;

abstract class PipelineRuntimeDiagnosticsState0 extends PipelineRuntimeProbeState {
    protected String framebufferSamples(Framebuffer framebuffer) {
        if (framebuffer == null || MinecraftReflectionCompat.framebufferWidth(framebuffer) <= 0 || MinecraftReflectionCompat.framebufferHeight(framebuffer) <= 0) {
            return "none";
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, MinecraftReflectionCompat.framebufferObject(framebuffer));
            GL11.glReadBuffer(MinecraftReflectionCompat.framebufferObject(framebuffer) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            int width = MinecraftReflectionCompat.framebufferWidth(framebuffer);
            int height = MinecraftReflectionCompat.framebufferHeight(framebuffer);
            return "center=" + self().readFramebufferPixel(width / 2, height / 2)
                    + ";upper=" + self().readFramebufferPixel(width / 2, Math.max(0, height * 3 / 4))
                    + ";lower=" + self().readFramebufferPixel(width / 2, Math.max(0, height / 4));
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            self().restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    protected String currentDrawFramebufferColorSamples(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.displayWidth(mc) <= 0 || MinecraftReflectionCompat.displayHeight(mc) <= 0) {
            return "none";
        }
        int drawFramebuffer = self().currentDrawFramebufferBinding();
        int drawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        if (drawFramebuffer == 0 && drawBuffer == GL11.GL_NONE) {
            drawBuffer = GL11.GL_BACK;
        }
        return self().framebufferIdColorSamples(drawFramebuffer, MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc), drawBuffer);
    }

    protected String framebufferIdColorSamples(int framebuffer, int width, int height, int readBuffer) {
        if (width <= 0 || height <= 0 || readBuffer == GL11.GL_NONE) {
            return "invalid";
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(readBuffer);
            return self().sampleBoundReadFramebuffer(width, height, false);
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            self().restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    protected String framebufferDepthSamples(Framebuffer framebuffer) {
        if (framebuffer == null || MinecraftReflectionCompat.framebufferWidth(framebuffer) <= 0 || MinecraftReflectionCompat.framebufferHeight(framebuffer) <= 0) {
            return "none";
        }
        return self().framebufferIdDepthSamples(
                MinecraftReflectionCompat.framebufferObject(framebuffer),
                MinecraftReflectionCompat.framebufferWidth(framebuffer),
                MinecraftReflectionCompat.framebufferHeight(framebuffer),
                MinecraftReflectionCompat.framebufferObject(framebuffer) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
    }

    protected String currentFramebufferDepthSamples(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.displayWidth(mc) <= 0 || MinecraftReflectionCompat.displayHeight(mc) <= 0) {
            return "none";
        }
        int readFramebuffer = self().currentReadFramebufferBinding();
        int readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        if (readFramebuffer == 0 && readBuffer == GL11.GL_NONE) {
            readBuffer = GL11.GL_BACK;
        }
        return self().framebufferIdDepthSamples(readFramebuffer, MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc), readBuffer);
    }

    protected String deferredFramebufferColorSamples(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            int texture = framebuffer.getReadTexture(attachment);
            if (texture <= 0) {
                return "no-texture";
            }
            int probeFbo = GL30.glGenFramebuffers();
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, probeFbo);
                GL30.glFramebufferTexture2D(
                        GL30.GL_READ_FRAMEBUFFER,
                        GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D,
                        texture,
                        0
                );
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                return self().sampleBoundReadFramebuffer(
                        Math.max(1, framebuffer.getAttachmentWidth(attachment)),
                        Math.max(1, framebuffer.getAttachmentHeight(attachment)),
                        false);
            } finally {
                GL30.glDeleteFramebuffers(probeFbo);
            }
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            self().restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
            MinecraftReflectionCompat.glStateBindTexture(previousTexture);
        }
    }

    protected String deferredFramebufferRecoveryColorSamples(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        if (!framebuffer.hasRecoveryColorSnapshot()) {
            return "no-snapshot";
        }
        int width = Math.max(1, framebuffer.getRecoveryColorWidth());
        int height = Math.max(1, framebuffer.getRecoveryColorHeight());
        int x = Math.clamp(width / 2, 0, width - 1);
        int bottomSkyY = Math.clamp(height / 16, 0, height - 1);
        int lowerY = Math.clamp(height * 3 / 16, 0, height - 1);
        int centerY = Math.clamp(height / 2, 0, height - 1);
        int upperY = Math.clamp(height * 13 / 16, 0, height - 1);
        int topDomeY = Math.clamp(height * 15 / 16, 0, height - 1);
        return "bottomSky=" + self().recoveryColorPixelSummary(framebuffer, x, bottomSkyY)
                + ";lower=" + self().recoveryColorPixelSummary(framebuffer, x, lowerY)
                + ";center=" + self().recoveryColorPixelSummary(framebuffer, x, centerY)
                + ";upper=" + self().recoveryColorPixelSummary(framebuffer, x, upperY)
                + ";topDome=" + self().recoveryColorPixelSummary(framebuffer, x, topDomeY);
    }

    protected String recoveryColorPixelSummary(DeferredFramebuffer framebuffer, int x, int y) {
        try {
            float[] color = framebuffer.readRecoveryColorAt(x, y);
            if (!self().isFiniteColor(color)) {
                return x + "," + y + "=rgba(nan,nan,nan,nan)";
            }
            return x + "," + y + "=rgba("
                    + PipelineRuntimeState.recoveryColorByte(color[0]) + ','
                    + PipelineRuntimeState.recoveryColorByte(color[1]) + ','
                    + PipelineRuntimeState.recoveryColorByte(color[2]) + ','
                    + PipelineRuntimeState.recoveryColorByte(color[3]) + ')';
        } catch (RuntimeException | LinkageError e) {
            return x + "," + y + "=error=" + e.getClass().getSimpleName();
        }
    }

    protected static int recoveryColorByte(float value) {
        if (!Float.isFinite(value)) {
            return 0;
        }
        return Math.clamp(Math.round(value * 255.0f), 0, 255);
    }

    protected String deferredFramebufferAttachmentSamples(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        for (Attachment attachment : Attachment.values()) {
            if (summary.length() > 0) {
                summary.append('|');
            }
            summary.append(attachment.name())
                    .append('=')
                    .append(self().deferredFramebufferColorSamples(framebuffer, attachment));
        }
        return summary.toString();
    }

    protected String shaderedVoidSkyProgramSummary() {
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        return "compositeFixed=" + self().fullscreenProgramsSummary(ProgramArrayId.COMPOSITE)
                + ", compositeIndexed=" + self().fullscreenArrayProgramsSummary(ProgramArrayId.COMPOSITE)
                + ", final=" + self().describePipelineProgram(finalProgram)
                + ", finalDrawBuffers=" + (finalProgram != null ? finalProgram.drawBuffers() : "none")
                + ", finalComputes=" + finalComputePrograms.size();
    }

    protected String fullscreenProgramsSummary(ProgramArrayId arrayId) {
        FullscreenProgramArray array = fullscreenProgramArrays.get(arrayId);
        if (array == null || array.fixedPasses().isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        for (RenderPass pass : array.fixedPasses()) {
            PipelineProgram program = programs.get(pass);
            if (program == null || !program.hasOwnProgram()) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(',');
            }
            summary.append(pass).append(program.drawBuffers());
        }
        return summary.length() == 0 ? "none" : summary.toString();
    }

    protected String fullscreenArrayProgramsSummary(ProgramArrayId arrayId) {
        List<FullscreenArrayProgram> arrayPrograms = fullscreenArrayPrograms.getOrDefault(arrayId, List.of());
        if (arrayPrograms.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        for (FullscreenArrayProgram program : arrayPrograms) {
            if (program == null || !program.hasProgram()) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(',');
            }
            summary.append(program.name()).append(program.drawBuffers());
        }
        return summary.length() == 0 ? "none" : summary.toString();
    }

    protected String framebufferIdDepthSamples(int framebuffer, int width, int height, int readBuffer) {
        if (width <= 0 || height <= 0) {
            return "invalid-size";
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            if (readBuffer != GL11.GL_NONE) {
                GL11.glReadBuffer(readBuffer);
            }
            return self().sampleBoundReadFramebuffer(width, height, true);
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            self().restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    protected String sampleBoundReadFramebuffer(int width, int height, boolean includeDepth) {
        int x = Math.max(0, width / 2);
        int bottomSkyY = Math.clamp(height / 16, 0, height - 1);
        int lowerY = Math.clamp(height * 3 / 16, 0, height - 1);
        int centerY = Math.clamp(height / 2, 0, height - 1);
        int upperY = Math.clamp(height * 13 / 16, 0, height - 1);
        int topDomeY = Math.clamp(height * 15 / 16, 0, height - 1);
        return "bottomSky=" + self().readFramebufferPixelSummary(x, bottomSkyY, includeDepth)
                + ";lower=" + self().readFramebufferPixelSummary(x, lowerY, includeDepth)
                + ";center=" + self().readFramebufferPixelSummary(x, centerY, includeDepth)
                + ";upper=" + self().readFramebufferPixelSummary(x, upperY, includeDepth)
                + ";topDome=" + self().readFramebufferPixelSummary(x, topDomeY, includeDepth);
    }

    protected String readFramebufferPixel(int x, int y) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return (pixel.get(0) & 0xFF) + "/" + (pixel.get(1) & 0xFF) + "/" + (pixel.get(2) & 0xFF) + "/" + (pixel.get(3) & 0xFF);
    }

    protected int currentReadFramebufferBinding() {
        return GLContext.getCapabilities().OpenGL30
                ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                : GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    protected boolean isIgnoredShaderlessSkyProbeScreen(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.currentScreen(mc) == null) {
            return false;
        }
        String screenClass = MinecraftReflectionCompat.currentScreen(mc).getClass().getName();
        return "net.minecraft.client.gui.GuiChat".equals(screenClass);
    }

    protected int currentDrawFramebufferBinding() {
        return GLContext.getCapabilities().OpenGL30
                ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                : GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    protected void restoreReadBufferForFramebuffer(int framebuffer, int readBuffer) {
        GL11.glReadBuffer(self().safeBufferForFramebuffer(framebuffer, readBuffer));
    }

    protected void restoreDrawBufferForFramebuffer(int framebuffer, int drawBuffer) {
        GL11.glDrawBuffer(self().safeBufferForFramebuffer(framebuffer, drawBuffer));
    }

    protected int safeBufferForFramebuffer(int framebuffer, int buffer) {
        if (buffer == GL11.GL_NONE) {
            return GL11.GL_NONE;
        }
        boolean attachmentBuffer = buffer >= GL30.GL_COLOR_ATTACHMENT0 && buffer < GL30.GL_COLOR_ATTACHMENT0 + maxDrawBuffers();
        return framebuffer == 0
                ? attachmentBuffer ? GL11.GL_BACK : buffer
                : attachmentBuffer ? buffer : GL30.GL_COLOR_ATTACHMENT0;
    }

    protected void bindMinecraftFramebufferForGui(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.minecraftFramebuffer(mc) == null) {
            return;
        }
        Framebuffer framebuffer = MinecraftReflectionCompat.minecraftFramebuffer(mc);
        int framebufferObject = MinecraftReflectionCompat.framebufferObject(framebuffer);
        MinecraftReflectionCompat.bindFramebuffer(framebuffer, false);
        self().restoreDrawBufferForFramebuffer(framebufferObject, GL30.GL_COLOR_ATTACHMENT0);
        self().restoreReadBufferForFramebuffer(framebufferObject, GL30.GL_COLOR_ATTACHMENT0);
        MinecraftReflectionCompat.glStateViewport(
                0,
                0,
                MinecraftReflectionCompat.displayWidth(mc),
                MinecraftReflectionCompat.displayHeight(mc));
    }

    protected int boundTexture2D(int textureUnit) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            MinecraftReflectionCompat.glStateSetActiveTexture(textureUnit);
            return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        } finally {
            MinecraftReflectionCompat.glStateSetActiveTexture(previousActiveTexture);
        }
    }

    protected boolean texture2DEnabled(int textureUnit) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            MinecraftReflectionCompat.glStateSetActiveTexture(textureUnit);
            return GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        } finally {
            MinecraftReflectionCompat.glStateSetActiveTexture(previousActiveTexture);
        }
    }

    protected boolean textureCoordArrayEnabled(int textureUnit) {
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        try {
            MinecraftReflectionCompat.setClientActiveTexture(textureUnit);
            return GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY);
        } finally {
            MinecraftReflectionCompat.setClientActiveTexture(previousClientTexture);
        }
    }

    protected String fogColorSummary() {
        FloatBuffer color = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_FOG_COLOR, color);
        return color.get(0) + "/" + color.get(1) + "/" + color.get(2) + "/" + color.get(3);
    }

    protected String currentColorSummary() {
        FloatBuffer color = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        return color.get(0) + "/" + color.get(1) + "/" + color.get(2) + "/" + color.get(3);
    }

    protected String colorMaskSummary() {
        ByteBuffer colorMask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        return (colorMask.get(0) != 0)
                + "/"
                + (colorMask.get(1) != 0)
                + "/"
                + (colorMask.get(2) != 0)
                + "/"
                + (colorMask.get(3) != 0);
    }

    protected String viewportSummary() {
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        return viewportBuffer.get(0)
                + ","
                + viewportBuffer.get(1)
                + ","
                + viewportBuffer.get(2)
                + "x"
                + viewportBuffer.get(3);
    }

    public boolean shouldUseCrystalOnlyEmission(IBlockState state) {
        return PipelineRuntimeState.isAstralCrystalCluster(state);
    }

    public boolean shouldUseCrystalOnlyEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return self().shouldUseCrystalOnlyEmission(self().actualLightState(state, blockAccess, pos));
    }

    protected int blockRenderEmissionForState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        int explicit = self().explicitShaderedBlockEmission(state, blockAccess, pos);
        if (explicit > 0) {
            return explicit;
        }
        return PipelineRuntimeState.intrinsicBlockEmission(state);
    }

    protected int explicitShaderedBlockEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        int blockcrafteryEmission = self().blockcrafteryLightEmission(state);
        if (blockcrafteryEmission > 0) {
            return blockcrafteryEmission;
        }
        int astralEmission = PipelineRuntimeState.astralCrystalEmission(state);
        if (astralEmission > 0) {
            return astralEmission;
        }
        return 0;
    }

    protected int inheritedBlockRenderEmission(IBlockState state) {
        int blockcrafteryEmission = self().blockcrafteryLightEmission(state);
        if (blockcrafteryEmission > 0) {
            return blockcrafteryEmission;
        }
        int astralEmission = PipelineRuntimeState.astralCrystalEmission(state);
        if (astralEmission > 0) {
            return astralEmission;
        }
        try {
            return PipelineRuntimeState.clampLightValue(MinecraftReflectionCompat.stateLightValue(state));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public int blockIntrinsicEmission(IBlockState state) {
        return state != null ? self().inheritedBlockRenderEmission(state) : 0;
    }

    protected int blockcrafteryLightEmission(IBlockState state) {
        return 0;
    }

    protected IBlockState inheritedRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!PipelineRuntimeState.isBlockcrafteryEditableBlock(state)) {
            return null;
        }
        return self().inheritedBlockcrafteryRenderState(state, blockAccess, pos);
    }

    protected int containedFrameEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        IBlockState contained = self().inheritedBlockcrafteryRenderState(state, blockAccess, pos);
        return contained != null ? self().blockRenderEmissionForState(contained, blockAccess, pos) : 0;
    }

    protected boolean containedFrameHasBloom(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        IBlockState contained = self().inheritedBlockcrafteryRenderState(state, blockAccess, pos);
        if (contained == null) return false;
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        boolean bloom = self().stateHasBloomLayerGeometry(contained)
                || (bloomLayer != null && PipelineRuntimeState.canRenderInLayer(contained, bloomLayer))
                || self().blockRenderEmissionForState(contained, blockAccess, pos) > 0;
        if (!bloom) return false;
        int probe = blockcrafteryBloomDecisionProbeCount.incrementAndGet();
        if (probe <= 0) {
            MainMod.LOGGER.info("[AUSMBlockcrafteryBloomProbe] call={} thread={} pos={} state={} access={} present={} emission={} bloom={} primary={} secondary={} layer={} bloomLayer={}",
                    probe, Thread.currentThread().getName(), pos, state,
                    blockAccess != null ? blockAccess.getClass().getName() : "null",
                    true, self().blockRenderEmissionForState(contained, blockAccess, pos), bloom,
                    self().diagnosticStateName(contained), "null",
                    MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer());
        }
        return bloom;
    }

    /**
     * Shared framed Bloom decision for renderer compatibility hooks.
     */
    public boolean hasContainedFrameBloom(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return self().containedFrameHasBloom(state, blockAccess, pos);
    }

    public boolean containedFrameHasBloom(IBlockAccess blockAccess, BlockPos pos) {
        IBlockState host = MinecraftReflectionCompat.blockAccessBlockState(blockAccess, pos);
        return self().containedFrameHasBloom(host, blockAccess, pos);
    }

    public int containedFrameEmission(IBlockAccess blockAccess, BlockPos pos) {
        IBlockState host = MinecraftReflectionCompat.blockAccessBlockState(blockAccess, pos);
        return self().containedFrameEmission(host, blockAccess, pos);
    }

    public BlockRenderLayer containedFrameBaseLayer(IBlockAccess blockAccess, BlockPos pos) {
        IBlockState host = MinecraftReflectionCompat.blockAccessBlockState(blockAccess, pos);
        IBlockState contained = self().inheritedBlockcrafteryRenderState(host, blockAccess, pos);
        BlockRenderLayer layer = PipelineRuntimeState.safeRenderLayer(contained);
        return layer != null && !AusmBloomLayer.isBloomLayer(layer) ? layer : BlockRenderLayer.SOLID;
    }

    public boolean shouldForceCeleritasGeometryBloomFullbright(IBlockState state, IBlockAccess blockAccess,
                                                               BlockPos pos, BlockRenderLayer layer) {
        if (!PipelineRuntimeState.isBlockcrafteryEditableBlock(state)) {
            return self().shouldForceCeleritasGeometryBloomFullbright(state, layer);
        }
        IBlockState contained = self().inheritedBlockcrafteryRenderState(state, blockAccess, pos);
        if (contained != null && self().containedFrameHasBloom(state, blockAccess, pos)) {
            return AusmBloomLayer.isBloomLayer(layer)
                    || (!AusmBloomLayer.isBloomLayer(layer)
                    && self().blockRenderEmissionForState(contained, blockAccess, pos) > 0);
        }
        return self().shouldForceCeleritasGeometryBloomFullbright(state, layer);
    }

    protected boolean isBloomOrEmissiveInheritedState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        return self().blockShaderlessBloomEmission(state, blockAccess, pos) > 0;
    }
}
