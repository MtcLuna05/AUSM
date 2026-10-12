package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.client.DistantHorizonsCompat;
import com.luna.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restore after DH's HEAD hooks, immediately before vanilla submits the chunk layer. */
@Mixin(RenderGlobal.class)
public class DistantHorizonsTerrainStateMixin {
    @Inject(method = "func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174982_a(Lnet/minecraft/util/BlockRenderLayer;)V"),
            remap = false, require = 1)
    private void ausm$restoreTerrainAfterDistantHorizons(BlockRenderLayer layer, double partialTicks,
            int pass, Entity camera, CallbackInfoReturnable<Integer> callback) {
        if (layer == BlockRenderLayer.SOLID && DistantHorizonsCompat.installed()) {
            PipelineContext.getInstance().restorePassAfterDistantHorizons();
        }
    }
}
