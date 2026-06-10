package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.client.dynamic.DynamicLightManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldDynamicLightMixin {
    @Inject(method = "getLightFromNeighborsFor", at = @At("RETURN"), cancellable = true)
    private void ausm$applyShaderlessDynamicLight(EnumSkyBlock type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (type != EnumSkyBlock.BLOCK || !DynamicLightManager.active()) {
            return;
        }

        int dynamicLight = DynamicLightManager.lightAt(pos);
        if (dynamicLight > cir.getReturnValueI()) {
            cir.setReturnValue(dynamicLight);
        }
    }

    @Inject(method = "getLightFor", at = @At("RETURN"), cancellable = true)
    private void ausm$applyShaderlessDynamicLightFor(EnumSkyBlock type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (type != EnumSkyBlock.BLOCK || !DynamicLightManager.active()) {
            return;
        }

        int dynamicLight = DynamicLightManager.lightAt(pos);
        if (dynamicLight > cir.getReturnValueI()) {
            cir.setReturnValue(dynamicLight);
        }
    }

    @Inject(method = "getCombinedLight", at = @At("RETURN"), cancellable = true)
    private void ausm$applyShaderlessDynamicCombinedLight(BlockPos pos, int lightValue, CallbackInfoReturnable<Integer> cir) {
        int adjusted = DynamicLightManager.applyPackedLight(pos, cir.getReturnValueI());
        if (adjusted != cir.getReturnValueI()) {
            cir.setReturnValue(adjusted);
        }
    }
}
