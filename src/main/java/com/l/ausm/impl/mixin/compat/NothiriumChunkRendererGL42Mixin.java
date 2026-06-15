package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.ChunkRendererGL42", remap = false)
public class NothiriumChunkRendererGL42Mixin {
    @ModifyConstant(method = "lambda$initVAOs$8", constant = @Constant(intValue = 28), remap = false)
    private int ausm$usePipelineStride(int original) {
        return NothiriumPipelineCompat.pipelineBlockStride(original);
    }
}
