package com.luna.ausm.impl.pipeline;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

abstract class PipelineWorldRenderScopeBase extends PipelineRuntimeState {
    protected static final AtomicInteger EXTERNAL_FACADE_TERRAIN_PROBE_LOGS = new AtomicInteger();

    protected static final Set<String> EXTERNAL_FACADE_TERRAIN_PROBE_KEYS = ConcurrentHashMap.newKeySet();

    protected int shadowOriginProbeLogs;

    protected String shadowOriginProbeKey = "";

    protected PipelineWorldRenderScope self() {
        return (PipelineWorldRenderScope) this;
    }
}
