package com.luna.ausm.impl.pipeline;

/**
 * Stable identity and retry policy for deferred client chunk-render refreshes.
 */
final class PipelineClientChunkRefreshPolicy {
    private PipelineClientChunkRefreshPolicy() {
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    static int initialDelay(String reason, String blockUpdateReason, String shaderlessBloomReason, int defaultDelay) {
        return blockUpdateReason.equals(reason) || shaderlessBloomReason.equals(reason) ? 0 : defaultDelay;
    }

    static int maxSections(int budget) {
        return Math.max(1, budget);
    }

    static int clampSectionCursor(int sectionY, int sectionCount) {
        return Math.clamp(sectionY, 0, Math.max(0, sectionCount - 1));
    }
}
