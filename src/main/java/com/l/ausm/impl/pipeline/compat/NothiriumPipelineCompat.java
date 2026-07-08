package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

public final class NothiriumPipelineCompat {
    private NothiriumPipelineCompat() {
    }

    public static VertexFormat pipelineBlockFormat(VertexFormat fallback) {
        boolean pipeline = shouldUsePipelineBlockFormat();
        VertexFormat selected = pipeline ? ExtendedVertexFormats.PIPELINE_BLOCK : fallback;
        return selected;
    }

    public static int pipelineBlockStride(int fallback) {
        boolean pipeline = shouldUsePipelineBlockFormat();
        int selected = pipeline && ExtendedVertexFormats.PIPELINE_BLOCK != null
                ? ExtendedVertexFormats.size(ExtendedVertexFormats.PIPELINE_BLOCK)
                : fallback;
        return selected;
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
