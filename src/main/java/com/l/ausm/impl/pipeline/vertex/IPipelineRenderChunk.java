package com.l.ausm.impl.pipeline.vertex;

import net.minecraft.util.BlockRenderLayer;

public interface IPipelineRenderChunk {
    boolean ausm$usesPipelineVertexFormat(BlockRenderLayer layer);

    boolean ausm$usesPipelineVertexFormat();

    default boolean ausm$hasShaderlessBloomMetadata(BlockRenderLayer layer) {
        return true;
    }
}
