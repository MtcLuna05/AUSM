package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.tileentity.TileEntityBeacon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Binds the OptiFine beacon-beam program while beacon beams render.
 */
@Mixin(TileEntityBeaconRenderer.class)
public class TileEntityBeaconRendererMixin {

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFIF)V", at = @At("HEAD"))
    private void onRenderBeaconHead(TileEntityBeacon beacon, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.BEACON_BEAM);
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFIF)V", at = @At("RETURN"))
    private void onRenderBeaconReturn(TileEntityBeacon beacon, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }
}
