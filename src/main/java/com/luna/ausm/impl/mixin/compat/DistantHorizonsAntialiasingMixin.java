package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.ShadedDistantHorizonsRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native TAA reads DH's native textures, which are unused by our framebuffer override. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.postProcessing.antialiasing.GlDhTaaRenderer", remap = false)
public class DistantHorizonsAntialiasingMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 1)
    private void ausm$preserveShaderpackTerrain(CallbackInfo callback) {
        if (ShadedDistantHorizonsRendering.shaderpackOverrideActive) {
            callback.cancel();
        }
    }
}
