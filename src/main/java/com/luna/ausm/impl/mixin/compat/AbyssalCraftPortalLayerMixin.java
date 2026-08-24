package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
        targets = {
                "com.shinoow.abyssalcraft.common.blocks.BlockAbyssPortal",
                "com.shinoow.abyssalcraft.common.blocks.BlockDreadlandsPortal",
                "com.shinoow.abyssalcraft.common.blocks.BlockOmotholPortal"
        },
        remap = false
)
public class AbyssalCraftPortalLayerMixin {
    @Inject(method = "func_180664_k", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$renderAbyssalPortalOutsideWaterPass(CallbackInfoReturnable<BlockRenderLayer> cir) {
        if (PipelineContext.getInstance().isActive()) {
            cir.setReturnValue(BlockRenderLayer.CUTOUT);
        }
    }
}
