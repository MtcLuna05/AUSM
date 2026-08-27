package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OpenBlocks' stencil-masked projection is incompatible with AUSM's shader
 * MRT ownership. Preserve the renderer's baked-model fallback while shaders
 * are active instead.
 */
@Mixin(targets = "openblocks.client.renderer.SkyBlockRenderer", remap = false)
public class OpenBlocksSkyBlockRendererMixin {
    @Inject(method = "hasSkyTexture", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$useNormalModelWhileShadered(CallbackInfoReturnable<Boolean> cir) {
        if (PipelineContext.getInstance().isActive()) {
            cir.setReturnValue(false);
        }
    }
}
