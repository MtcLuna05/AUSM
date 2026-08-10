package com.l.ausm.impl.pipeline.compat;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Invalidates the GL43 indirect-command cache when Nothirium uploads a new
 * section VBO.  Keeping this separate from the renderer mixin lets an upload
 * made by a worker-drained render task safely refresh every buffered command
 * slot before it is drawn again.
 */
public final class NothiriumVisibleTerrainCache {
    private static final AtomicLong VBO_GENERATION = new AtomicLong();

    private NothiriumVisibleTerrainCache() {
    }

    public static void markVboUpload() {
        VBO_GENERATION.incrementAndGet();
    }

    public static long vboGeneration() {
        return VBO_GENERATION.get();
    }
}
