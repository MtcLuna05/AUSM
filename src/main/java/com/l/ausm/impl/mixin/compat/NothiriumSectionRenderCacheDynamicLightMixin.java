package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.client.dynamic.DynamicLightManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.SectionRenderCache", remap = false)
public class NothiriumSectionRenderCacheDynamicLightMixin {
    @Inject(method = "func_175626_b", at = @At("RETURN"), cancellable = true, remap = false)
    private void ausm$applyShaderlessDynamicCombinedLight(BlockPos pos, int lightValue, CallbackInfoReturnable<Integer> cir) {
        if (!DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            return;
        }

        int before = cir.getReturnValueI();
        int adjusted = DynamicLightManager.applyPackedLight(pos, before);
        if (adjusted != before) {
            cir.setReturnValue(adjusted);
        }
    }
}
