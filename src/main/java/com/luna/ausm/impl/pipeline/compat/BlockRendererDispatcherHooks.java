package com.luna.ausm.impl.pipeline.compat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class BlockRendererDispatcherHooks {
    public static final int RENDER_PROBE_LOG_LIMIT = 0;
    public static final int EMISSIVE_DISPATCHER_FALLBACK_LOG_LIMIT = 0;
    public static final int EMISSIVE_DISPATCHER_FALLBACK_SKIP_LOG_LIMIT = 0;
    public static final ThreadLocal<Integer> PROBE_START_VERTEX = new ThreadLocal<>();
    public static final ThreadLocal<Integer> FRAMED_DIAGNOSTIC_START_VERTEX = new ThreadLocal<>();
    public static final ThreadLocal<Integer> SOFT_VANILLA_SPECIAL_START_VERTEX = new ThreadLocal<>();
    public static final ThreadLocal<Boolean> BLOOM_FALLBACK_RENDER = new ThreadLocal<>();
    public static final ThreadLocal<Boolean> LIQUID_RENDER = new ThreadLocal<>();
    public static final AtomicInteger PROBE_LOG_COUNT = new AtomicInteger();
    public static final AtomicInteger FRAMED_PIPE_FAILURE_COUNT = new AtomicInteger();
    public static final AtomicInteger EMISSIVE_DISPATCHER_FALLBACK_LOG_COUNT = new AtomicInteger();
    public static final AtomicInteger EMISSIVE_DISPATCHER_FALLBACK_SKIP_LOG_COUNT = new AtomicInteger();
    public static final Set<String> PROBE_LOGGED = ConcurrentHashMap.newKeySet();

    private BlockRendererDispatcherHooks() {
    }
}
