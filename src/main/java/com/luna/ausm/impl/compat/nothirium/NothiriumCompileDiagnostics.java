package com.luna.ausm.impl.compat.nothirium;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostic counters kept outside the mixin target surface.
 *
 * <p>Mixin rejects non-private static fields declared by a mixin class. These
 * values are shared with the split compile helpers, so they belong in an
 * ordinary companion class instead of being widened on the mixin itself.</p>
 */
final class NothiriumCompileDiagnostics {
    static final int FIRE_FALLBACK_LOG_LIMIT = 0;
    static final AtomicInteger FIRE_FALLBACK_LOGS = new AtomicInteger();

    static final int EMISSIVE_FALLBACK_LOG_LIMIT = 0;
    static final AtomicInteger EMISSIVE_FALLBACK_LOGS = new AtomicInteger();

    static final int BLOOM_ONLY_BASE_FALLBACK_LOG_LIMIT = 0;
    static final AtomicInteger BLOOM_ONLY_BASE_FALLBACK_LOGS = new AtomicInteger();

    static final int BLOOM_BASE_ROUTE_PROBE_LIMIT = 0;
    static final AtomicInteger BLOOM_BASE_ROUTE_PROBES = new AtomicInteger();

    static final int BLOOM_VERTEX_PROBE_LIMIT = 0;
    static final AtomicInteger NATIVE_BLOOM_VERTEX_PROBES = new AtomicInteger();

    static final int ENDERIO_GLASS_LAYER_PROBE_LIMIT = 0;
    static final AtomicInteger ENDERIO_GLASS_LAYER_PROBES = new AtomicInteger();

    static final int FRAMED_BLOOM_ROUTE_PROBE_LIMIT = 0;
    static final AtomicInteger FRAMED_BLOOM_ROUTE_PROBES = new AtomicInteger();

    static final int FRAMED_BLOOM_FINAL_PROBE_LIMIT = 0;
    static final AtomicInteger FRAMED_BLOOM_FINAL_PROBES = new AtomicInteger();

    private NothiriumCompileDiagnostics() {
    }
}
