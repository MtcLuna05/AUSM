package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.johni0702.minecraft.betterportals.client.render.PortalRenderer", remap = false)
public abstract class BetterPortalsPortalRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void ausm$beforeBetterPortalsRender(@Coerce Object portal, Vec3d pos, float partialTicks, CallbackInfo ci) {
        BetterPortalsCompat.pushPortalRendererState();
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void ausm$afterBetterPortalsRender(@Coerce Object portal, Vec3d pos, float partialTicks, CallbackInfo ci) {
        BetterPortalsCompat.popPortalRendererState();
        PipelineContext.getInstance().restoreActiveWorldPassAfterExternalShader();
    }
}
