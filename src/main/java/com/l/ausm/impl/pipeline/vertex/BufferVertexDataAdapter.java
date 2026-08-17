package com.l.ausm.impl.pipeline.vertex;

import java.nio.ByteBuffer;

/**
 * Classifies and adapts vanilla-shaped vertex payloads before a mixin writes
 * them into an extended pipeline buffer.
 */
public final class BufferVertexDataAdapter {
    private static final ThreadLocal<int[]> VERTEX_SCRATCH = ThreadLocal.withInitial(() -> new int[16]);

    private BufferVertexDataAdapter() {
    }

    public static int[] vertexScratch(int size) {
        int[] scratch = VERTEX_SCRATCH.get();
        if (scratch.length < size) {
            scratch = new int[size];
            VERTEX_SCRATCH.set(scratch);
        }
        return scratch;
    }

    public static void clearVertexScratchTail(int[] scratch, int from, int to) {
        if (scratch == null || from >= to) {
            return;
        }
        for (int index = Math.max(0, from); index < to; index++) {
            scratch[index] = 0;
        }
    }

    public static int pipelineBlockVertexStride(int[] vertexData, int targetStride) {
        int sourceInts = vertexData != null ? vertexData.length : 0;
        if (sourceInts <= 0) {
            return -1;
        }

        // BakedQuad is four vertices. A vanilla 1.12 quad is 28 ints, which is
        // also divisible by the 14-int pipeline stride, so classify by quad
        // vertex count before using divisibility. Some Forge/model pipelines
        // include a normal slot and produce 8-int vertices.
        if (sourceInts % 4 == 0 && sourceInts <= 4 * targetStride) {
            int quadStride = sourceInts / 4;
            if (quadStride == 7 || quadStride == 8 || quadStride == 14 || quadStride == targetStride) {
                return quadStride;
            }
        }
        if (sourceInts % 7 == 0 && looksLikeVanillaIntStride(vertexData, 7)) {
            return 7;
        }
        if (sourceInts % 8 == 0 && looksLikeVanillaIntStride(vertexData, 8)) {
            return 8;
        }
        if (sourceInts % 7 == 0 && sourceInts % targetStride != 0) {
            return 7;
        }
        if (sourceInts % 8 == 0 && sourceInts % targetStride != 0) {
            return 8;
        }
        if (sourceInts % 14 == 0) {
            return 14;
        }
        return sourceInts % targetStride == 0 ? targetStride : -1;
    }

    public static int pipelineBlockBulkStride(ByteBuffer source, int sourceBytes, int targetStride) {
        int vanillaStride = 7 * Integer.BYTES;
        int forgeNormalStride = 8 * Integer.BYTES;
        int optifineStride = 14 * Integer.BYTES;
        if (sourceBytes == 4 * vanillaStride) {
            return vanillaStride;
        }
        if (sourceBytes == 4 * forgeNormalStride) {
            return forgeNormalStride;
        }
        if (sourceBytes % vanillaStride == 0 && looksLikeVanillaByteStride(source, sourceBytes, vanillaStride)) {
            return vanillaStride;
        }
        if (sourceBytes % forgeNormalStride == 0 && looksLikeVanillaByteStride(source, sourceBytes, forgeNormalStride)) {
            return forgeNormalStride;
        }
        if (sourceBytes % optifineStride == 0) {
            return optifineStride;
        }
        if (sourceBytes % vanillaStride == 0) {
            return vanillaStride;
        }
        if (sourceBytes % forgeNormalStride == 0 && sourceBytes % targetStride != 0) {
            return forgeNormalStride;
        }
        return sourceBytes % targetStride == 0 ? targetStride : -1;
    }

    public static void sanitizeAgricraftCropVertex(int[] expandedData, int target, int sourceStride,
                                            boolean agricraftCrop, int packedLightmap) {
        if (!agricraftCrop
                || expandedData == null
                || (sourceStride != 7 && sourceStride != 8 && sourceStride != 14)
                || target < 0
                || target + 6 >= expandedData.length) {
            return;
        }
        // AgriCraft/InfinityLib emits dynamic crop quads through an ITEM-like
        // tessellator. If those quads are later treated as BLOCK vertices, UV0
        // or packed normal data lands in UV1/lightmap and appears as blue bands.
        expandedData[target + 6] = packedLightmap;
    }

    public static void copyVanillaEntityVertex(int[] sourceData, int source, int[] targetData, int target) {
        System.arraycopy(sourceData, source, targetData, target, 7);
    }

    private static boolean looksLikeVanillaIntStride(int[] data, int strideInts) {
        if (data == null || data.length < strideInts * 2 || data.length % strideInts != 0) {
            return false;
        }
        int vertices = Math.min(data.length / strideInts, 8);
        for (int vertex = 0; vertex < vertices; vertex++) {
            if (!looksLikeVanillaVertex(data, vertex * strideInts, strideInts)) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeVanillaByteStride(ByteBuffer data, int sourceBytes, int strideBytes) {
        if (data == null || sourceBytes < strideBytes * 2 || sourceBytes % strideBytes != 0) {
            return false;
        }
        int vertices = Math.min(sourceBytes / strideBytes, 8);
        for (int vertex = 0; vertex < vertices; vertex++) {
            if (!looksLikeVanillaVertex(data, vertex * strideBytes, strideBytes / Integer.BYTES)) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeVanillaVertex(int[] data, int base, int strideInts) {
        if (base < 0 || base + 6 >= data.length || strideInts < 7) {
            return false;
        }
        return looksLikePosition(data[base], data[base + 1], data[base + 2])
                && looksLikeColor(data[base + 3])
                && looksLikeUv(data[base + 4], data[base + 5]);
    }

    private static boolean looksLikeVanillaVertex(ByteBuffer data, int base, int strideInts) {
        if (base < 0 || base + 7 * Integer.BYTES > data.limit() || strideInts < 7) {
            return false;
        }
        return looksLikePosition(data.getInt(base), data.getInt(base + 4), data.getInt(base + 8))
                && looksLikeColor(data.getInt(base + 12))
                && looksLikeUv(data.getInt(base + 16), data.getInt(base + 20));
    }

    private static boolean looksLikePosition(int xBits, int yBits, int zBits) {
        float x = Float.intBitsToFloat(xBits);
        float y = Float.intBitsToFloat(yBits);
        float z = Float.intBitsToFloat(zBits);
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                && Math.abs(x) < 4096.0f
                && Math.abs(y) < 4096.0f
                && Math.abs(z) < 4096.0f;
    }

    private static boolean looksLikeUv(int uBits, int vBits) {
        float u = Float.intBitsToFloat(uBits);
        float v = Float.intBitsToFloat(vBits);
        return Float.isFinite(u) && Float.isFinite(v)
                && Math.abs(u) < 64.0f
                && Math.abs(v) < 64.0f;
    }

    private static boolean looksLikeColor(int color) {
        return ((color >>> 24) & 0xFF) > 0;
    }
}
