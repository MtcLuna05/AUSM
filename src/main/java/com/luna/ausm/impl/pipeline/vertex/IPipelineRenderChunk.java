package com.luna.ausm.impl.pipeline.vertex;

import net.minecraft.util.BlockRenderLayer;

public interface IPipelineRenderChunk {
    boolean ausm$usesPipelineVertexFormat(BlockRenderLayer layer);

    boolean ausm$usesPipelineVertexFormat();

}
