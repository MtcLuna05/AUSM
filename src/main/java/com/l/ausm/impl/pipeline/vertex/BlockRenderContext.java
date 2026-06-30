package com.l.ausm.impl.pipeline.vertex;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public final class BlockRenderContext {

    private static final ThreadLocal<Integer> CURRENT_BLOCK_ENTITY_ID = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Short> CURRENT_RENDER_TYPE = ThreadLocal.withInitial(() -> (short) -1);
    private static final ThreadLocal<Short> CURRENT_METADATA = ThreadLocal.withInitial(() -> (short) 0);
    private static final ThreadLocal<Integer> CURRENT_LOCAL_X = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_LOCAL_Y = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_LOCAL_Z = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_BLOCK_X = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_BLOCK_Y = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_BLOCK_Z = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<IBlockAccess> CURRENT_BLOCK_ACCESS = new ThreadLocal<>();
    private static final ThreadLocal<BlockPos> CURRENT_BLOCK_POS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CURRENT_AGRICRAFT_CROP = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> CURRENT_PACKED_LIGHTMAP = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_BLOCK_EMISSION = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_BLOCK_ALPHA = ThreadLocal.withInitial(() -> -1);
    private static final ThreadLocal<Boolean> CURRENT_CRYSTAL_ONLY_EMISSION = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> CURRENT_QUAD_EMISSION_OVERRIDE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> CURRENT_QUAD_BLOCK_ENTITY_ID_OVERRIDE = new ThreadLocal<>();
    private static final ThreadLocal<Short> CURRENT_QUAD_RENDER_TYPE_OVERRIDE = new ThreadLocal<>();
    private static final ThreadLocal<Short> CURRENT_QUAD_METADATA_OVERRIDE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SEPARATE_AO_ELIGIBLE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<float[]> CURRENT_QUAD_AO = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BLOOM_MASK_FALLBACK = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<float[]> BLOOM_MASK_UV = ThreadLocal.withInitial(() -> new float[] {0.5f, 0.5f});
    private static final ThreadLocal<Integer> BLOOM_MASK_COLOR = ThreadLocal.withInitial(() -> -1);
    private static final ThreadLocal<String> CURRENT_DEBUG_KIND = ThreadLocal.withInitial(() -> "unknown");
    private static final ThreadLocal<String> CURRENT_DEBUG_STATE = ThreadLocal.withInitial(() -> "unknown");
    private static final ThreadLocal<String> CURRENT_DEBUG_EFFECTIVE_STATE = ThreadLocal.withInitial(() -> "unknown");

    private BlockRenderContext() {
    }

    public static void setBlockEntityId(int blockEntityId) {
        CURRENT_BLOCK_ENTITY_ID.set(blockEntityId);
    }

    public static int blockEntityId() {
        Integer override = CURRENT_QUAD_BLOCK_ENTITY_ID_OVERRIDE.get();
        return override != null ? override : CURRENT_BLOCK_ENTITY_ID.get();
    }

    public static void setRenderType(short renderType) {
        CURRENT_RENDER_TYPE.set(renderType);
    }

    public static short renderType() {
        Short override = CURRENT_QUAD_RENDER_TYPE_OVERRIDE.get();
        return override != null ? override : CURRENT_RENDER_TYPE.get();
    }

    public static void setMetadata(int metadata) {
        CURRENT_METADATA.set((short) (metadata & 0xFFFF));
    }

    public static short metadata() {
        Short override = CURRENT_QUAD_METADATA_OVERRIDE.get();
        return override != null ? override : CURRENT_METADATA.get();
    }

    public static void setLocalBlockPos(int x, int y, int z) {
        CURRENT_BLOCK_X.set(x);
        CURRENT_BLOCK_Y.set(y);
        CURRENT_BLOCK_Z.set(z);
        CURRENT_LOCAL_X.set(x & 15);
        CURRENT_LOCAL_Y.set(y & 15);
        CURRENT_LOCAL_Z.set(z & 15);
    }

    public static void setWorldBlockContext(IBlockAccess blockAccess, BlockPos pos) {
        if (blockAccess != null) {
            CURRENT_BLOCK_ACCESS.set(blockAccess);
        } else {
            CURRENT_BLOCK_ACCESS.remove();
        }
        if (pos != null) {
            CURRENT_BLOCK_POS.set(pos.toImmutable());
        } else {
            CURRENT_BLOCK_POS.remove();
        }
    }

    public static IBlockAccess blockAccess() {
        return CURRENT_BLOCK_ACCESS.get();
    }

    public static BlockPos blockPos() {
        return CURRENT_BLOCK_POS.get();
    }

    public static int blockX() {
        return CURRENT_BLOCK_X.get();
    }

    public static int blockY() {
        return CURRENT_BLOCK_Y.get();
    }

    public static int blockZ() {
        return CURRENT_BLOCK_Z.get();
    }

    public static void setAgricraftCrop(boolean agricraftCrop) {
        CURRENT_AGRICRAFT_CROP.set(agricraftCrop);
    }

    public static boolean isAgricraftCrop() {
        return CURRENT_AGRICRAFT_CROP.get();
    }

    public static void setPackedLightmap(int packedLightmap) {
        CURRENT_PACKED_LIGHTMAP.set(packedLightmap);
    }

    public static int packedLightmap() {
        return CURRENT_PACKED_LIGHTMAP.get();
    }

    public static int localX() {
        return CURRENT_LOCAL_X.get();
    }

    public static int localY() {
        return CURRENT_LOCAL_Y.get();
    }

    public static int localZ() {
        return CURRENT_LOCAL_Z.get();
    }

    public static void setBlockEmission(int blockEmission) {
        CURRENT_BLOCK_EMISSION.set(Math.max(0, Math.min(15, blockEmission)));
    }

    public static int blockEmission() {
        Integer override = CURRENT_QUAD_EMISSION_OVERRIDE.get();
        return override != null ? Math.max(0, Math.min(15, override)) : CURRENT_BLOCK_EMISSION.get();
    }

    public static void setBlockAlpha(int alpha) {
        CURRENT_BLOCK_ALPHA.set(alpha >= 0 ? Math.max(0, Math.min(255, alpha)) : -1);
    }

    public static int blockAlpha() {
        return CURRENT_BLOCK_ALPHA.get();
    }

    public static int vanillaLightmapEmission() {
        return CURRENT_CRYSTAL_ONLY_EMISSION.get() ? 0 : blockEmission();
    }

    public static void setCrystalOnlyEmission(boolean crystalOnlyEmission) {
        CURRENT_CRYSTAL_ONLY_EMISSION.set(crystalOnlyEmission);
        CURRENT_QUAD_EMISSION_OVERRIDE.remove();
    }

    public static void setQuadSprite(String spriteName) {
        if (!CURRENT_CRYSTAL_ONLY_EMISSION.get()) {
            CURRENT_QUAD_EMISSION_OVERRIDE.remove();
            return;
        }
        if (isAstralCrystalSprite(spriteName)) {
            CURRENT_QUAD_EMISSION_OVERRIDE.remove();
        } else {
            CURRENT_QUAD_EMISSION_OVERRIDE.set(0);
        }
    }

    public static void clearQuadEmissionOverride() {
        clearQuadOverrides();
    }

    public static void setQuadBlockMetadata(int blockEntityId, short renderType, int metadata, int emission) {
        CURRENT_QUAD_BLOCK_ENTITY_ID_OVERRIDE.set(blockEntityId);
        CURRENT_QUAD_RENDER_TYPE_OVERRIDE.set(renderType);
        CURRENT_QUAD_METADATA_OVERRIDE.set((short) (metadata & 0xFFFF));
        CURRENT_QUAD_EMISSION_OVERRIDE.set(Math.max(0, Math.min(15, emission)));
    }

    public static void clearQuadOverrides() {
        CURRENT_QUAD_BLOCK_ENTITY_ID_OVERRIDE.remove();
        CURRENT_QUAD_RENDER_TYPE_OVERRIDE.remove();
        CURRENT_QUAD_METADATA_OVERRIDE.remove();
        CURRENT_QUAD_EMISSION_OVERRIDE.remove();
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
                | ((blockEmission() & 0xFF) << 24);
    }

    private static boolean isAstralCrystalSprite(String spriteName) {
        if (spriteName == null) {
            return false;
        }
        String normalized = spriteName.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("astralsorcery:blocks/crystal/")
                && !normalized.contains("rock");
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

    public static void setBloomMaskFallback(boolean enabled, float u, float v, int color) {
        BLOOM_MASK_FALLBACK.set(enabled);
        BLOOM_MASK_UV.set(new float[] {u, v});
        BLOOM_MASK_COLOR.set(color);
    }

    public static boolean bloomMaskFallback() {
        return BLOOM_MASK_FALLBACK.get();
    }

    public static float bloomMaskU() {
        return BLOOM_MASK_UV.get()[0];
    }

    public static float bloomMaskV() {
        return BLOOM_MASK_UV.get()[1];
    }

    public static int bloomMaskColor() {
        return BLOOM_MASK_COLOR.get();
    }

    public static void clearBloomMaskFallback() {
        BLOOM_MASK_FALLBACK.remove();
        BLOOM_MASK_UV.remove();
        BLOOM_MASK_COLOR.remove();
    }

    public static void setDebugBlock(String kind, String state, String effectiveState) {
        CURRENT_DEBUG_KIND.set(kind != null ? kind : "unknown");
        CURRENT_DEBUG_STATE.set(state != null ? state : "unknown");
        CURRENT_DEBUG_EFFECTIVE_STATE.set(effectiveState != null ? effectiveState : "unknown");
    }

    public static String debugKind() {
        return CURRENT_DEBUG_KIND.get();
    }

    public static String debugState() {
        return CURRENT_DEBUG_STATE.get();
    }

    public static String debugEffectiveState() {
        return CURRENT_DEBUG_EFFECTIVE_STATE.get();
    }

    public static void clear() {
        CURRENT_BLOCK_ENTITY_ID.remove();
        CURRENT_RENDER_TYPE.remove();
        CURRENT_METADATA.remove();
        CURRENT_LOCAL_X.remove();
        CURRENT_LOCAL_Y.remove();
        CURRENT_LOCAL_Z.remove();
        CURRENT_BLOCK_X.remove();
        CURRENT_BLOCK_Y.remove();
        CURRENT_BLOCK_Z.remove();
        CURRENT_BLOCK_ACCESS.remove();
        CURRENT_BLOCK_POS.remove();
        CURRENT_AGRICRAFT_CROP.remove();
        CURRENT_PACKED_LIGHTMAP.remove();
        CURRENT_BLOCK_EMISSION.remove();
        CURRENT_BLOCK_ALPHA.remove();
        CURRENT_CRYSTAL_ONLY_EMISSION.remove();
        clearQuadOverrides();
        SEPARATE_AO_ELIGIBLE.remove();
        CURRENT_QUAD_AO.remove();
        BLOOM_MASK_FALLBACK.remove();
        BLOOM_MASK_UV.remove();
        BLOOM_MASK_COLOR.remove();
        CURRENT_DEBUG_KIND.remove();
        CURRENT_DEBUG_STATE.remove();
        CURRENT_DEBUG_EFFECTIVE_STATE.remove();
    }
}
