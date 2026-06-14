package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegionRenderCacheBuilder.class)
public class AusmBloomRegionRenderCacheBuilderMixin {
    @Shadow
    @Final
    private BufferBuilder[] worldRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ausm$initializeBloomLayer(CallbackInfo ci) {
        AusmBloomLayer.ensureRegionBuffer(worldRenderers);
    }
}
