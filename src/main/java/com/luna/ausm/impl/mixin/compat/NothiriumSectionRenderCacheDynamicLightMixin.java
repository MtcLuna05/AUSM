package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.client.dynamic.DynamicLightManager;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.SectionRenderCache", remap = false)
public class NothiriumSectionRenderCacheDynamicLightMixin {
    @ModifyReturnValue(method = "func_175626_b", at = @At("RETURN"), remap = false)
    private int ausm$applyShaderlessDynamicCombinedLight(int original, BlockPos pos, int lightValue) {
        if (!DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            return original;
        }
        return DynamicLightManager.applyPackedLight(pos, original);
    }
}
