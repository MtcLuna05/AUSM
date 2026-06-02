package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Binds the OptiFine block-entity program while tile entities render.
 */
@Mixin(TileEntityRendererDispatcher.class)
public class TileEntityRendererDispatcherMixin {

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At("HEAD"))
    private void onRenderTileEntityHead(TileEntity tileEntity, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.BLOCK_ENTITIES);
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At("RETURN"))
    private void onRenderTileEntityReturn(TileEntity tileEntity, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }
}
