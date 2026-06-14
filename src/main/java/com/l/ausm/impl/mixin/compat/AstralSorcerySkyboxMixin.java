package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "hellfirepvp.astralsorcery.client.sky.RenderAstralSkybox", remap = false)
public class AstralSorcerySkyboxMixin {
    private static boolean logged;

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$skipAstralSun(CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldSuppressVanillaSunGeometry()) {
            ausm$logSuppression();
            ci.cancel();
        }
    }

    @Inject(method = "renderSolarEclipseSun", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$skipAstralSolarEclipseSun(@Coerce Object skyHandler, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldSuppressVanillaSunGeometry()) {
            ausm$logSuppression();
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$skipAstralMoon(CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldSuppressVanillaMoonGeometry()) {
            ausm$logSuppression();
            ci.cancel();
        }
    }

    private static void ausm$logSuppression() {
        if (!logged) {
            logged = true;
            MainMod.LOGGER.info("[AstralCompat] Disabled Astral Sorcery sun/moon quads; stars and constellations remain on Astral's normal path.");
        }
    }
}
