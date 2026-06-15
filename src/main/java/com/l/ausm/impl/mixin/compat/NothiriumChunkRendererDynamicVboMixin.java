package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.ChunkRendererDynamicVbo", remap = false)
public class NothiriumChunkRendererDynamicVboMixin {
    @ModifyArg(
            method = "lambda$new$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/nothirium/opengl/DynamicVBO;<init>(III)V",
                    remap = false
            ),
            index = 0,
            remap = false
    )
    private static int ausm$usePipelineVertexSize(int original) {
        return NothiriumPipelineCompat.pipelineBlockStride(original);
    }
}
