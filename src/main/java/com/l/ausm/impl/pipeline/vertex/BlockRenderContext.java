package com.l.ausm.impl.pipeline.vertex;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public final class BlockRenderContext {
    public static final int BLOOM_ONLY_MASK_EMISSION = 16;

    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(State::new);

    private BlockRenderContext() {
    }

    public static void setBlockEntityId(int blockEntityId) {
        current().blockEntityId = blockEntityId;
    }

    public static int blockEntityId() {
        State state = current();
        return state.hasQuadBlockEntityIdOverride ? state.quadBlockEntityIdOverride : state.blockEntityId;
    }

    public static void setRenderType(short renderType) {
        current().renderType = renderType;
    }

    public static short renderType() {
        State state = current();
        return state.hasQuadRenderTypeOverride ? state.quadRenderTypeOverride : state.renderType;
    }

    public static void setMetadata(int metadata) {
        current().metadata = (short) (metadata & 0xFFFF);
    }

    public static short metadata() {
        State state = current();
        return state.hasQuadMetadataOverride ? state.quadMetadataOverride : state.metadata;
    }

    public static void setLocalBlockPos(int x, int y, int z) {
        State state = current();
        state.blockX = x;
        state.blockY = y;
        state.blockZ = z;
        state.localX = x & 15;
        state.localY = y & 15;
        state.localZ = z & 15;
    }

    public static void setWorldBlockContext(IBlockAccess blockAccess, BlockPos pos) {
        State state = current();
        state.blockAccess = blockAccess;
        state.blockPos = pos != null ? new BlockPos(state.blockX, state.blockY, state.blockZ) : null;
    }

    public static IBlockAccess blockAccess() {
        return current().blockAccess;
    }

    public static BlockPos blockPos() {
        return current().blockPos;
    }

    public static int blockX() {
        return current().blockX;
    }

    public static int blockY() {
        return current().blockY;
    }

    public static int blockZ() {
        return current().blockZ;
    }

    public static void setAgricraftCrop(boolean agricraftCrop) {
        current().agricraftCrop = agricraftCrop;
    }

    public static boolean isAgricraftCrop() {
        return current().agricraftCrop;
    }

    public static void setPackedLightmap(int packedLightmap) {
        current().packedLightmap = packedLightmap;
    }

    public static int packedLightmap() {
        return current().packedLightmap;
    }

    public static int localX() {
        return current().localX;
    }

    public static int localY() {
        return current().localY;
    }

    public static int localZ() {
        return current().localZ;
    }

    public static void setBlockEmission(int blockEmission) {
        current().blockEmission = clamp(blockEmission, 0, 15);
    }

    public static int blockEmission() {
        State state = current();
        return state.hasQuadEmissionOverride ? clamp(state.quadEmissionOverride, 0, 15) : state.blockEmission;
    }

    public static void setBlockAlpha(int alpha) {
        current().blockAlpha = alpha >= 0 ? clamp(alpha, 0, 255) : -1;
    }

    public static int blockAlpha() {
        return current().blockAlpha;
    }

    public static void setCustomLiquidTint(int color) {
        current().customLiquidTint = color;
    }

    public static int customLiquidTint() {
        return current().customLiquidTint;
    }

    public static int vanillaLightmapEmission() {
        State state = current();
        return state.crystalOnlyEmission || state.bloomOnlyEmission ? 0 : blockEmission();
    }

    public static void setBloomOnlyEmission(boolean bloomOnlyEmission) {
        current().bloomOnlyEmission = bloomOnlyEmission;
    }

    public static void setCrystalOnlyEmission(boolean crystalOnlyEmission) {
        State state = current();
        state.crystalOnlyEmission = crystalOnlyEmission;
        state.hasQuadEmissionOverride = false;
    }

    public static void setQuadSprite(String spriteName) {
        State state = current();
        if (!state.crystalOnlyEmission) {
            state.hasQuadEmissionOverride = false;
            return;
        }
        if (isAstralCrystalSprite(spriteName)) {
            state.hasQuadEmissionOverride = false;
        } else {
            state.quadEmissionOverride = 0;
            state.hasQuadEmissionOverride = true;
        }
    }

    public static void clearQuadEmissionOverride() {
        clearQuadOverrides();
    }

    public static void setQuadBlockMetadata(int blockEntityId, short renderType, int metadata, int emission) {
        State state = current();
        state.quadBlockEntityIdOverride = blockEntityId;
        state.quadRenderTypeOverride = renderType;
        state.quadMetadataOverride = (short) (metadata & 0xFFFF);
        state.quadEmissionOverride = clamp(emission, 0, 15);
        state.hasQuadBlockEntityIdOverride = true;
        state.hasQuadRenderTypeOverride = true;
        state.hasQuadMetadataOverride = true;
        state.hasQuadEmissionOverride = true;
    }

    public static void clearQuadOverrides() {
        State state = current();
        state.hasQuadBlockEntityIdOverride = false;
        state.hasQuadRenderTypeOverride = false;
        state.hasQuadMetadataOverride = false;
        state.hasQuadEmissionOverride = false;
    }

    public static int midBlock(float x, float y, float z) {
        return midBlock(x, y, z, midBlockEmission());
    }

    public static int midBlock(float x, float y, float z, int emission) {
        State state = current();
        return packMidBlock(
                state.localX + 0.5f - x,
                state.localY + 0.5f - y,
                state.localZ + 0.5f - z,
                emission
        );
    }

    public static int midBlockEmission() {
        return current().bloomMaskFallback ? BLOOM_ONLY_MASK_EMISSION : blockEmission();
    }

    private static int packMidBlock(float x, float y, float z, int emission) {
        return ((int) (x * 64.0f) & 0xFF)
                | (((int) (y * 64.0f) & 0xFF) << 8)
                | (((int) (z * 64.0f) & 0xFF) << 16)
                | ((emission & 0xFF) << 24);
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
        current().separateAoEligible = separateAoEligible;
    }

    public static boolean separateAoEligible() {
        return current().separateAoEligible;
    }

    public static void setQuadAo(float[] quadAo) {
        current().quadAo = quadAo == null ? null : quadAo.clone();
    }

    public static boolean hasQuadAo() {
        return current().quadAo != null;
    }

    public static float separateAoForVertex(int vertexIndex, float fallback) {
        float[] quadAo = current().quadAo;
        if (quadAo == null || vertexIndex < 1 || vertexIndex > 4) {
            return fallback;
        }
        return quadAo[4 - vertexIndex];
    }

    public static void clearQuadAo() {
        current().quadAo = null;
    }

    public static void setBloomMaskFallback(boolean enabled) {
        current().bloomMaskFallback = enabled;
    }

    public static boolean bloomMaskFallback() {
        return current().bloomMaskFallback;
    }

    public static void clearBloomMaskFallback() {
        current().bloomMaskFallback = false;
    }

    public static void setDebugBlock(String kind, String state, String effectiveState) {
        State current = current();
        current.debugKind = kind != null ? kind : "unknown";
        current.debugState = state != null ? state : "unknown";
        current.debugEffectiveState = effectiveState != null ? effectiveState : "unknown";
    }

    public static String debugKind() {
        return current().debugKind;
    }

    public static String debugState() {
        return current().debugState;
    }

    public static String debugEffectiveState() {
        return current().debugEffectiveState;
    }

    public static void clear() {
        current().clear();
    }

    private static State current() {
        return CURRENT.get();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class State {
        private int blockEntityId;
        private short renderType = -1;
        private short metadata;
        private int localX;
        private int localY;
        private int localZ;
        private int blockX;
        private int blockY;
        private int blockZ;
        private IBlockAccess blockAccess;
        private BlockPos blockPos;
        private boolean agricraftCrop;
        private int packedLightmap;
        private int blockEmission;
        private int blockAlpha = -1;
        private int customLiquidTint = -1;
        private boolean crystalOnlyEmission;
        private boolean bloomOnlyEmission;
        private int quadEmissionOverride;
        private int quadBlockEntityIdOverride;
        private short quadRenderTypeOverride;
        private short quadMetadataOverride;
        private boolean hasQuadEmissionOverride;
        private boolean hasQuadBlockEntityIdOverride;
        private boolean hasQuadRenderTypeOverride;
        private boolean hasQuadMetadataOverride;
        private boolean separateAoEligible;
        private float[] quadAo;
        private boolean bloomMaskFallback;
        private String debugKind = "unknown";
        private String debugState = "unknown";
        private String debugEffectiveState = "unknown";

        private void clear() {
            blockEntityId = 0;
            renderType = -1;
            metadata = 0;
            localX = 0;
            localY = 0;
            localZ = 0;
            blockX = 0;
            blockY = 0;
            blockZ = 0;
            blockAccess = null;
            blockPos = null;
            agricraftCrop = false;
            packedLightmap = 0;
            blockEmission = 0;
            blockAlpha = -1;
            customLiquidTint = -1;
            crystalOnlyEmission = false;
            bloomOnlyEmission = false;
            quadEmissionOverride = 0;
            quadBlockEntityIdOverride = 0;
            quadRenderTypeOverride = 0;
            quadMetadataOverride = 0;
            hasQuadEmissionOverride = false;
            hasQuadBlockEntityIdOverride = false;
            hasQuadRenderTypeOverride = false;
            hasQuadMetadataOverride = false;
            separateAoEligible = false;
            quadAo = null;
            bloomMaskFallback = false;
            debugKind = "unknown";
            debugState = "unknown";
            debugEffectiveState = "unknown";
        }
    }
}
