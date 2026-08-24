package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.function.BiConsumer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Executes bounded chunk and block lighting refreshes through mapping-safe accessors.
 */
final class PipelineLightingRefresh {
    private PipelineLightingRefresh() {
    }

    static int refreshChunks(World world, int minX, int maxX, int minZ, int maxZ) {
        if (!(world instanceof WorldClient worldClient)) {
            return 0;
        }
        ChunkProviderClient provider = MinecraftReflectionCompat.call(worldClient, ChunkProviderClient.class, null,
                new String[]{"func_72863_F", "getChunkProvider"}, MinecraftReflectionCompat.NO_PARAMETERS);
        if (provider == null) {
            return 0;
        }
        int refreshed = 0;
        for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
            for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
                Chunk chunk = MinecraftReflectionCompat.call(provider, Chunk.class, null,
                        new String[]{"func_186026_b", "getLoadedChunk"}, new Class<?>[]{int.class, int.class}, chunkX, chunkZ);
                if (chunk == null || MinecraftReflectionCompat.callBoolean(chunk,
                        new String[]{"func_76621_g", "isEmpty"}, MinecraftReflectionCompat.NO_PARAMETERS, false)) {
                    continue;
                }
                try {
                    if (MinecraftReflectionCompat.providerHasSkyLight(MinecraftReflectionCompat.worldProvider(world))) {
                        MinecraftReflectionCompat.invoke(chunk, new String[]{"func_76603_b", "generateSkylightMap"}, MinecraftReflectionCompat.NO_PARAMETERS);
                    }
                    MinecraftReflectionCompat.invoke(chunk, new String[]{"func_76613_n", "resetRelightChecks"}, MinecraftReflectionCompat.NO_PARAMETERS);
                    MinecraftReflectionCompat.invoke(chunk, new String[]{"func_76594_o", "enqueueRelightChecks"}, MinecraftReflectionCompat.NO_PARAMETERS);
                    MinecraftReflectionCompat.invoke(chunk, new String[]{"func_150809_p", "checkLight"}, MinecraftReflectionCompat.NO_PARAMETERS);
                    refreshed++;
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }
        return refreshed;
    }

    static int refreshBlocks(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             BiConsumer<World, BlockPos> syntheticLightRefresh) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int checks = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    MinecraftReflectionCompat.mutableBlockPosSet(pos, x, y, z);
                    if (!MinecraftReflectionCompat.worldIsBlockLoaded(world, pos, false)) {
                        continue;
                    }
                    IBlockState state;
                    int sourceLight;
                    int storedBlockLight;
                    try {
                        state = MinecraftReflectionCompat.worldBlockState(world, pos);
                        sourceLight = MinecraftReflectionCompat.stateLightValue(state, world, pos);
                        storedBlockLight = MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.BLOCK, pos);
                    } catch (RuntimeException | LinkageError ignored) {
                        continue;
                    }
                    if (sourceLight <= 0 && storedBlockLight <= 0) {
                        continue;
                    }
                    try {
                        MinecraftReflectionCompat.callBoolean(world, new String[]{"func_180500_c", "checkLightFor"},
                                new Class<?>[]{EnumSkyBlock.class, BlockPos.class}, false, EnumSkyBlock.BLOCK, pos);
                        if (MinecraftReflectionCompat.providerHasSkyLight(MinecraftReflectionCompat.worldProvider(world))) {
                            MinecraftReflectionCompat.callBoolean(world, new String[]{"func_180500_c", "checkLightFor"},
                                    new Class<?>[]{EnumSkyBlock.class, BlockPos.class}, false, EnumSkyBlock.SKY, pos);
                        }
                        checks++;
                    } catch (RuntimeException | LinkageError ignored) {
                    }
                    if (sourceLight > 0) {
                        syntheticLightRefresh.accept(world, MinecraftReflectionCompat.blockPosToImmutable(pos));
                    }
                }
            }
        }
        return checks;
    }
}
