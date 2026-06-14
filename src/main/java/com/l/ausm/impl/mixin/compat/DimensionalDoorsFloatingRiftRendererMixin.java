package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.dimdev.dimdoors.client.TileEntityFloatingRiftRenderer", remap = false)
public class DimensionalDoorsFloatingRiftRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void ausm$prepareDimDoorsFloatingRift(@Coerce Object rift, double x, double y, double z,
                                                  float partialTicks, int destroyStage, float alpha,
                                                  CallbackInfo ci) {
        PipelineContext.getInstance().prepareExternalWorldOverlayRender();
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void ausm$finishDimDoorsFloatingRift(@Coerce Object rift, double x, double y, double z,
                                                 float partialTicks, int destroyStage, float alpha,
                                                 CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.finishExternalOverlayRender("Dimensional Doors floating rift");
        context.restoreActiveWorldPassAfterExternalShader();
    }
}
