package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "lumien.randomthings.block.BlockBlockLuminousBase", remap = false)
public abstract class RandomThingsLuminousBlockMixin {
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID || AusmBloomLayer.isBloomLayer(layer);
    }
}
