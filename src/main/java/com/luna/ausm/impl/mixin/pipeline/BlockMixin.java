package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.pipeline.pack.ShaderBlockLayerOverrides;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
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
        // Some Forge BlockFluidBase implementations do not advertise their
        // translucent layer consistently to asynchronous chunk compilers.
        // Keep every liquid on the vanilla translucent route, including
        // Astral Sorcery's liquidstarlight, in shadered and shaderless modes.
        if (MinecraftReflectionCompat.stateIsLiquidOrWater(state)) {
            cir.setReturnValue(layer == BlockRenderLayer.TRANSLUCENT);
            return;
        }
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
