package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import meldexun.nothirium.api.renderer.IVBOPart;
import meldexun.nothirium.api.renderer.chunk.ChunkRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.ChunkRendererGL15", remap = false)
public class NothiriumChunkRendererGL15Mixin {
    @ModifyConstant(method = "setupAttributePointers", constant = @Constant(intValue = 28), remap = false)
    private int ausm$usePipelineStride(int original) {
        return NothiriumPipelineCompat.pipelineBlockStride(original);
    }

    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lmeldexun/nothirium/api/renderer/IVBOPart;getFirst()I"),
            require = 0,
            remap = false
    )
    private int ausm$drawFirstOrZero(IVBOPart part) {
        return part == null ? 0 : part.getFirst();
    }

    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lmeldexun/nothirium/api/renderer/IVBOPart;getCount()I"),
            require = 0,
            remap = false
    )
    private int ausm$drawCountOrZero(IVBOPart part) {
        return part == null ? 0 : part.getCount();
    }

    @Inject(method = "renderChunks", at = @At("HEAD"), require = 0, remap = false)
    private void ausm$probeRenderChunksHead(ChunkRenderPass pass, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.beginShaderlessNothiriumTerrainFogGuard("gl15", pass);
        context.logNothiriumRenderProbe("gl15", "renderChunks-head", pass);
    }

    @Inject(method = "renderChunks", at = @At("RETURN"), require = 0, remap = false)
    private void ausm$probeRenderChunksReturn(ChunkRenderPass pass, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.logNothiriumRenderProbe("gl15", "renderChunks-return", pass);
        context.endShaderlessNothiriumTerrainFogGuard("gl15", pass);
    }
}
