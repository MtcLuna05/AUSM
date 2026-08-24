package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OpenBlocks' sky block normally copies Minecraft's framebuffer before world
 * rendering, then uses a stencil-masked fullscreen draw in its tile renderer.
 * Neither operation is valid while AUSM owns the G-buffer.  Returning false
 * retains OpenBlocks' ordinary baked-model fallback without the foreign pass.
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
