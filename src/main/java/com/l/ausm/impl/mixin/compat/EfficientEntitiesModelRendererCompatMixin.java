package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.EfficientEntitiesChestCompat;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applied after Efficient Entities' ModelRenderer mixin. Its original helper
 * calls Thread.getStackTrace for every model part, producing thousands of
 * exceptions and stack arrays per minute. The dispatcher context gives the
 * same answer without walking the stack.
 */
@Mixin(value = ModelRenderer.class, priority = 900)
public abstract class EfficientEntitiesModelRendererCompatMixin {
    @Inject(
            method = "isCalledFromChestRenderer()Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void ausm$useDispatcherChestContext(CallbackInfoReturnable<Boolean> cir) {
        boolean chest = EfficientEntitiesChestCompat.isChestRenderActive();
        cir.setReturnValue(chest);
    }
}
