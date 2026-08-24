package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents OpenBlocks from copying and rebinding the vanilla framebuffer while AUSM owns it. */
@Mixin(targets = "openblocks.client.renderer.SkyBlockRenderer$SkyCapture", remap = false)
public class OpenBlocksSkyCaptureMixin {
    @Inject(method = "run", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$skipVanillaFramebufferCapture(CallbackInfo ci) {
        if (PipelineContext.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
