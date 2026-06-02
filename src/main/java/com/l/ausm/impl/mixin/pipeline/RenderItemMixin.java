package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Binds the OptiFine armor-glint program for enchanted item glint rendering.
 */
@Mixin(RenderItem.class)
public class RenderItemMixin {
    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$itemPhaseStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$glintPhaseStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("HEAD"))
    private void ausm$onRenderItemHead(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        AUSM$itemPhaseStack.get().push(PipelineContext.getInstance().beginItemRenderPhase());
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("RETURN"))
    private void ausm$onRenderItemReturn(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        Deque<Boolean> stackState = AUSM$itemPhaseStack.get();
        if (!stackState.isEmpty() && stackState.pop()) {
            PipelineContext.getInstance().endPass();
        }
    }

    @Inject(method = "renderEffect(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("HEAD"))
    private void onRenderEffectHead(IBakedModel model, CallbackInfo ci) {
        AUSM$glintPhaseStack.get().push(PipelineContext.getInstance().beginItemGlintPhase());
    }

    @Inject(method = "renderEffect(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("RETURN"))
    private void onRenderEffectReturn(IBakedModel model, CallbackInfo ci) {
        Deque<Boolean> stackState = AUSM$glintPhaseStack.get();
        if (!stackState.isEmpty() && stackState.pop()) {
            PipelineContext.getInstance().endPass();
        }
    }
}
