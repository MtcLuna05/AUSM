package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native far fade mixes in Minecraft's previous presentation, including its HUD. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.postProcessing.fade.GlDhFarFadeRenderer", remap = false)
public class DistantHorizonsFarFadeMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 1)
    private void ausm$keepPreviousPresentationOutOfLods(CallbackInfo callback) {
        if (PipelineContext.getInstance().isPipelineActive()) {
            callback.cancel();
        }
    }
}
