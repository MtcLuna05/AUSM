package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.johni0702.minecraft.view.impl.client.ClientWorldsManagerImpl", remap = false)
public abstract class BetterPortalsClientWorldsManagerMixin {
    @Inject(method = "makeMainView", at = @At("RETURN"), remap = false)
    private void ausm$afterBetterPortalsMainViewSwap(@Coerce Object newMainView, CallbackInfo ci) {
        PipelineContext.getInstance().handleBetterPortalsMainViewSwap();
    }
}
