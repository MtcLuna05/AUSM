package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "gregtech.client.utils.BloomEffectUtil", remap = false)
public class LumenizedBloomBlockLayerMixin {
    @Inject(method = "renderBloomBlockLayer", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ausm$renderStandaloneBloom(RenderGlobal renderGlobal, BlockRenderLayer layer,
                                                   double partialTicks, int pass, Entity entity,
                                                   CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(PipelineContext.getInstance().renderLumenizedBloomStandalone(
                renderGlobal,
                partialTicks,
                pass,
                entity
        ));
    }
}
