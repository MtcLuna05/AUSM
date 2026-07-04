package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.client.dynamic.DynamicLightManager;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkCache.class)
public class ChunkCacheDynamicLightMixin {
    @Inject(method = "getLightFor", at = @At("RETURN"), cancellable = true)
    private void ausm$applyShaderlessDynamicLightFor(EnumSkyBlock type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (type != EnumSkyBlock.BLOCK) {
            return;
        }
        if (!DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            DynamicLightManager.logShaderlessLightQueryProbe("chunk-getLightFor-skip", pos, cir.getReturnValueI(), cir.getReturnValueI(), false);
            return;
        }

        int before = cir.getReturnValueI();
        int dynamicLight = DynamicLightManager.lightAt(pos);
        if (dynamicLight > before) {
            cir.setReturnValue(dynamicLight);
            DynamicLightManager.logShaderlessLightQueryProbe("chunk-getLightFor-apply", pos, before, dynamicLight, true);
        } else {
            DynamicLightManager.logShaderlessLightQueryProbe("chunk-getLightFor-keep", pos, before, before, false);
        }
    }

    @Inject(method = "getLightForExt", at = @At("RETURN"), cancellable = true)
    private void ausm$applyShaderlessDynamicLightForExt(EnumSkyBlock type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (type != EnumSkyBlock.BLOCK) {
            return;
        }
        if (!DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            DynamicLightManager.logShaderlessLightQueryProbe("chunk-getLightForExt-skip", pos, cir.getReturnValueI(), cir.getReturnValueI(), false);
            return;
        }

        int before = cir.getReturnValueI();
        int dynamicLight = DynamicLightManager.lightAt(pos);
        if (dynamicLight > before) {
            cir.setReturnValue(dynamicLight);
            DynamicLightManager.logShaderlessLightQueryProbe("chunk-getLightForExt-apply", pos, before, dynamicLight, true);
        } else {
            DynamicLightManager.logShaderlessLightQueryProbe("chunk-getLightForExt-keep", pos, before, before, false);
        }
    }

    @Inject(method = "getCombinedLight", at = @At("RETURN"), cancellable = true)
    private void ausm$applyShaderlessDynamicCombinedLight(BlockPos pos, int lightValue, CallbackInfoReturnable<Integer> cir) {
        int beforeRepair = cir.getReturnValueI();
        int repaired = PipelineContext.getInstance().repairShaderlessVoidWorldCombinedLight(pos, lightValue, beforeRepair);
        if (repaired != beforeRepair) {
            cir.setReturnValue(repaired);
        }
        if (!DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            DynamicLightManager.logShaderlessLightQueryProbe("chunk-combined-skip", pos, cir.getReturnValueI(), cir.getReturnValueI(), false);
            return;
        }

        int before = cir.getReturnValueI();
        int adjusted = DynamicLightManager.applyPackedLight(pos, before);
        if (adjusted != before) {
            cir.setReturnValue(adjusted);
            DynamicLightManager.logShaderlessLightQueryProbe("chunk-combined-apply", pos, before, adjusted, true);
        } else {
            DynamicLightManager.logShaderlessLightQueryProbe("chunk-combined-keep", pos, before, before, false);
        }
    }
}
