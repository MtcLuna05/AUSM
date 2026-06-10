package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.ProjectRedHaloRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "mrtjp.projectred.illumination.LightFactory$$anon$1", remap = false)
public class ProjectRedLightFactoryItemRendererMixin {
    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ausm$replaceProjectRedLightFactoryItem(ItemStack stack, ItemCameraTransforms.TransformType transformType, CallbackInfo ci) {
        ci.cancel();
        ProjectRedHaloRenderer.renderSolidProjectRedRendererItem(stack, "LightFactory");
    }
}
