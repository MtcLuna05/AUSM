package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.compat.ProjectRedHaloRenderer;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
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
    private static final ThreadLocal<Deque<Boolean>> AUSM$guiItemStateStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$guiBuiltInStateStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$guiForgeLitStateStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$renderedItemStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$glintPhaseStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            at = @At("HEAD")
    )
    private void ausm$auditProjectRedHeldItem(ItemStack stack, EntityLivingBase entity,
                                              ItemCameraTransforms.TransformType transformType,
                                              boolean leftHanded, CallbackInfo ci) {
        ProjectRedHaloRenderer.auditRenderItem(stack, "renderItem_entity", transformType);
    }

    @Inject(
            method = "renderItemModel(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            at = @At("HEAD")
    )
    private void ausm$auditProjectRedItemModel(ItemStack stack, IBakedModel model,
                                               ItemCameraTransforms.TransformType transformType,
                                               boolean leftHanded, CallbackInfo ci) {
        ProjectRedHaloRenderer.auditRenderItem(stack, "renderItemModel", transformType);
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("HEAD"))
    private void ausm$onRenderItemHead(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        ProjectRedHaloRenderer.auditRenderItem(stack, "renderItem_model", model != null ? model.getClass().getName() : null);
        PipelineContext context = PipelineContext.getInstance();
        if (context.isRenderingGuiScreen()) {
            context.probeGuiModelState("item-head");
            AUSM$guiItemStateStack.get().push(context.beginGuiItemStateScope());
            context.beginGuiItemModelProbe(stack, model);
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
        if (context.isRenderingGuiScreen()) {
            context.probeGuiModelState("item-return");
            context.endGuiItemModelProbe();
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
        if (context.isRenderingGuiScreen()) {
            context.probeGuiModelState("before-built-in-item-renderer");
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
        if (!context.isRenderingGuiScreen()) {
            context.prepareHandItemDrawState("forge_lit_item");
        } else {
            AUSM$guiForgeLitStateStack.get().push(context.beginGuiItemStateScope());
            context.beginGuiItemModelProbe(stack, model);
            context.probeGuiItemModel("forge-lit-item", stack, model);
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
        if (!context.isRenderingGuiScreen()) {
            return;
        }
        context.endGuiItemModelProbe();
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
        if (!context.isRenderingGuiScreen()) {
            context.prepareHandItemDrawState("standard_item");
        }
    }

    @Inject(method = "renderEffect(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("HEAD"))
    private void onRenderEffectHead(IBakedModel model, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isRenderingGuiScreen()) {
            AUSM$glintPhaseStack.get().push(false);
            return;
        }
        AUSM$glintPhaseStack.get().push(context.beginItemGlintPhase());
    }

    @Inject(method = "renderEffect(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("RETURN"))
    private void onRenderEffectReturn(IBakedModel model, CallbackInfo ci) {
        Deque<Boolean> stackState = AUSM$glintPhaseStack.get();
        if (!stackState.isEmpty() && stackState.pop()) {
            PipelineContext.getInstance().endPass();
        }
    }
}
