package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;

public final class ShaderBlockLayerOverrides {
    private static final AtomicReference<Map<Block, BlockRenderLayer>> ACTIVE_OVERRIDES =
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
        if (!PipelineContext.getInstance().shouldApplyShaderBlockLayerOverrides()) {
            return null;
        }
        Block block = MinecraftReflectionCompat.blockFromState(state);
        if (block == null) {
            return null;
        }
        return ACTIVE_OVERRIDES.get().get(block);
    }
}
