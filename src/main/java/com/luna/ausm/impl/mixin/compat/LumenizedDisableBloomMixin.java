package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
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
            // GregTech replaces EntityRenderer's fourth vanilla block-layer call with
            // this helper. That call is TRANSLUCENT, not an additional bloom draw, so
            // dropping it makes every fluid disappear. Enter the water phase here
            // because EntityRenderer's ordinal-based translucent hook no longer sees
            // the replaced RenderGlobal invocation.
            if (layer == BlockRenderLayer.TRANSLUCENT && !context.shouldBypassWorldPassRendering()) {
                context.beginTranslucents();
                context.applyWaterRenderState();
                context.applyTerrainCulling(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
                context.beginPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
                cir.setReturnValue(context.renderWorldBlockLayer(renderGlobal, layer, partialTicks, pass, entity));
                return;
            }

            // AUSM submits its own native bloom layer while the deferred terrain
            // depth is current. Suppress only GregTech's extra bloom callback.
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
