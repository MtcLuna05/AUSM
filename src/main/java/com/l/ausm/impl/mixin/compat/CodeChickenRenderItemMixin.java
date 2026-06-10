package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.ProjectRedHaloRenderer;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "codechicken.lib.render.item.CCRenderItem", remap = false)
public class CodeChickenRenderItemMixin {
    @Inject(
            method = "func_180454_a(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ausm$replaceProjectRedCclItem(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        if (ProjectRedHaloRenderer.renderSolidProjectRedIlluminationItem(stack)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "func_184394_a(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            at = @At(value = "INVOKE", target = "Lcodechicken/lib/render/item/CCRenderItem;func_180454_a(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V"),
            remap = false
    )
    private void ausm$replaceTransformedProjectRedCclItem(@Coerce Object renderer, ItemStack stack, IBakedModel model,
                                                          ItemStack originalStack, IBakedModel originalModel,
                                                          ItemCameraTransforms.TransformType transformType, boolean leftHanded) {
        if (!ProjectRedHaloRenderer.renderSolidProjectRedIlluminationItem(stack)) {
            ProjectRedHaloRenderer.renderOriginalCclItem(renderer, stack, model);
        }
    }

    @Redirect(
            method = "func_191962_a(Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At(value = "INVOKE", target = "Lcodechicken/lib/render/item/CCRenderItem;func_180454_a(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V"),
            remap = false
    )
    private void ausm$replaceGuiProjectRedCclItem(@Coerce Object renderer, ItemStack stack, IBakedModel model,
                                                  ItemStack originalStack, int x, int y, IBakedModel originalModel) {
        if (!ProjectRedHaloRenderer.renderSolidProjectRedIlluminationItem(stack)) {
            ProjectRedHaloRenderer.renderOriginalCclItem(renderer, stack, model);
        }
    }
}
