package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "cyb0124.curvy_pipes.client.VertexBuffer", remap = false)
public abstract class CurvyPipesVertexBufferMixin {
    @Inject(method = "draw()V", at = @At("HEAD"))
    private void ausm$prepareNativePipeBuffer(CallbackInfo ci) {
        // Native editing overlays can leave program 0 bound between pipe batches.
        PipelineContext.getInstance().prepareCurvyPipesDraw();
    }
}
