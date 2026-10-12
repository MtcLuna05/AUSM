package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;

public final class ShaderBlockLayerOverrides {
    private static final Object NO_OVERRIDE = new Object();
    private static final AtomicReference<OverrideSnapshot> ACTIVE_OVERRIDES =
            new AtomicReference<>(new OverrideSnapshot(Map.of()));
    private static final ThreadLocal<OverrideCache> THREAD_CACHE = ThreadLocal.withInitial(OverrideCache::new);

    private ShaderBlockLayerOverrides() {
    }

    public static void install(ShaderBlockIdMap.BlockIdRules rules) {
        if (rules == null || rules.layerOverrides().isEmpty()) {
            clear();
            return;
        }
        ACTIVE_OVERRIDES.set(new OverrideSnapshot(rules.layerOverrides()));
    }

    public static void clear() {
        ACTIVE_OVERRIDES.set(new OverrideSnapshot(Map.of()));
    }

    public static BlockRenderLayer layerFor(IBlockState state) {
        if (!PipelineContext.getInstance().shouldApplyShaderBlockLayerOverrides()) {
            return null;
        }
        OverrideSnapshot snapshot = ACTIVE_OVERRIDES.get();
        OverrideCache cache = THREAD_CACHE.get();
        if (cache.snapshot != snapshot) {
            cache.snapshot = snapshot;
            cache.byState.clear();
        }
        Object cached = cache.byState.get(state);
        if (cached != null) {
            return cached == NO_OVERRIDE ? null : (BlockRenderLayer) cached;
        }
        Block block = MinecraftReflectionCompat.blockFromState(state);
        if (block == null) {
            cache.put(state, NO_OVERRIDE);
            return null;
        }
        BlockRenderLayer layer = snapshot.layers.get(block);
        cache.put(state, layer != null ? layer : NO_OVERRIDE);
        return layer;
    }

    private record OverrideSnapshot(Map<Block, BlockRenderLayer> layers) { }

    private static final class OverrideCache {
        private OverrideSnapshot snapshot;
        private final IdentityHashMap<IBlockState, Object> byState = new IdentityHashMap<>();

        private void put(IBlockState state, Object value) {
            if (byState.size() >= 4096) {
                byState.clear();
            }
            byState.put(state, value);
        }
    }
}
