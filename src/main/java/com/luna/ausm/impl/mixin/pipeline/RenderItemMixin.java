package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.pipeline.PipelineContext;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Binds the OptiFine armor-glint program for enchanted item glint rendering.
 */
@Mixin(RenderItem.class)
public class RenderItemMixin {
    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$itemPhaseStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$guiItemStateStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$guiBuiltInStateStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$guiForgeLitStateStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$renderedItemStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$glintPhaseStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("HEAD"))
    private void ausm$onRenderItemHead(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isRenderingGuiItemContext()) {
            AUSM$guiItemStateStack.get().push(context.beginGuiItemStateScope());
            AUSM$renderedItemStack.get().push(false);
            AUSM$itemPhaseStack.get().push(false);
            return;
        }
        AUSM$guiItemStateStack.get().push(false);
        context.beginRenderedItem(stack);
        AUSM$renderedItemStack.get().push(true);
        context.prepareHandItemRenderState();
        AUSM$itemPhaseStack.get().push(context.beginItemRenderPhase());
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("RETURN"))
    private void ausm$onRenderItemReturn(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        Deque<Boolean> stackState = AUSM$itemPhaseStack.get();
        if (!stackState.isEmpty() && stackState.pop()) {
            context.endPass();
        }
        Deque<Boolean> renderedItemState = AUSM$renderedItemStack.get();
        if (!renderedItemState.isEmpty() && renderedItemState.pop()) {
            context.endRenderedItem();
        }
        Deque<Boolean> guiState = AUSM$guiItemStateStack.get();
        if (!guiState.isEmpty() && guiState.pop()) {
            context.endGuiItemStateScope();
        }
    }

    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/tileentity/TileEntityItemStackRenderer;renderByItem(Lnet/minecraft/item/ItemStack;)V"
            )
    )
    private void ausm$beforeBuiltInItemRenderer(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isRenderingGuiItemContext()) {
            AUSM$guiBuiltInStateStack.get().push(context.beginGuiBuiltInItemStateScope());
        } else {
            context.prepareHandItemDrawState("built_in_item_renderer");
        }
    }

    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/tileentity/TileEntityItemStackRenderer;renderByItem(Lnet/minecraft/item/ItemStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void ausm$afterBuiltInItemRenderer(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        Deque<Boolean> state = AUSM$guiBuiltInStateStack.get();
        if (!state.isEmpty() && state.pop()) {
            PipelineContext.getInstance().endGuiBuiltInItemStateScope();
        }
    }

    @Inject(
            method = "renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;renderLitItem(Lnet/minecraft/client/renderer/RenderItem;Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V"
            )
    )
    private void ausm$beforeForgeLitItemDraw(IBakedModel model, int color, ItemStack stack, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.isRenderingGuiItemContext()) {
            context.prepareHandItemDrawState("forge_lit_item");
        } else {
            // renderEffect establishes the glint's GL_EQUAL depth mask before
            // invoking renderModel. Do not replace that state with the normal
            // GUI base-item scope (GL_LEQUAL + depth writes), or transparent
            // pixels in the baked quad become a slot-sized glint rectangle.
            // RenderItem's two-argument renderModel overload is the common
            // endpoint for vanilla glint and private copies such as HEI's.
            // It always delegates here with ItemStack.EMPTY, while ordinary
            // item submissions retain their real stack.  Use that vanilla
            // invariant instead of an optional mod-specific marker.
            boolean renderingGlint = stack.isEmpty() || !AUSM$glintPhaseStack.get().isEmpty();
            AUSM$guiForgeLitStateStack.get().push(
                    !renderingGlint && context.beginGuiItemStateScope()
            );
        }
    }

    @Inject(
            method = "renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;renderLitItem(Lnet/minecraft/client/renderer/RenderItem;Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void ausm$afterForgeLitItemDraw(IBakedModel model, int color, ItemStack stack, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.isRenderingGuiItemContext()) {
            return;
        }
        Deque<Boolean> state = AUSM$guiForgeLitStateStack.get();
        if (!state.isEmpty() && state.pop()) {
            context.endGuiItemStateScope();
        }
    }

    @Inject(
            method = "renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;draw()V"
            )
    )
    private void ausm$beforeStandardItemDraw(IBakedModel model, int color, ItemStack stack, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.isRenderingGuiItemContext()) {
            context.prepareHandItemDrawState("standard_item");
        } else if (!stack.isEmpty() && AUSM$glintPhaseStack.get().isEmpty()) {
            context.prepareGuiItemBaseDrawState();
        }
    }

    @Inject(method = "renderEffect(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("HEAD"))
    private void onRenderEffectHead(IBakedModel model, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isRenderingGuiItemContext()) {
            AUSM$glintPhaseStack.get().push(false);
            return;
        }
        AUSM$glintPhaseStack.get().push(context.beginItemGlintPhase());
    }

    @Inject(
            method = "renderEffect(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderItem;renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;I)V"
            )
    )
    private void ausm$beforeItemGlintModelDraw(IBakedModel model, CallbackInfo ci) {
        PipelineContext.getInstance().prepareItemGlintDrawState();
    }

    @Inject(method = "renderEffect(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("RETURN"))
    private void onRenderEffectReturn(IBakedModel model, CallbackInfo ci) {
        Deque<Boolean> stackState = AUSM$glintPhaseStack.get();
        if (!stackState.isEmpty() && stackState.pop()) {
            PipelineContext.getInstance().endItemGlintPhase();
        }
    }
}
