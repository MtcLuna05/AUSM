package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.client.dynamic.DynamicLightManager;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkCache.class)
public class ChunkCacheDynamicLightMixin {
    @ModifyReturnValue(method = "getLightFor", at = @At("RETURN"))
    private int ausm$applyShaderlessDynamicLightFor(int original, EnumSkyBlock type, BlockPos pos) {
        if (type != EnumSkyBlock.BLOCK) {
            return original;
        }
        if (!DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            return original;
        }

        int dynamicLight = DynamicLightManager.lightAt(pos);
        return Math.max(original, dynamicLight);
    }

    @ModifyReturnValue(method = "getLightForExt", at = @At("RETURN"))
    private int ausm$applyShaderlessDynamicLightForExt(int original, EnumSkyBlock type, BlockPos pos) {
        if (type != EnumSkyBlock.BLOCK) {
            return original;
        }
        if (!DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            return original;
        }

        int dynamicLight = DynamicLightManager.lightAt(pos);
        return Math.max(original, dynamicLight);
    }

    @ModifyReturnValue(method = "getCombinedLight", at = @At("RETURN"))
    private int ausm$applyShaderlessDynamicCombinedLight(int original, BlockPos pos, int lightValue) {
        int repaired = PipelineContext.getInstance().repairShaderlessVoidWorldCombinedLight(pos, lightValue, original);
        if (!DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            return repaired;
        }
        return DynamicLightManager.applyPackedLight(pos, repaired);
    }
}
