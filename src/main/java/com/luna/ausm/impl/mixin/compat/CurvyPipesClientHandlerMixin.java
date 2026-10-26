package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.pipeline.PipelineContext;
import java.nio.ByteBuffer;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "cyb0124.curvy_pipes.client.ClientHandler", remap = false)
public abstract class CurvyPipesClientHandlerMixin {
    @Unique
    private static WorldRenderingPhase ausm$previousPhase;

    @Inject(method = "renderLevel(Lnet/minecraft/util/BlockRenderLayer;)V", at = @At("HEAD"))
    private static void ausm$beginPipes(BlockRenderLayer layer, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.isActive()
                || (layer != BlockRenderLayer.CUTOUT_MIPPED && layer != BlockRenderLayer.TRANSLUCENT)) {
            return;
        }
        ausm$previousPhase = context.getPhase();
        context.beginPhase(layer == BlockRenderLayer.TRANSLUCENT
                ? WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT : WorldRenderingPhase.BLOCK_ENTITIES);
        context.prepareCurvyPipesDraw();
    }

    @Inject(method = "renderLevel(Lnet/minecraft/util/BlockRenderLayer;)V", at = @At("RETURN"))
    private static void ausm$endPipes(BlockRenderLayer layer, CallbackInfo ci) {
        if (ausm$previousPhase != null) {
            PipelineContext context = PipelineContext.getInstance();
            ausm$previousPhase = null;
            context.endPass();
            if (context.isShadowPassActive()) {
                context.restorePassAfterDistantHorizons();
            } else {
                context.restoreActiveWorldPassAfterExternalShader();
            }
        }
    }

    @Inject(method = "drawImmediate(ILjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V", at = @At("HEAD"), cancellable = true)
    private static void ausm$prepareImmediatePipes(int type, ByteBuffer vertices, ByteBuffer indices, CallbackInfo ci) {
        // Editing overlays and previews must never enter the light's depth map.
        if (PipelineContext.getInstance().isShadowPassActive()) {
            ci.cancel();
            return;
        }
        if (ausm$previousPhase != null && (type == 0 || type == 1)) {
            PipelineContext.getInstance().prepareCurvyPipesDraw();
        }
    }

    @Inject(method = "drawItem", at = @At("HEAD"), cancellable = true)
    private static void ausm$skipShadowPreviewItem(net.minecraft.item.ItemStack item, int light, CallbackInfo ci) {
        if (PipelineContext.getInstance().isShadowPassActive()) ci.cancel();
    }

    @Inject(method = "drawCrumble", at = @At("HEAD"), cancellable = true)
    private static void ausm$skipShadowCrumble(float percent, ByteBuffer vertices, ByteBuffer indices, CallbackInfo ci) {
        if (PipelineContext.getInstance().isShadowPassActive()) ci.cancel();
    }
}
