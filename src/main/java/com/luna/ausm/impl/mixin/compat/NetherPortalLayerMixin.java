package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import net.minecraft.block.BlockPortal;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockPortal.class)
public class NetherPortalLayerMixin {
    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private void ausm$renderPortalOutsideWaterPass(CallbackInfoReturnable<BlockRenderLayer> cir) {
        if (PipelineContext.getInstance().isActive()) {
            cir.setReturnValue(BlockRenderLayer.CUTOUT);
        }
    }
}
