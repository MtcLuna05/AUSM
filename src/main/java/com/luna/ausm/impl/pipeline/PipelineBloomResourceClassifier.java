package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomRenderer;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;

/**
 * Detects bloom-bearing model resources and retains the result per block state.
 */
final class PipelineBloomResourceClassifier {
    private final AusmBloomRenderer bloomRenderer;
    private final ConcurrentMap<String, Boolean> stateCache = new ConcurrentHashMap<>();
    private final Set<String> scansInProgress = ConcurrentHashMap.newKeySet();

    PipelineBloomResourceClassifier(AusmBloomRenderer bloomRenderer) {
        this.bloomRenderer = bloomRenderer;
    }

    boolean hasBloomResourceGeometry(IBlockState state) {
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        String key = MinecraftReflectionCompat.stateString(state);
        Boolean cached = stateCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (!scansInProgress.add(key)) {
            return false;
        }
        try {
            boolean result = scanStateForBloomResourceGeometry(state);
            stateCache.putIfAbsent(key, result);
            return result;
        } finally {
            scansInProgress.remove(key);
        }
    }

    private boolean scanStateForBloomResourceGeometry(IBlockState state) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        BlockRendererDispatcher dispatcher = MinecraftReflectionCompat.blockRendererDispatcher(minecraft);
        if (dispatcher == null) {
            return false;
        }
        IBakedModel model = MinecraftReflectionCompat.call(dispatcher, IBakedModel.class, null,
                new String[]{"func_184389_a", "getModelForState"}, new Class<?>[]{IBlockState.class}, state);
        if (model == null) {
            return false;
        }
        BlockRenderLayer previousLayer = MinecraftReflectionCompat.currentRenderLayer();
        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                if (AusmBloomLayer.isBloomLayer(layer) || !canRenderInLayer(state, layer)) {
                    continue;
                }
                MinecraftReflectionCompat.setCurrentRenderLayer(layer);
                if (modelQuadsHaveBloomSprite(model, state, null)) {
                    return true;
                }
                for (EnumFacing side : EnumFacing.values()) {
                    if (modelQuadsHaveBloomSprite(model, state, side)) {
                        return true;
                    }
                }
            }
        } finally {
            MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
        }
        return false;
    }

    private boolean modelQuadsHaveBloomSprite(IBakedModel model, IBlockState state, EnumFacing side) {
        List<BakedQuad> quads = MinecraftReflectionCompat.bakedModelQuads(model, state, side, 0L);
        if (quads == null || quads.isEmpty()) {
            return false;
        }
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad != null ? MinecraftReflectionCompat.bakedQuadSprite(quad) : null;
            String name = sprite != null ? MinecraftReflectionCompat.spriteIconName(sprite) : null;
            if (name != null && (isEmissiveSpriteName(name) || bloomRenderer.hasBloomSprite(name))) {
                return true;
            }
        }
        return false;
    }

    private static boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        try {
            Block block = MinecraftReflectionCompat.blockFromState(state);
            return state != null && block != null && layer != null
                    && MinecraftReflectionCompat.blockCanRenderInLayer(block, state, layer);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isEmissiveSpriteName(String spriteName) {
        String normalized = spriteName.toLowerCase(Locale.ROOT);
        return normalized.endsWith("_e") || normalized.contains("_e/") || normalized.contains("/emissive")
                || normalized.contains("_emissive") || normalized.contains("/glow") || normalized.contains("_glow")
                || normalized.contains("/bloom") || normalized.contains("_bloom");
    }
}
