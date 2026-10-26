package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.client.dynamic.DynamicLightManager;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.DynamicLightNoopWorldEventListener;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public class WorldDynamicLightMixin {
    @Inject(method = "notifyBlockUpdate", at = @At("RETURN"))
    private void ausm$trackPlacedColoredLight(BlockPos pos, IBlockState oldState,
                                               IBlockState newState, int flags,
                                               CallbackInfo ci) {
        World world = (World) (Object) this;
        if (world.isRemote) {
            PipelineContext.getInstance().refreshSyntheticLightCandidate(world, pos);
        }
    }

    @Redirect(
            method = "notifyBlockUpdate",
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;")
    )
    private Object ausm$safeNotifyBlockUpdateListenerGet(List<?> listeners, int index) {
        if (index < 0 || index >= listeners.size()) {
            return DynamicLightNoopWorldEventListener.INSTANCE;
        }
        return listeners.get(index);
    }


    /**
     * Chunk render caches already receive dynamic light while terrain is baked. Live
     * renderers (entities, block entities, and particles) query the client World
     * directly instead, so mirror the same adjustment on that rendering path.
     */
    @ModifyReturnValue(method = "getLightFor", at = @At("RETURN"))
    private int ausm$applyShaderlessDynamicWorldBlockLight(int original, EnumSkyBlock type, BlockPos pos) {
        if (type != EnumSkyBlock.BLOCK || !DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            return original;
        }

        int dynamicLight = DynamicLightManager.lightAt(pos);
        return Math.max(original, dynamicLight);
    }

    @ModifyReturnValue(method = "getCombinedLight", at = @At("RETURN"))
    private int ausm$applyShaderlessDynamicWorldCombinedLight(int original, BlockPos pos, int lightValue) {
        if (!DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            return original;
        }
        return DynamicLightManager.applyPackedLight(pos, original);
    }

}
