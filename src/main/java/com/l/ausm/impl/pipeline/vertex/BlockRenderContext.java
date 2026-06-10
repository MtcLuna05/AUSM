package com.l.ausm.impl.pipeline.vertex;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

public final class BlockRenderContext {

    private static final ThreadLocal<Integer> CURRENT_BLOCK_ENTITY_ID = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Short> CURRENT_RENDER_TYPE = ThreadLocal.withInitial(() -> (short) -1);
    private static final ThreadLocal<Short> CURRENT_METADATA = ThreadLocal.withInitial(() -> (short) 0);
    private static final ThreadLocal<Integer> CURRENT_LOCAL_X = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_LOCAL_Y = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_LOCAL_Z = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_BLOCK_EMISSION = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> SEPARATE_AO_ELIGIBLE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<float[]> CURRENT_QUAD_AO = new ThreadLocal<>();

    private BlockRenderContext() {
    }

    public static void setBlockEntityId(int blockEntityId) {
        CURRENT_BLOCK_ENTITY_ID.set(blockEntityId);
    }

    public static int blockEntityId() {
        return CURRENT_BLOCK_ENTITY_ID.get();
    }

    public static void setRenderType(short renderType) {
        CURRENT_RENDER_TYPE.set(renderType);
    }

    public static short renderType() {
        return CURRENT_RENDER_TYPE.get();
    }

    public static void setMetadata(int metadata) {
        CURRENT_METADATA.set((short) (metadata & 0xFFFF));
    }

    public static short metadata() {
        return CURRENT_METADATA.get();
    }

    public static void setLocalBlockPos(int x, int y, int z) {
        CURRENT_LOCAL_X.set(x & 15);
        CURRENT_LOCAL_Y.set(y & 15);
        CURRENT_LOCAL_Z.set(z & 15);
    }

    public static void setBlockEmission(int blockEmission) {
        CURRENT_BLOCK_EMISSION.set(Math.max(0, Math.min(15, blockEmission)));
    }

    public static int blockEmission() {
        return CURRENT_BLOCK_EMISSION.get();
    }

    public static int midBlock(float x, float y, float z) {
        return packMidBlock(
                CURRENT_LOCAL_X.get() + 0.5f - x,
                CURRENT_LOCAL_Y.get() + 0.5f - y,
                CURRENT_LOCAL_Z.get() + 0.5f - z
        );
    }

    private static int packMidBlock(float x, float y, float z) {
        return ((int) (x * 64.0f) & 0xFF)
                | (((int) (y * 64.0f) & 0xFF) << 8)
                | (((int) (z * 64.0f) & 0xFF) << 16)
                | ((CURRENT_BLOCK_EMISSION.get() & 0xFF) << 24);
    }

    public static void setSeparateAoEligible(boolean separateAoEligible) {
        SEPARATE_AO_ELIGIBLE.set(separateAoEligible);
    }

    public static boolean separateAoEligible() {
        return SEPARATE_AO_ELIGIBLE.get();
    }

    public static void setQuadAo(float[] quadAo) {
        CURRENT_QUAD_AO.set(quadAo == null ? null : quadAo.clone());
    }

    public static boolean hasQuadAo() {
        return CURRENT_QUAD_AO.get() != null;
    }

    public static float separateAoForVertex(int vertexIndex, float fallback) {
        float[] quadAo = CURRENT_QUAD_AO.get();
        if (quadAo == null || vertexIndex < 1 || vertexIndex > 4) {
            return fallback;
        }
        return quadAo[4 - vertexIndex];
    }

    public static void clearQuadAo() {
        CURRENT_QUAD_AO.remove();
    }

    public static void clear() {
        CURRENT_BLOCK_ENTITY_ID.remove();
        CURRENT_RENDER_TYPE.remove();
        CURRENT_METADATA.remove();
        CURRENT_LOCAL_X.remove();
        CURRENT_LOCAL_Y.remove();
        CURRENT_LOCAL_Z.remove();
        CURRENT_BLOCK_EMISSION.remove();
        SEPARATE_AO_ELIGIBLE.remove();
        CURRENT_QUAD_AO.remove();
    }
}
