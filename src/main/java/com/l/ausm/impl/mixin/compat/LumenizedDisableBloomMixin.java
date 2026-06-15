package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
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
            MainMod.LOGGER.info("[AUSMBloom] Replacing original Lumenized bloom pass while preserving wrapped terrain layers.");
        }
        int bloomRendered = context.renderAusmBloomLayer(
                renderGlobal,
                partialTicks,
                pass,
                entity
        );

        if (renderGlobal == null || layer == null || AusmBloomLayer.isBloomLayer(layer)) {
            cir.setReturnValue(bloomRendered);
            return;
        }

        cir.setReturnValue(renderGlobal.renderBlockLayer(layer, partialTicks, pass, entity));
    }
}
