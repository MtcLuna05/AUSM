package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

public final class NothiriumPipelineCompat {
    private NothiriumPipelineCompat() {
    }

    public static VertexFormat pipelineBlockFormat(VertexFormat fallback) {
        ensureFormats();
        return ExtendedVertexFormats.PIPELINE_BLOCK != null ? ExtendedVertexFormats.PIPELINE_BLOCK : fallback;
    }

    public static int pipelineBlockStride(int fallback) {
        ensureFormats();
        return ExtendedVertexFormats.PIPELINE_BLOCK != null ? ExtendedVertexFormats.PIPELINE_BLOCK.getSize() : fallback;
    }

    private static void ensureFormats() {
        if (ExtendedVertexFormats.PIPELINE_BLOCK == null) {
            ExtendedVertexFormats.initialize();
        }
    }
}
