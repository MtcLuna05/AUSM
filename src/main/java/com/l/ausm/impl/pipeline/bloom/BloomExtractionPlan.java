package com.l.ausm.impl.pipeline.bloom;

import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.util.BlockRenderLayer;

/** Shared immutable plan for the terrain passes used by shaderless bloom extraction. */
public final class BloomExtractionPlan {
    private static final BlockRenderLayer[] TERRAIN_LAYERS = {
            BlockRenderLayer.SOLID,
            BlockRenderLayer.CUTOUT_MIPPED,
            BlockRenderLayer.CUTOUT,
            BlockRenderLayer.TRANSLUCENT
    };

    private BloomExtractionPlan() {
    }

    public static BlockRenderLayer[] terrainLayers() {
        return TERRAIN_LAYERS;
    }

    public static WorldRenderingPhase phaseFor(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.SOLID) {
            return WorldRenderingPhase.TERRAIN_SOLID;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return WorldRenderingPhase.TERRAIN_CUTOUT;
        }
        return WorldRenderingPhase.TERRAIN_TRANSLUCENT;
    }

    public static long metadataKey(int dimension, int sectionX, int sectionY, int sectionZ, BlockRenderLayer layer) {
        return ((long) (dimension & 0x3FF) << 54)
                | ((long) (layer.ordinal() & 0xF) << 50)
                | ((long) (sectionY & 0x3FF) << 40)
                | ((long) (sectionX & 0xFFFFF) << 20)
                | (long) (sectionZ & 0xFFFFF);
    }

    public static boolean shouldRenderSyntheticLayer(BlockRenderLayer layer, boolean nothiriumLoaded) {
        return layer != null && (!AusmBloomLayer.isBloomLayer(layer) || !nothiriumLoaded);
    }
}
