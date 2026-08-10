package com.l.ausm.impl.pipeline.vertex;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public final class BlockRenderContext {
    public static final int BLOOM_ONLY_MASK_EMISSION = 16;
    public static final int FRAMED_BLOOM_BOOST_MARKER = 150;
    /** Reserved only for bounded diagnostics of the copied frame BLOOM overlay. */
    public static final int FRAMED_BLOOM_OVERLAY_PROBE_MARKER = 151;

    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(State::new);

    private BlockRenderContext() {
    }

    public static void setBlockEntityId(int blockEntityId) {
        current().blockEntityId = blockEntityId;
    }

    /** Sets the complete per-block terrain context with one ThreadLocal lookup. */
    public static void configureBlock(int blockEntityId, short renderType, int metadata,
                                      int x, int y, int z, IBlockAccess blockAccess, BlockPos blockPos,
                                      boolean framedMaterialOwner, boolean agricraftCrop, int packedLightmap,
                                      int blockEmission, boolean framedBloomBoost, int blockAlpha,
                                      int customLiquidTint, boolean crystalOnlyEmission,
                                      boolean separateAoEligible) {
        State state = current();
        state.blockEntityId = blockEntityId;
        state.renderType = renderType;
        state.metadata = (short) (metadata & 0xFFFF);
        state.blockX = x;
        state.blockY = y;
        state.blockZ = z;
        state.localX = x & 15;
        state.localY = y & 15;
        state.localZ = z & 15;
        state.blockAccess = blockAccess;
        state.blockPos = blockPos;
        state.framedMaterialOwner = framedMaterialOwner;
        state.agricraftCrop = agricraftCrop;
        state.packedLightmap = packedLightmap;
        state.blockEmission = clamp(blockEmission, 0, 15);
        state.framedBloomBoost = framedBloomBoost;
        state.bloomOnlyEmission = false;
        state.blockAlpha = blockAlpha >= 0 ? clamp(blockAlpha, 0, 255) : -1;
        state.customLiquidTint = customLiquidTint;
        state.crystalOnlyEmission = crystalOnlyEmission;
        state.hasQuadEmissionOverride = false;
        state.separateAoEligible = separateAoEligible;
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

    public static long packedEntityData() {
        State state = current();
        int blockEntityId = state.hasQuadBlockEntityIdOverride ? state.quadBlockEntityIdOverride : state.blockEntityId;
        short renderType = state.hasQuadRenderTypeOverride ? state.quadRenderTypeOverride : state.renderType;
        short metadata = state.hasQuadMetadataOverride ? state.quadMetadataOverride : state.metadata;
        int low = (blockEntityId & 0xFFFF) | (renderType << 16);
        int framedBloomMarker = framedBloomBoost(state) ? FRAMED_BLOOM_BOOST_MARKER : 0;
        return (low & 0xFFFFFFFFL)
                | ((long) (metadata & 0xFFFF) << 32)
                | ((long) framedBloomMarker << 48);
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
        state.blockPos = pos;
    }

    public static IBlockAccess blockAccess() {
        return current().blockAccess;
    }

    public static BlockPos blockPos() {
        return current().blockPos;
    }

    public static boolean hasWorldBlockContext() {
        State state = current();
        return state.blockAccess != null && state.blockPos != null;
    }

    public static void setFramedMaterialOwner(boolean framedMaterialOwner) {
        current().framedMaterialOwner = framedMaterialOwner;
    }

    public static boolean isFramedMaterialOwner() {
        return current().framedMaterialOwner;
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
        return blockEmission(current());
    }

    public static void setFramedBloomBoost(boolean framedBloomBoost) {
        current().framedBloomBoost = framedBloomBoost;
    }

    public static boolean framedBloomBoost() {
        return framedBloomBoost(current());
    }

    public static void setQuadFramedBloomBoost(boolean framedBloomBoost) {
        State state = current();
        state.quadFramedBloomBoostOverride = framedBloomBoost;
        state.hasQuadFramedBloomBoostOverride = true;
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
        return state.crystalOnlyEmission || state.bloomOnlyEmission ? 0 : blockEmission(state);
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
        state.hasQuadFramedBloomBoostOverride = false;
    }

    private static boolean framedBloomBoost(State state) {
        return state.hasQuadFramedBloomBoostOverride
                ? state.quadFramedBloomBoostOverride
                : state.framedBloomBoost;
    }

    public static int midBlock(float x, float y, float z) {
        return midBlock(x, y, z, midBlockEmission());
    }

    public static int midBlock(float x, float y, float z, int emission) {
        State state = current();
        return midBlock(x, y, z, packedLocalPosition(state), emission);
    }

    public static int packedLocalPosition() {
        return packedLocalPosition(current());
    }

    public static int midBlock(float x, float y, float z, int packedLocalPosition, int emission) {
        return packMidBlock(
                (packedLocalPosition & 15) + 0.5f - x,
                ((packedLocalPosition >>> 4) & 15) + 0.5f - y,
                ((packedLocalPosition >>> 8) & 15) + 0.5f - z,
                emission
        );
    }

    public static int midBlockEmission() {
        State state = current();
        return state.bloomMaskFallback ? BLOOM_ONLY_MASK_EMISSION : blockEmission(state);
    }

    private static int packedLocalPosition(State state) {
        return state.localX | state.localY << 4 | state.localZ << 8;
    }

    private static int blockEmission(State state) {
        return state.hasQuadEmissionOverride ? clamp(state.quadEmissionOverride, 0, 15) : state.blockEmission;
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
        State state = current();
        setQuadAo(state, quadAo);
    }

    public static void setQuadAoIfEligible(float[] quadAo) {
        State state = current();
        if (state.separateAoEligible) {
            setQuadAo(state, quadAo);
        }
    }

    private static void setQuadAo(State state, float[] quadAo) {
        if (quadAo == null || quadAo.length < 4) {
            state.hasQuadAo = false;
            return;
        }
        state.quadAo0 = quadAo[0];
        state.quadAo1 = quadAo[1];
        state.quadAo2 = quadAo[2];
        state.quadAo3 = quadAo[3];
        state.hasQuadAo = true;
    }

    public static boolean hasQuadAo() {
        return current().hasQuadAo;
    }

    public static float separateAoForVertex(int vertexIndex, float fallback) {
        State state = current();
        if (!state.hasQuadAo) {
            return fallback;
        }
        return separateAoForVertex(state, vertexIndex, fallback);
    }

    public static float separateAoForVertexIfEligible(int vertexIndex, float fallback) {
        State state = current();
        if (!state.separateAoEligible || !state.hasQuadAo) {
            return Float.NaN;
        }
        return separateAoForVertex(state, vertexIndex, fallback);
    }

    public static boolean separateAoAvailable() {
        State state = current();
        return state.separateAoEligible && state.hasQuadAo;
    }

    private static float separateAoForVertex(State state, int vertexIndex, float fallback) {
        if (vertexIndex < 1 || vertexIndex > 4) {
            return fallback;
        }
        switch (vertexIndex) {
            case 1:
                return state.quadAo3;
            case 2:
                return state.quadAo2;
            case 3:
                return state.quadAo1;
            case 4:
                return state.quadAo0;
            default:
                return fallback;
        }
    }

    public static void clearQuadAo() {
        current().hasQuadAo = false;
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
        private boolean framedMaterialOwner;
        private boolean agricraftCrop;
        private int packedLightmap;
        private int blockEmission;
        private int blockAlpha = -1;
        private int customLiquidTint = -1;
        private boolean crystalOnlyEmission;
        private boolean bloomOnlyEmission;
        private boolean framedBloomBoost;
        private boolean quadFramedBloomBoostOverride;
        private int quadEmissionOverride;
        private int quadBlockEntityIdOverride;
        private short quadRenderTypeOverride;
        private short quadMetadataOverride;
        private boolean hasQuadEmissionOverride;
        private boolean hasQuadBlockEntityIdOverride;
        private boolean hasQuadRenderTypeOverride;
        private boolean hasQuadMetadataOverride;
        private boolean hasQuadFramedBloomBoostOverride;
        private boolean separateAoEligible;
        private boolean hasQuadAo;
        private float quadAo0;
        private float quadAo1;
        private float quadAo2;
        private float quadAo3;
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
            framedMaterialOwner = false;
            agricraftCrop = false;
            packedLightmap = 0;
            blockEmission = 0;
            blockAlpha = -1;
            customLiquidTint = -1;
            crystalOnlyEmission = false;
            bloomOnlyEmission = false;
            framedBloomBoost = false;
            quadFramedBloomBoostOverride = false;
            quadEmissionOverride = 0;
            quadBlockEntityIdOverride = 0;
            quadRenderTypeOverride = 0;
            quadMetadataOverride = 0;
            hasQuadEmissionOverride = false;
            hasQuadBlockEntityIdOverride = false;
            hasQuadRenderTypeOverride = false;
            hasQuadMetadataOverride = false;
            hasQuadFramedBloomBoostOverride = false;
            separateAoEligible = false;
            hasQuadAo = false;
            quadAo0 = 0.0F;
            quadAo1 = 0.0F;
            quadAo2 = 0.0F;
            quadAo3 = 0.0F;
            bloomMaskFallback = false;
            debugKind = "unknown";
            debugState = "unknown";
            debugEffectiveState = "unknown";
        }
    }
}
