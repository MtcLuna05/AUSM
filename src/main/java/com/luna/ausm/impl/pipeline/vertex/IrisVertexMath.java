package com.luna.ausm.impl.pipeline.vertex;

public final class IrisVertexMath {
    private IrisVertexMath() {
    }

    public static void computeFaceNormal(float[] out,
                                         float x0, float y0, float z0,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         float x3, float y3, float z3) {
        float dx0 = x2 - x0;
        float dy0 = y2 - y0;
        float dz0 = z2 - z0;
        float dx1 = x3 - x1;
        float dy1 = y3 - y1;
        float dz1 = z3 - z1;

        float nx = dy0 * dz1 - dz0 * dy1;
        float ny = dz0 * dx1 - dx0 * dz1;
        float nz = dx0 * dy1 - dy0 * dx1;
        float invLength = inverseSqrt(nx * nx + ny * ny + nz * nz);
        if (!isFinite(invLength) || invLength == 0.0f) {
            out[0] = 0.0f;
            out[1] = 1.0f;
            out[2] = 0.0f;
            return;
        }
        out[0] = nx * invLength;
        out[1] = ny * invLength;
        out[2] = nz * invLength;
    }

    public static int packNormal(float x, float y, float z) {
        return packSnormByte(x)
                | (packSnormByte(y) << 8)
                | (packSnormByte(z) << 16);
    }

    public static int computeTangent(float normalX, float normalY, float normalZ,
                                     float x0, float y0, float z0, float u0, float v0,
                                     float x1, float y1, float z1, float u1, float v1,
                                     float x2, float y2, float z2, float u2, float v2) {
        float edge1x = x1 - x0;
        float edge1y = y1 - y0;
        float edge1z = z1 - z0;
        float edge2x = x2 - x0;
        float edge2y = y2 - y0;
        float edge2z = z2 - z0;

        float deltaU1 = u1 - u0;
        float deltaV1 = v1 - v0;
        float deltaU2 = u2 - u0;
        float deltaV2 = v2 - v0;

        float denominator = deltaU1 * deltaV2 - deltaU2 * deltaV1;
        if (Math.abs(denominator) < 1.0e-8f || !isFinite(denominator)) {
            return fallbackTangent(normalX, normalY, normalZ);
        }
        float factor = 1.0f / denominator;

        float tangentX = factor * (deltaV2 * edge1x - deltaV1 * edge2x);
        float tangentY = factor * (deltaV2 * edge1y - deltaV1 * edge2y);
        float tangentZ = factor * (deltaV2 * edge1z - deltaV1 * edge2z);

        float tangentDotNormal = tangentX * normalX + tangentY * normalY + tangentZ * normalZ;
        tangentX -= normalX * tangentDotNormal;
        tangentY -= normalY * tangentDotNormal;
        tangentZ -= normalZ * tangentDotNormal;
        float tangentScale = inverseSqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
        if (!isFinite(tangentScale) || tangentScale == 0.0f) {
            return fallbackTangent(normalX, normalY, normalZ);
        }
        tangentX *= tangentScale;
        tangentY *= tangentScale;
        tangentZ *= tangentScale;

        float bitangentX = factor * (-deltaU2 * edge1x + deltaU1 * edge2x);
        float bitangentY = factor * (-deltaU2 * edge1y + deltaU1 * edge2y);
        float bitangentZ = factor * (-deltaU2 * edge1z + deltaU1 * edge2z);

        float predictedBitangentX = tangentY * normalZ - tangentZ * normalY;
        float predictedBitangentY = tangentZ * normalX - tangentX * normalZ;
        float predictedBitangentZ = tangentX * normalY - tangentY * normalX;
        // Handedness only depends on the dot product's sign. Normalizing the
        // bitangent adds a square root per polygon but multiplies it by a
        // non-negative scalar, so it cannot affect the result.
        float tangentW = bitangentX * predictedBitangentX
                + bitangentY * predictedBitangentY
                + bitangentZ * predictedBitangentZ < 0.0f ? -1.0f : 1.0f;

        return packSnormByte(tangentX)
                | (packSnormByte(tangentY) << 8)
                | (packSnormByte(tangentZ) << 16)
                | (packSnormByte(tangentW) << 24);
    }

    public static int computeSmoothTangent(float normalX, float normalY, float normalZ,
                                           float x0, float y0, float z0, float u0, float v0,
                                           float x1, float y1, float z1, float u1, float v1,
                                           float x2, float y2, float z2, float u2, float v2) {
        float d0 = x0 * normalX + y0 * normalY + z0 * normalZ;
        float d1 = x1 * normalX + y1 * normalY + z1 * normalZ;
        float d2 = x2 * normalX + y2 * normalY + z2 * normalZ;

        return computeTangent(normalX, normalY, normalZ,
                x0 - d0 * normalX, y0 - d0 * normalY, z0 - d0 * normalZ, u0, v0,
                x1 - d1 * normalX, y1 - d1 * normalY, z1 - d1 * normalZ, u1, v1,
                x2 - d2 * normalX, y2 - d2 * normalY, z2 - d2 * normalZ, u2, v2);
    }

    public static float unpackSnormByte(int value) {
        return (byte) (value & 0xFF) / 127.0f;
    }

    private static int packSnormByte(float value) {
        if (!isFinite(value)) {
            value = 0.0f;
        }
        value = Math.clamp(value, -1.0f, 1.0f);
        return (int) (value * 127.0f) & 0xFF;
    }

    private static float inverseSqrt(float value) {
        return value <= 1.0e-20f || !isFinite(value) ? 0.0f : (float) (1.0d / Math.sqrt(value));
    }

    private static int fallbackTangent(float normalX, float normalY, float normalZ) {
        if (!isFinite(normalX) || !isFinite(normalY) || !isFinite(normalZ)
                || normalX * normalX + normalY * normalY + normalZ * normalZ <= 1.0e-20f) {
            normalX = 0.0f;
            normalY = 1.0f;
            normalZ = 0.0f;
        }

        float refX = Math.abs(normalY) < 0.9f ? 0.0f : 1.0f;
        float refY = Math.abs(normalY) < 0.9f ? 1.0f : 0.0f;
        float refZ = 0.0f;
        float tangentX = refY * normalZ - refZ * normalY;
        float tangentY = refZ * normalX - refX * normalZ;
        float tangentZ = refX * normalY - refY * normalX;
        float scale = inverseSqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
        if (scale == 0.0f) {
            tangentX = 1.0f;
            tangentY = 0.0f;
            tangentZ = 0.0f;
        } else {
            tangentX *= scale;
            tangentY *= scale;
            tangentZ *= scale;
        }
        return packSnormByte(tangentX)
                | (packSnormByte(tangentY) << 8)
                | (packSnormByte(tangentZ) << 16)
                | (packSnormByte(1.0f) << 24);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
