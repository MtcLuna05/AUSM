package com.l.ausm.impl.pipeline;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Safe render-property queries shared by block policy, diagnostics, and terrain compatibility. */
final class PipelineBlockRenderProperties {
    private PipelineBlockRenderProperties() {
    }

    static EnumBlockRenderType renderType(IBlockState state) {
        try {
            return state != null ? MinecraftReflectionCompat.stateRenderType(state) : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static BlockRenderLayer renderLayer(IBlockState state) {
        try {
            Block block = state != null ? MinecraftReflectionCompat.blockFromState(state) : null;
            return block != null ? MinecraftReflectionCompat.blockRenderLayer(block) : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static int lightValue(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        try {
            if (state == null) {
                return 0;
            }
            return blockAccess != null && pos != null
                    ? MinecraftReflectionCompat.stateLightValue(state, blockAccess, pos)
                    : MinecraftReflectionCompat.stateLightValue(state);
        } catch (RuntimeException | LinkageError ignored) {
            return -1;
        }
    }

    static boolean opaqueCube(IBlockState state) {
        return state != null && MinecraftReflectionCompat.callBoolean(state,
                new String[] {"func_185913_b", "isOpaqueCube"}, MinecraftReflectionCompat.NO_PARAMETERS, false);
    }

    static boolean fullCube(IBlockState state) {
        return state != null && MinecraftReflectionCompat.callBoolean(state,
                new String[] {"func_185917_h", "isFullCube"}, MinecraftReflectionCompat.NO_PARAMETERS, false);
    }

    static boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        try {
            Block block = state != null ? MinecraftReflectionCompat.blockFromState(state) : null;
            return block != null && layer != null && MinecraftReflectionCompat.blockCanRenderInLayer(block, state, layer);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    static int metadata(IBlockState state) {
        Block block = state != null ? MinecraftReflectionCompat.blockFromState(state) : null;
        try {
            return block != null ? MinecraftReflectionCompat.blockMetaFromState(block, state) : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
