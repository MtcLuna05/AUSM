package com.l.ausm.impl.pipeline;

import java.util.concurrent.atomic.AtomicInteger;

abstract class PipelineContextBase extends PipelineWorldRenderScope {
    protected static boolean disableShaderlessPreGuiHooks = true;

    protected static final AtomicInteger FLAT_FOLIAGE_HIGHLIGHT_PROBE_LOGS = new AtomicInteger();

    protected static final AtomicInteger LILY_PAD_LIGHTING_PROBE_LOGS = new AtomicInteger();

    protected PipelineContext self() {
        return (PipelineContext) this;
    }
}
