package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "lumien.randomthings.block.BlockBlockLuminousBase", remap = false)
public abstract class RandomThingsLuminousBlockMixin {
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return ausm$isRandomThingsTranslucentLuminous(state)
                ? layer == BlockRenderLayer.TRANSLUCENT
                : layer == BlockRenderLayer.SOLID;
    }

    @Unique
    private static boolean ausm$isRandomThingsTranslucentLuminous(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        ResourceLocation name = block != null ? MinecraftReflectionCompat.blockRegistryName(block) : null;
        return name != null
                && "randomthings".equals(MinecraftReflectionCompat.resourceNamespace(name))
                && "translucentluminousblock".equalsIgnoreCase(MinecraftReflectionCompat.resourcePath(name));
    }
}
