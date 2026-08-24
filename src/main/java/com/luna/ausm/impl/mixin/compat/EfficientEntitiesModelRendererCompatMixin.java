package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.compat.EfficientEntitiesChestCompat;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applied after Efficient Entities' ModelRenderer mixin. Its original helper
 * calls Thread.getStackTrace for every model part, producing thousands of
 * exceptions and stack arrays per minute. The dispatcher context gives the
 * same answer without walking the stack. While AUSM shaders are active it also
 * returns true so Efficient Entities leaves model submission to vanilla; its
 * global persistent batches otherwise alias geometry between shader phases.
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
        cir.setReturnValue(EfficientEntitiesChestCompat.shouldUseVanillaModelRenderer(
                PipelineContext.getInstance().isActive()));
    }
}
