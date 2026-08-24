package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.compat.NothiriumFogCompat;
import com.luna.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import meldexun.nothirium.api.renderer.IVBOPart;
import meldexun.nothirium.api.renderer.chunk.ChunkRenderPass;
import meldexun.nothirium.api.renderer.chunk.IRenderChunkProvider;
import meldexun.nothirium.mc.renderer.chunk.RenderChunk;
import meldexun.renderlib.util.Frustum;
import meldexun.renderlib.util.GLBuffer;
import meldexun.renderlib.util.GLShader;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.ChunkRendererGL43", remap = false)
public class NothiriumChunkRendererGL43Mixin {
    /**
     * Three slots make the persistent mapped indirect buffers one frame less
     * likely to need the renderer's blocking timestamp retirement.
     */
    private static final int AUSM_INDIRECT_BUFFER_SLOTS = 3;

    private double ausm$cameraX;
    private double ausm$cameraY;
    private double ausm$cameraZ;
    private int ausm$cameraOffsetUniform = Integer.MIN_VALUE;
    private boolean ausm$staticOriginsEnabled;

    @Shadow(remap = false)
    @Final
    private GLShader shader;

    @ModifyConstant(method = "<init>()V", constant = @Constant(intValue = 2), require = 0, remap = false)
    private static int ausm$tripleBufferIndirectCommands(int original) {
        return AUSM_INDIRECT_BUFFER_SLOTS;
    }

    @ModifyConstant(method = "lambda$initVAOs$11", constant = @Constant(intValue = 28), remap = false)
    private int ausm$usePipelineStride(int original) {
        return NothiriumPipelineCompat.pipelineBlockStride(original);
    }

    @Inject(method = "setup", at = @At("HEAD"), require = 0, remap = false)
    private void ausm$captureCameraForStaticChunkOrigins(IRenderChunkProvider<RenderChunk> provider,
                                                         double cameraX, double cameraY, double cameraZ,
                                                         Frustum frustum, int frame,
                                                         CallbackInfo ci) {
        ausm$cameraX = cameraX;
        ausm$cameraY = cameraY;
        ausm$cameraZ = cameraZ;
        if (ausm$cameraOffsetUniform == Integer.MIN_VALUE) {
            ausm$cameraOffsetUniform = shader.getUniform("u_AusmCameraOffset");
            ausm$staticOriginsEnabled = ausm$cameraOffsetUniform >= 0;
        }
    }

    @Redirect(
            method = "record",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/renderlib/util/GLBuffer;putFloat(JF)V"
            ),
            require = 0,
            remap = false
    )
    private void ausm$storeStaticChunkOrigin(GLBuffer buffer, long offset, float cameraRelativeValue) {
        if (!ausm$staticOriginsEnabled) {
            buffer.putFloat(offset, cameraRelativeValue);
            return;
        }
        int axisOffset = (int) (offset % 12L);
        float staticValue;
        if (axisOffset == 0) {
            staticValue = (float) (cameraRelativeValue + ausm$cameraX);
        } else if (axisOffset == 4) {
            staticValue = (float) (cameraRelativeValue + ausm$cameraY);
        } else {
            staticValue = (float) (cameraRelativeValue + ausm$cameraZ);
        }
        buffer.putFloat(offset, staticValue);
    }

    /**
     * A section can lose its part between AbstractChunkRenderer's visibility
     * check and this renderer's indirect-command write (most visibly while a
     * shader-pack reload replaces terrain buffers).  An indirect command with
     * zero vertices is valid; dereferencing the vanished part is not.  Keep
     * the command slot so every subsequent command retains its list index.
     */
    @Redirect(
            method = "record",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/nothirium/api/renderer/IVBOPart;getCount()I"
            ),
            require = 0,
            remap = false
    )
    private int ausm$recordCountOrZero(IVBOPart part) {
        return part == null ? 0 : part.getCount();
    }

    @Redirect(
            method = "record",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/nothirium/api/renderer/IVBOPart;getFirst()I"
            ),
            require = 0,
            remap = false
    )
    private int ausm$recordFirstOrZero(IVBOPart part) {
        return part == null ? 0 : part.getFirst();
    }

    @Inject(
            method = "renderChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/renderlib/util/GLShader;use()V",
                    shift = At.Shift.AFTER
            ),
            require = 0,
            remap = false
    )
    private void ausm$uploadCameraOffset(ChunkRenderPass pass, CallbackInfo ci) {
        if (ausm$cameraOffsetUniform >= 0) {
            GL20.glUniform3f(
                    ausm$cameraOffsetUniform,
                    (float) ausm$cameraX,
                    (float) ausm$cameraY,
                    (float) ausm$cameraZ
            );
        }
    }

    @Inject(method = "renderChunks", at = @At("HEAD"), require = 0, remap = false)
    private void ausm$probeRenderChunksHead(ChunkRenderPass pass, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.beginShaderlessNothiriumTerrainFogGuard("gl43", pass);
        context.logNothiriumRenderProbe("gl43", "renderChunks-head", pass);
    }

    @Inject(method = "renderChunks", at = @At("RETURN"), require = 0, remap = false)
    private void ausm$probeRenderChunksReturn(ChunkRenderPass pass, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.logNothiriumRenderProbe("gl43", "renderChunks-return", pass);
        context.endShaderlessNothiriumTerrainFogGuard("gl43", pass);
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0, remap = false)
    private void ausm$probeRenderHead(ChunkRenderPass pass, CallbackInfo ci) {
        PipelineContext.getInstance().logNothiriumRenderProbe("gl43", "render-head", pass);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0, remap = false)
    private void ausm$probeRenderReturn(ChunkRenderPass pass, CallbackInfo ci) {
        PipelineContext.getInstance().logNothiriumRenderProbe("gl43", "render-return", pass);
    }

    @Redirect(
            method = "renderChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/nothirium/mc/util/FogUtil;setupFogFromGL(Lmeldexun/renderlib/util/GLShader;)V"
            ),
            require = 0,
            remap = false
    )
    private void ausm$setupShaderlessFogFromGL(GLShader shader) {
        NothiriumFogCompat.setupFogFromGL(shader, "gl43");
    }
}
