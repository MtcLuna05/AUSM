package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "gregtech.client.utils.BloomEffectUtil", remap = false)
public class LumenizedDisableBloomMixin {
    private static boolean logged;

    @Inject(method = "renderBloomBlockLayer", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ausm$disableOriginalBloom(RenderGlobal renderGlobal, BlockRenderLayer layer,
                                                  double partialTicks, int pass, Entity entity,
                                                  CallbackInfoReturnable<Integer> cir) {
        PipelineContext context = PipelineContext.getInstance();
        if (!logged) {
            logged = true;
            MainMod.LOGGER.info("[AUSMBloom] Disabled original Lumenized bloom renderer; AUSM owns BLOOM rendering.");
        }
        cir.setReturnValue(context.renderAusmBloomLayer(
                renderGlobal,
                partialTicks,
                pass,
                entity
        ));
    }
}
