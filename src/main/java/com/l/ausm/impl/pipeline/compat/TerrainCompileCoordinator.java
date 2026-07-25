package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.IdentityHashMap;

/**
 * Shared, backend-neutral decisions made while a chunk section is compiled.
 * Native renderers retain mesh ownership; this avoids repeating expensive
 * compatibility classification for every block/layer visit.
 */
public final class TerrainCompileCoordinator {
    private static final ThreadLocal<CompileState> STATE = ThreadLocal.withInitial(CompileState::new);

    private TerrainCompileCoordinator() {
    }

    public static void beginSection() {
        CompileState state = STATE.get();
        if (state.depth++ == 0) {
            state.decisions.clear();
            state.nextGeneration();
        }
    }

    public static void endSection() {
        CompileState state = STATE.get();
        if (state.depth > 0 && --state.depth == 0) {
            state.decisions.clear();
        }
    }

    public static boolean canRenderInLayer(Block block, IBlockState state, BlockRenderLayer layer,
                                           PipelineContext pipeline) {
        if (block == null || state == null || layer == null) {
            return false;
        }
        CompileDecision decision = decision(state, pipeline);
        // The resource pack owns BLOOM participation. Do not synthesize this
        // layer from a classifier: compatibility mixins must be able to move
        // a block back into a normal terrain layer without leaving a second
        // bloom-only mesh behind.
        if (AusmBloomLayer.isBloomLayer(layer)) {
            // Let the position-aware render hook inspect GPOM's material. It
            // will discard hosts without bloom and route inherited bloom
            // materials through the Forge dispatcher.
            if (decision.blockcraftery) {
                return true;
            }
            return MinecraftReflectionCompat.blockCanRenderInLayer(block, state, layer);
        }
        // Fire and Twilight portals may advertise only the compatibility BLOOM
        // layer after the shader bridge changes their render-layer query. Keep
        // their real base geometry in the normal terrain pass as well.
        if (MinecraftReflectionCompat.stateMaterialIsFire(state)
                && layer == BlockRenderLayer.CUTOUT) {
            return true;
        }
        if (pipeline.isCeleritasTwilightPortalState(state)
                && layer == BlockRenderLayer.TRANSLUCENT) {
            return true;
        }
        if (decision.forgeFallback) {
            if (MinecraftReflectionCompat.blockCanRenderInLayer(block, state, layer)) {
                return true;
            }
            BlockRenderLayer vanillaLayer = MinecraftReflectionCompat.blockRenderLayer(block);
            return (vanillaLayer != null && layer == vanillaLayer)
                    || pipeline.shouldRenderBloomSourceInBaseLayer(state, layer);
        }
        int bit = 1 << Math.max(0, Math.min(30, layer.ordinal()));
        if ((decision.knownLayers & bit) == 0) {
            boolean nativeLayer = MinecraftReflectionCompat.blockCanRenderInLayer(block, state, layer);
            boolean forcedBase = pipeline.shouldRenderBloomSourceInBaseLayer(state, layer);
            if (nativeLayer || forcedBase) {
                decision.renderedLayers |= bit;
            }
            decision.knownLayers |= bit;
        }
        return (decision.renderedLayers & bit) != 0;
    }

    private static boolean isBlockcrafteryState(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        net.minecraft.util.ResourceLocation name = block == null
                ? null : MinecraftReflectionCompat.blockRegistryName(block);
        return "blockcraftery".equals(MinecraftReflectionCompat.resourceNamespace(name));
    }

    /**
     * Vanilla Forge fallback is deliberately limited to Blockcraftery. Broad
     * portal/bloom fallback routing was the dominant Celeritas compile cost.
     */
    public static boolean requiresForgeFallback(IBlockState state, PipelineContext pipeline) {
        return state != null && decision(state, pipeline).forgeFallback;
    }

    public static boolean requiresForgeFallback(IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                                PipelineContext pipeline) {
        if (state == null) {
            return false;
        }
        // Custom fluids own their mesh through Forge's fluid renderer. The
        // Celeritas renderer only sees the host block model and can silently
        // omit the liquid surface, so keep every liquid on the dispatcher path.
        if (MinecraftReflectionCompat.stateIsLiquidOrWater(state)) {
            return true;
        }
        CompileDecision decision = decision(state, pipeline);
        if (decision.forgeFallback) {
            return true;
        }
        // Only editable Blockcraftery frames require position-sensitive material
        // inspection. Cache that reflection-heavy lookup once per section cell,
        // rather than repeating it for every native Celeritas render layer.
        if (!decision.blockcraftery || blockAccess == null || pos == null) {
            return false;
        }
        CompileState compileState = STATE.get();
        int localIndex = localSectionIndex(pos);
        if (compileState.dynamicFallbackGeneration[localIndex] != compileState.generation) {
            compileState.dynamicFallbackGeneration[localIndex] = compileState.generation;
            compileState.dynamicFallback[localIndex] = pipeline.shouldUseCeleritasForgeFallback(state, blockAccess, pos);
        }
        return compileState.dynamicFallback[localIndex];
    }

    private static CompileDecision decision(IBlockState state, PipelineContext pipeline) {
        CompileState compileState = STATE.get();
        CompileDecision decision = compileState.decisions.get(state);
        if (decision == null) {
            decision = new CompileDecision(pipeline.shouldUseCeleritasForgeFallback(state), isBlockcrafteryState(state));
            compileState.decisions.put(state, decision);
        }
        return decision;
    }

    private static int localSectionIndex(BlockPos pos) {
        int x = MinecraftReflectionCompat.blockPosX(pos) & 15;
        int y = MinecraftReflectionCompat.blockPosY(pos) & 15;
        int z = MinecraftReflectionCompat.blockPosZ(pos) & 15;
        return x | z << 4 | y << 8;
    }

    private static final class CompileState {
        private final IdentityHashMap<IBlockState, CompileDecision> decisions = new IdentityHashMap<>();
        private final int[] dynamicFallbackGeneration = new int[16 * 16 * 16];
        private final boolean[] dynamicFallback = new boolean[16 * 16 * 16];
        private int generation = 1;
        private int depth;

        private void nextGeneration() {
            generation++;
            if (generation == 0) {
                java.util.Arrays.fill(dynamicFallbackGeneration, 0);
                generation = 1;
            }
        }
    }

    private static final class CompileDecision {
        private final boolean forgeFallback;
        private final boolean blockcraftery;
        private int knownLayers;
        private int renderedLayers;

        private CompileDecision(boolean forgeFallback, boolean blockcraftery) {
            this.forgeFallback = forgeFallback;
            this.blockcraftery = blockcraftery;
        }
    }
}
