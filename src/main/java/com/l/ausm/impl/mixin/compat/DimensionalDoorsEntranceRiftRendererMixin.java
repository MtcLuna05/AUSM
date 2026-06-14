package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.dimdev.dimdoors.client.TileEntityEntranceRiftRenderer", remap = false)
public class DimensionalDoorsEntranceRiftRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void ausm$prepareDimDoorsEntranceRift(@Coerce Object rift, double x, double y, double z,
                                                  float partialTicks, int destroyStage, float alpha,
                                                  CallbackInfo ci) {
        PipelineContext.getInstance().prepareExternalWorldOverlayRender();
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void ausm$finishDimDoorsEntranceRift(@Coerce Object rift, double x, double y, double z,
                                                 float partialTicks, int destroyStage, float alpha,
                                                 CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.finishExternalOverlayRender("Dimensional Doors entrance rift");
        context.restoreActiveWorldPassAfterExternalShader();
    }
}
