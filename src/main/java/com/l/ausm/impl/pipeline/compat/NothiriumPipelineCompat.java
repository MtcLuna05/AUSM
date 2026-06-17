package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

public final class NothiriumPipelineCompat {
    private NothiriumPipelineCompat() {
    }

    public static VertexFormat pipelineBlockFormat(VertexFormat fallback) {
        return shouldUsePipelineBlockFormat() ? ExtendedVertexFormats.PIPELINE_BLOCK : fallback;
    }

    public static int pipelineBlockStride(int fallback) {
        return shouldUsePipelineBlockFormat() ? ExtendedVertexFormats.PIPELINE_BLOCK.getSize() : fallback;
    }

    public static boolean shouldUsePipelineBlockFormat() {
        ensureFormats();
        return ExtendedVertexFormats.PIPELINE_BLOCK != null
                && PipelineContext.getInstance().shouldUsePipelineBlockFormat();
    }

    private static void ensureFormats() {
        if (ExtendedVertexFormats.PIPELINE_BLOCK == null) {
            ExtendedVertexFormats.initialize();
        }
    }
}
