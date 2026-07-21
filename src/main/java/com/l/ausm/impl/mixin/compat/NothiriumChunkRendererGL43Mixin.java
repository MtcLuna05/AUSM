package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.NothiriumFogCompat;
import com.l.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import meldexun.nothirium.api.renderer.chunk.ChunkRenderPass;
import meldexun.renderlib.util.GLShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.ChunkRendererGL43", remap = false)
public class NothiriumChunkRendererGL43Mixin {
    @ModifyConstant(method = "lambda$initVAOs$11", constant = @Constant(intValue = 28), remap = false)
    private int ausm$usePipelineStride(int original) {
        return NothiriumPipelineCompat.pipelineBlockStride(original);
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
