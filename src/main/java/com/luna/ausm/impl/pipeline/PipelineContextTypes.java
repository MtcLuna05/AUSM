package com.luna.ausm.impl.pipeline;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.AxisAlignedBB;

final class ShaderChunkRefresh {
    final WorldClient world;
    final int chunkX;
    final int chunkZ;

    ShaderChunkRefresh(WorldClient world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ShaderChunkRefresh refresh)) {
            return false;
        }
        return world == refresh.world && chunkX == refresh.chunkX && chunkZ == refresh.chunkZ;
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(world);
        result = 31 * result + chunkX;
        result = 31 * result + chunkZ;
        return result;
    }
}

final class ClientChunkRenderRefresh {
    final WorldClient world;
    final int chunkX;
    final int chunkZ;
    String reason;
    int attemptsRemaining;
    int delayFrames;
    int nextSectionY;
    int coveredSections;
    boolean shadowRefreshed;

    ClientChunkRenderRefresh(WorldClient world, int chunkX, int chunkZ, String reason,
                             int attemptsRemaining, int delayFrames) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.reason = reason;
        this.attemptsRemaining = attemptsRemaining;
        this.delayFrames = delayFrames;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ClientChunkRenderRefresh refresh)) {
            return false;
        }
        return world == refresh.world && chunkX == refresh.chunkX && chunkZ == refresh.chunkZ;
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(world);
        result = 31 * result + chunkX;
        result = 31 * result + chunkZ;
        return result;
    }
}

final class ClientChunkRenderScheduleResult {
    final int scheduledChunks;
    final int coveredSections;
    final int nextSectionY;
    final boolean completed;
    final int requiredSections;

    ClientChunkRenderScheduleResult(int scheduledChunks, int coveredSections, int nextSectionY,
                                    boolean completed, int requiredSections) {
        this.scheduledChunks = scheduledChunks;
        this.coveredSections = coveredSections;
        this.nextSectionY = nextSectionY;
        this.completed = completed;
        this.requiredSections = requiredSections;
    }

    static ClientChunkRenderScheduleResult empty() {
        return new ClientChunkRenderScheduleResult(0, 0, 0, false, 1);
    }
}

final class ChunkFadeKey {
    private final int dimensionId;
    private final int chunkX;
    private final int chunkY;
    private final int chunkZ;

    ChunkFadeKey(int dimensionId, int chunkX, int chunkY, int chunkZ) {
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChunkFadeKey key)) {
            return false;
        }
        return dimensionId == key.dimensionId
                && chunkX == key.chunkX
                && chunkY == key.chunkY
                && chunkZ == key.chunkZ;
    }

    @Override
    public int hashCode() {
        int result = dimensionId;
        result = 31 * result + chunkX;
        result = 31 * result + chunkY;
        result = 31 * result + chunkZ;
        return result;
    }
}

final class ChunkFadeState {
    float value;
    long lastFrameSeen;

    ChunkFadeState(float value, long lastFrameSeen) {
        this.value = value;
        this.lastFrameSeen = lastFrameSeen;
    }
}

final class SyntheticLightInfo {
    final IBlockState originalState;
    final IBlockState actualState;
    final int shaderBlockId;
    final int voxelId;
    final int emission;
    final String reason;

    SyntheticLightInfo(IBlockState originalState, IBlockState actualState, int shaderBlockId, int voxelId, int emission, String reason) {
        this.originalState = originalState;
        this.actualState = actualState;
        this.shaderBlockId = shaderBlockId;
        this.voxelId = voxelId;
        this.emission = emission;
        this.reason = reason;
    }
}

record ShadowBlockEntityBounds(int x, int y, int z, AxisAlignedBB bounds) {
}
