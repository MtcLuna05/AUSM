package com.l.ausm.impl.mixin.compat;

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

        }

        if (context.isActive()) {
            // AUSM renders the native BLOOM layer from its world-pass hook while the
            // deferred terrain depth is current. GregTech invokes this callback later,
            // after the pipeline frame has been presented and its depth is unavailable.
            cir.setReturnValue(0);
            return;
        }

        if (!AusmBloomLayer.shouldUseNativeHook()) {
            if (renderGlobal == null || layer == null) {
                cir.setReturnValue(0);
                return;
            }
            if (AusmBloomLayer.isBloomLayer(layer)) {
                int rendered = context.renderShaderlessVisibleBloomLayerFromWorldPass((float) partialTicks, pass);
                cir.setReturnValue(rendered);
                return;
            }
            cir.setReturnValue(context.renderWorldBlockLayer(renderGlobal, layer, partialTicks, pass, entity));
            return;
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

        cir.setReturnValue(context.renderWorldBlockLayer(renderGlobal, layer, partialTicks, pass, entity));
    }
}
