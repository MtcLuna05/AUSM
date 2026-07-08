package com.l.ausm.impl.pipeline.pack;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class ShaderBlockLayerOverrides {
    private static final AtomicReference<Map<net.minecraft.block.Block, BlockRenderLayer>> ACTIVE_OVERRIDES =
            new AtomicReference<>(Map.of());

    private ShaderBlockLayerOverrides() {
    }

    public static void install(ShaderBlockIdMap.BlockIdRules rules) {
        if (rules == null || rules.layerOverrides().isEmpty()) {
            clear();
            return;
        }
        ACTIVE_OVERRIDES.set(rules.layerOverrides());
    }

    public static void clear() {
        ACTIVE_OVERRIDES.set(Map.of());
    }

    public static BlockRenderLayer layerFor(IBlockState state) {
        net.minecraft.block.Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        if (block == null) {
            return null;
        }
        return ACTIVE_OVERRIDES.get().get(block);
    }
}
