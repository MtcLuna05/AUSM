package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.MainMod;

/** Lightweight client-thread timings for the Nothirium shadow bridge. */
final class NothiriumShadowCpuTiming {
    private static final long WINDOW_SELECTIONS = 45L;

    private static long selectionCalls;
    private static long selectionNanos;
    private static long selectionMaxNanos;
    private static long selectionCacheHits;
    private static long scannedChunks;
    private static long selectedChunks;
    private static long drawCalls;
    private static long drawNanos;
    private static long drawMaxNanos;
    private static long submittedChunks;
    private static long indirectAttempts;
    private static long indirectSuccesses;
    private static long indirectFallbacks;
    private static long indirectNanos;
    private static long indirectSubmittedChunks;
    private static long indirectCommandUploads;
    private static long indirectCommandReuses;
    private static long gateGl43;
    private static long gateUniform;
    private static long gateAttribute;

    private NothiriumShadowCpuTiming() {
    }

    static void selectionFinished(long elapsedNanos, boolean cacheHit, int scanned, int selected) {
        if (selectionCalls >= WINDOW_SELECTIONS) {
            logAndReset();
        }
        selectionCalls++;
        selectionNanos += elapsedNanos;
        selectionMaxNanos = Math.max(selectionMaxNanos, elapsedNanos);
        selectionCacheHits += cacheHit ? 1L : 0L;
        scannedChunks += scanned;
        selectedChunks += selected;
    }

    static void layerDrawFinished(long elapsedNanos, int draws) {
        drawCalls++;
        drawNanos += elapsedNanos;
        drawMaxNanos = Math.max(drawMaxNanos, elapsedNanos);
        submittedChunks += draws;
    }

    static void indirectFinished(long elapsedNanos, boolean shadowPass, boolean succeeded, int draws) {
        if (!shadowPass) {
            return;
        }
        indirectAttempts++;
        indirectNanos += elapsedNanos;
        if (succeeded) {
            indirectSuccesses++;
            indirectSubmittedChunks += draws;
        } else {
            indirectFallbacks++;
        }
    }

    static void indirectGateMiss(boolean shadowPass, boolean gl43, boolean chunkOffsetUniform,
                                 boolean chunkOffsetAttribute) {
        if (!shadowPass) {
            return;
        }
        if (!gl43) {
            gateGl43++;
        } else if (!chunkOffsetUniform) {
            gateUniform++;
        } else if (!chunkOffsetAttribute) {
            gateAttribute++;
        }
    }

    static void indirectCommandCacheResult(boolean shadowPass, boolean uploaded) {
        if (!shadowPass) {
            return;
        }
        if (uploaded) {
            indirectCommandUploads++;
        } else {
            indirectCommandReuses++;
        }
    }

    private static void logAndReset() {
        long divisor = Math.max(1L, selectionCalls);
        MainMod.LOGGER.info(
                "[AUSMNothiriumShadowCpu] selections={} cacheHits={} selection={}/{}us scannedAvg={} selectedAvg={} "
                        + "layerDraws={} layerSubmit={}/{}us chunks={} indirect={}/{} fallback={} indirectBuild={}us chunks={} "
                        + "indirectCommands(upload={},reuse={}) gateMiss(gl43={},uniform={},attribute={})",
                selectionCalls,
                selectionCacheHits,
                micros(selectionNanos / divisor),
                micros(selectionMaxNanos),
                scannedChunks / divisor,
                selectedChunks / divisor,
                drawCalls,
                micros(drawCalls == 0 ? 0L : drawNanos / drawCalls),
                micros(drawMaxNanos),
                submittedChunks,
                indirectSuccesses,
                indirectAttempts,
                indirectFallbacks,
                micros(indirectAttempts == 0 ? 0L : indirectNanos / indirectAttempts),
                indirectSubmittedChunks,
                indirectCommandUploads,
                indirectCommandReuses,
                gateGl43,
                gateUniform,
                gateAttribute);
        selectionCalls = 0L;
        selectionNanos = 0L;
        selectionMaxNanos = 0L;
        selectionCacheHits = 0L;
        scannedChunks = 0L;
        selectedChunks = 0L;
        drawCalls = 0L;
        drawNanos = 0L;
        drawMaxNanos = 0L;
        submittedChunks = 0L;
        indirectAttempts = 0L;
        indirectSuccesses = 0L;
        indirectFallbacks = 0L;
        indirectNanos = 0L;
        indirectSubmittedChunks = 0L;
        indirectCommandUploads = 0L;
        indirectCommandReuses = 0L;
        gateGl43 = 0L;
        gateUniform = 0L;
        gateAttribute = 0L;
    }

    private static long micros(long nanos) {
        return nanos / 1_000L;
    }
}
