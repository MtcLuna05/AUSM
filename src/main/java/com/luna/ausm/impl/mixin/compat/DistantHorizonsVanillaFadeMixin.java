package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DH's vanilla fade expects a combined framebuffer; AUSM composes LODs after final. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public class DistantHorizonsVanillaFadeMixin {
    @Inject(method = {"renderFadeOpaque", "renderFadeTransparent"}, at = @At("HEAD"),
            cancellable = true, require = 1)
    private void ausm$keepShaderGbuffersOutOfVanillaFade(CallbackInfo callback) {
        if (PipelineContext.getInstance().isPipelineActive()) {
            callback.cancel();
        }
    }
}
