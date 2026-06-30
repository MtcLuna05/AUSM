package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.pack.ShaderBlockLayerOverrides;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
    @Inject(method = "canRenderInLayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$thermalTankLayers(IBlockState state, BlockRenderLayer layer, CallbackInfoReturnable<Boolean> cir) {
        BlockRenderLayer shaderLayer = ShaderBlockLayerOverrides.layerFor(state);
        if (shaderLayer != null) {
            cir.setReturnValue(layer == shaderLayer);
            return;
        }

        if (!"cofh.thermalexpansion.block.storage.BlockTank".equals(getClass().getName())) {
            return;
        }

        cir.setReturnValue(layer == BlockRenderLayer.CUTOUT || layer == BlockRenderLayer.TRANSLUCENT);
    }
}
