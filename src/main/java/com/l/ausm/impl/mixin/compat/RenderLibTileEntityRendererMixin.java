package com.l.ausm.impl.mixin.compat;

import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * RenderLib renders tile entities as a batch. Binding the block-entity shader once
 * for that batch avoids rebinding FBO attachments and custom uniforms per tile entity.
 */
@Mixin(targets = "meldexun.renderlib.renderer.tileentity.TileEntityRenderer", remap = false)
public class RenderLibTileEntityRendererMixin {
    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$blockEntityBatchStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "renderTileEntities(F)V", at = @At("HEAD"))
    private void ausm$beginBlockEntityBatch(float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        WorldRenderingPhase phase = context.getPhase();
        boolean shouldBind = phase != WorldRenderingPhase.BLOCK_ENTITIES
                && phase != WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT;
        AUSM$blockEntityBatchStack.get().push(shouldBind);
        if (shouldBind) {
            context.beginPhase(context.blockEntityPhaseForCurrentForgePass());
        }
    }

    @Inject(method = "renderTileEntities(F)V", at = @At("RETURN"))
    private void ausm$endBlockEntityBatch(float partialTicks, CallbackInfo ci) {
        Deque<Boolean> stack = AUSM$blockEntityBatchStack.get();
        if (!stack.isEmpty() && stack.pop()) {
            PipelineContext.getInstance().endPass();
        }
    }
}
