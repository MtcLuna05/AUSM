package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.util.concurrent.atomic.AtomicInteger;

public final class NothiriumPipelineCompat {
    private static final int FORMAT_PROBE_LIMIT = 48;
    private static final AtomicInteger FORMAT_PROBES = new AtomicInteger();

    private NothiriumPipelineCompat() {
    }

    public static VertexFormat pipelineBlockFormat(VertexFormat fallback) {
        boolean pipeline = shouldUsePipelineBlockFormat();
        VertexFormat selected = pipeline ? ExtendedVertexFormats.PIPELINE_BLOCK : fallback;
        logFormatProbe("format", fallback != null ? fallback.getSize() : -1, selected != null ? selected.getSize() : -1, pipeline);
        return selected;
    }

    public static int pipelineBlockStride(int fallback) {
        boolean pipeline = shouldUsePipelineBlockFormat();
        int selected = pipeline && ExtendedVertexFormats.PIPELINE_BLOCK != null
                ? ExtendedVertexFormats.PIPELINE_BLOCK.getSize()
                : fallback;
        logFormatProbe("stride", fallback, selected, pipeline);
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

    private static void logFormatProbe(String source, int fallback, int selected, boolean pipeline) {
        // Probe disabled.
    }

    private static String firstExternalCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.startsWith("java.lang.Thread")
                    || className.equals(NothiriumPipelineCompat.class.getName())) {
                continue;
            }
            return className + "#" + element.getMethodName() + ":" + element.getLineNumber();
        }
        return "unknown";
    }
}
