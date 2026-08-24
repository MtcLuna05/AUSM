package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.ellpeck.actuallyadditions.mod.util.AssetUtil", remap = false)
public class ActuallyAdditionsAssetUtilMixin {
    @Inject(method = "renderLaser", at = @At("HEAD"))
    private static void ausm$beginActuallyAdditionsLaser(double firstX, double firstY, double firstZ,
                                                         double secondX, double secondY, double secondZ,
                                                         double rotationTime, float alpha, double beamSize,
                                                         float[] color, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.beginPhase(WorldRenderingPhase.BEACON_BEAM);
        context.prepareUntexturedEmissiveWorldRenderState();
    }

    @Inject(method = "renderLaser", at = @At("RETURN"))
    private static void ausm$endActuallyAdditionsLaser(double firstX, double firstY, double firstZ,
                                                       double secondX, double secondY, double secondZ,
                                                       double rotationTime, float alpha, double beamSize,
                                                       float[] color, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.finishExternalWorldOverlayRender("Actually Additions laser");
    }
}
