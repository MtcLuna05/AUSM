package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.compat.ProjectRedHaloRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "mrtjp.projectred.illumination.LampRenderer$", remap = false)
public class ProjectRedLampRendererMixin {
    @Redirect(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V",
            at = @At(value = "INVOKE", target = "Lmrtjp/projectred/core/RenderHalo$;prepareRenderState()V"),
            remap = false
    )
    private void ausm$beginTexturelessItemHalo(@Coerce Object halo) {
        ProjectRedHaloRenderer.beginImmediateItemHalo();
    }

    @Redirect(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V",
            at = @At(value = "INVOKE", target = "Lmrtjp/projectred/core/RenderHalo$;renderHalo(Lcodechicken/lib/vec/Cuboid6;ILcodechicken/lib/vec/Transformation;)V"),
            remap = false
    )
    private void ausm$renderTexturelessItemHalo(@Coerce Object halo, @Coerce Object cuboid, int color, @Coerce Object transformation) {
        ProjectRedHaloRenderer.renderImmediateHalo(cuboid, color, transformation);
    }

    @Redirect(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V",
            at = @At(value = "INVOKE", target = "Lmrtjp/projectred/core/RenderHalo$;restoreRenderState()V"),
            remap = false
    )
    private void ausm$endTexturelessItemHalo(@Coerce Object halo) {
        ProjectRedHaloRenderer.endImmediateHalo();
    }
}
