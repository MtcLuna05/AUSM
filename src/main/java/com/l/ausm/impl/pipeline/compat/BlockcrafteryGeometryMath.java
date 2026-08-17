package com.l.ausm.impl.pipeline.compat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Stateless byte-buffer and quad geometry operations used while projecting a
 * contained block's visual payload onto a Blockcraftery host shape.
 */
final class BlockcrafteryGeometryMath {
    private BlockcrafteryGeometryMath() {
    }

    static float[] uvBoundsValues(byte[] data, int offset, int stride, ByteOrder order) {
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            int vertexOffset = offset + vertex * stride;
            float u = bytesFloat(data, vertexOffset + 16, order);
            float v = bytesFloat(data, vertexOffset + 20, order);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        return new float[]{minU, maxU, minV, maxV};
    }

    static float[] normal(float[] first, float[] second, float[] third) {
        float[] firstEdge = difference(second, first);
        float[] secondEdge = difference(third, first);
        float x = firstEdge[1] * secondEdge[2] - firstEdge[2] * secondEdge[1];
        float y = firstEdge[2] * secondEdge[0] - firstEdge[0] * secondEdge[2];
        float z = firstEdge[0] * secondEdge[1] - firstEdge[1] * secondEdge[0];
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return length < 0.0001F ? null : new float[]{x / length, y / length, z / length};
    }

    static float squaredDistance(float[] first, float[] second) {
        float x = first[0] - second[0];
        float y = first[1] - second[1];
        float z = first[2] - second[2];
        return x * x + y * y + z * z;
    }

    static int dominantAxis(float[] vector) {
        if (vector == null) {
            return -1;
        }
        float x = Math.abs(vector[0]);
        float y = Math.abs(vector[1]);
        float z = Math.abs(vector[2]);
        return x >= y && x >= z ? 0 : y >= z ? 1 : 2;
    }

    static float component(float[] vector, int axis) {
        return vector[axis];
    }

    static float[] position(byte[] data, int offset, ByteOrder order) {
        return new float[]{
                bytesFloat(data, offset, order),
                bytesFloat(data, offset + Float.BYTES, order),
                bytesFloat(data, offset + 2 * Float.BYTES, order)
        };
    }

    static float[] difference(float[] end, float[] start) {
        return new float[]{end[0] - start[0], end[1] - start[1], end[2] - start[2]};
    }

    static float[] scaled(float[] vector, float scalar) {
        return new float[]{vector[0] * scalar, vector[1] * scalar, vector[2] * scalar};
    }

    static float connectedFacePriority(float[] normal) {
        // Resolve an exact wedge tie toward the top material without
        // overriding the normal-based assignment.
        return normal[1] > 0.5F ? 0.0001F : 0.0F;
    }

    static int faceGroup(float[] normal) {
        int axis = dominantAxis(normal);
        if (axis < 0) {
            return -1;
        }
        return axis * 2 + (normal[axis] < 0.0F ? 0 : 1);
    }

    static String quadPoints(byte[] data, int offset, int stride, ByteOrder order) {
        return point(position(data, offset, order)) + "/" + point(position(data, offset + stride, order))
                + "/" + point(position(data, offset + 2 * stride, order))
                + "/" + point(position(data, offset + 3 * stride, order));
    }

    static String point(float[] value) {
        return value == null ? "null" : String.format(Locale.ROOT, "%.3f,%.3f,%.3f", value[0], value[1], value[2]);
    }

    static int replaceByte(int value, int byteIndex, int replacement, ByteOrder order) {
        int shift = order == ByteOrder.BIG_ENDIAN ? (3 - byteIndex) * Byte.SIZE : byteIndex * Byte.SIZE;
        int mask = 0xFF << shift;
        return (value & ~mask) | ((replacement & 0xFF) << shift);
    }

    static String lightingValues(byte[] data, int offset, int stride, int colorOffset, int lightmapOffset) {
        StringBuilder result = new StringBuilder();
        for (int vertex = 0; vertex < 4; vertex++) {
            if (vertex > 0) {
                result.append(',');
            }
            int base = offset + vertex * stride;
            int r = data[base + colorOffset] & 0xFF;
            int g = data[base + colorOffset + 1] & 0xFF;
            int b = data[base + colorOffset + 2] & 0xFF;
            int a = data[base + colorOffset + 3] & 0xFF;
            int light = (data[base + lightmapOffset] & 0xFF)
                    | ((data[base + lightmapOffset + 1] & 0xFF) << 8)
                    | ((data[base + lightmapOffset + 2] & 0xFF) << 16)
                    | ((data[base + lightmapOffset + 3] & 0xFF) << 24);
            result.append(String.format(Locale.ROOT, "%02x%02x%02x/%02x@%08x", r, g, b, a, light));
        }
        return result.toString();
    }

    static String positionBounds(byte[] data, int startVertex, int vertices, int stride, ByteOrder order) {
        float[] bounds = emptyBounds();
        for (int vertex = startVertex; vertex < startVertex + vertices; vertex++) {
            int offset = vertex * stride;
            includeBounds(bounds, bytesFloat(data, offset, order), bytesFloat(data, offset + 4, order),
                    bytesFloat(data, offset + 8, order));
        }
        return bounds(bounds);
    }

    static String positionBounds(int[] data, int startVertex, int vertices, int intsPerVertex) {
        float[] bounds = emptyBounds();
        for (int vertex = startVertex; vertex < startVertex + vertices; vertex++) {
            int offset = vertex * intsPerVertex;
            includeBounds(bounds, Float.intBitsToFloat(data[offset]), Float.intBitsToFloat(data[offset + 1]),
                    Float.intBitsToFloat(data[offset + 2]));
        }
        return bounds(bounds);
    }

    static String uvBounds(byte[] data, int offset, int stride, ByteOrder order) {
        float[] bounds = uvBoundsValues(data, offset, stride, order);
        return String.format(Locale.ROOT, "%.5f..%.5f,%.5f..%.5f",
                bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    static String vector(float[] normal) {
        if (normal == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f", normal[0], normal[1], normal[2]);
    }

    static void copyOrientedQuad(int[] containedVisuals, byte[] contained, byte[] host,
                                 int containedQuad, int hostQuad, int[] result,
                                 int stride, int intsPerVertex, ByteOrder order) {
        int[] sourceForHost = orientedSourceVertices(contained, host, containedQuad, hostQuad, stride, order);
        int sourceQuad = containedQuad * 4 * intsPerVertex;
        int destinationQuad = hostQuad * 4 * intsPerVertex;
        for (int hostVertex = 0; hostVertex < 4; hostVertex++) {
            int source = sourceQuad + sourceForHost[hostVertex] * intsPerVertex;
            int destination = destinationQuad + hostVertex * intsPerVertex;
            System.arraycopy(containedVisuals, source, result, destination, intsPerVertex);
        }
    }

    static int closestQuad(float[] hostNormal, float[][] containedNormals, float[] containedAreas,
                           int hostQuad, boolean preferLargestMatchingFace) {
        int fallback = hostQuad % containedNormals.length;
        if (hostNormal == null) {
            return fallback;
        }
        int best = fallback;
        float bestDot = -Float.MAX_VALUE;
        float bestArea = -Float.MAX_VALUE;
        for (int quad = 0; quad < containedNormals.length; quad++) {
            float[] candidate = containedNormals[quad];
            if (candidate == null) {
                continue;
            }
            float dot = hostNormal[0] * candidate[0]
                    + hostNormal[1] * candidate[1]
                    + hostNormal[2] * candidate[2];
            float area = containedAreas != null ? containedAreas[quad] : 0.0F;
            if (dot > bestDot + 0.0001F
                    || (preferLargestMatchingFace && Math.abs(dot - bestDot) <= 0.0001F && area > bestArea)) {
                bestDot = dot;
                bestArea = area;
                best = quad;
            }
        }
        return best;
    }

    static float area(byte[] data, int offset, int stride, ByteOrder order) {
        float ax = bytesFloat(data, offset, order);
        float ay = bytesFloat(data, offset + 4, order);
        float az = bytesFloat(data, offset + 8, order);
        float bx = bytesFloat(data, offset + stride, order);
        float by = bytesFloat(data, offset + stride + 4, order);
        float bz = bytesFloat(data, offset + stride + 8, order);
        float cx = bytesFloat(data, offset + stride * 2, order);
        float cy = bytesFloat(data, offset + stride * 2 + 4, order);
        float cz = bytesFloat(data, offset + stride * 2 + 8, order);
        float ux = bx - ax;
        float uy = by - ay;
        float uz = bz - az;
        float vx = cx - ax;
        float vy = cy - ay;
        float vz = cz - az;
        return (float) Math.sqrt((uy * vz - uz * vy) * (uy * vz - uz * vy)
                + (uz * vx - ux * vz) * (uz * vx - ux * vz)
                + (ux * vy - uy * vx) * (ux * vy - uy * vx));
    }

    static float[] normal(byte[] data, int offset, int stride, ByteOrder order) {
        float ax = bytesFloat(data, offset, order);
        float ay = bytesFloat(data, offset + 4, order);
        float az = bytesFloat(data, offset + 8, order);
        float bx = bytesFloat(data, offset + stride, order);
        float by = bytesFloat(data, offset + stride + 4, order);
        float bz = bytesFloat(data, offset + stride + 8, order);
        float cx = bytesFloat(data, offset + stride * 2, order);
        float cy = bytesFloat(data, offset + stride * 2 + 4, order);
        float cz = bytesFloat(data, offset + stride * 2 + 8, order);
        float ux = bx - ax;
        float uy = by - ay;
        float uz = bz - az;
        float vx = cx - ax;
        float vy = cy - ay;
        float vz = cz - az;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 0.0001F) {
            return null;
        }
        return new float[]{nx / length, ny / length, nz / length};
    }

    static byte[] read(ByteBuffer source, int offset, int length) {
        byte[] bytes = new byte[length];
        ByteBuffer copy = source.duplicate();
        copy.position(offset);
        copy.get(bytes);
        return bytes;
    }

    static int[] integers(byte[] bytes, ByteOrder order) {
        if (bytes.length % Integer.BYTES != 0) {
            return new int[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(order);
        int[] result = new int[bytes.length / Integer.BYTES];
        for (int index = 0; index < result.length; index++) {
            result[index] = buffer.getInt();
        }
        return result;
    }

    static byte[] bytes(int[] values, ByteOrder order) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Integer.BYTES).order(order);
        for (int value : values) {
            buffer.putInt(value);
        }
        return buffer.array();
    }

    static float bytesFloat(byte[] bytes, int offset, ByteOrder order) {
        return ByteBuffer.wrap(bytes, offset, Float.BYTES).order(order).getFloat();
    }

    static int bytesInt(byte[] bytes, int offset, ByteOrder order) {
        return ByteBuffer.wrap(bytes, offset, Integer.BYTES).order(order).getInt();
    }

    private static int[] orientedSourceVertices(byte[] contained, byte[] host, int containedQuad,
                                                int hostQuad, int stride, ByteOrder order) {
        int containedBase = containedQuad * 4 * stride;
        int hostBase = hostQuad * 4 * stride;
        int[] best = new int[]{0, 1, 2, 3};
        float bestScore = -Float.MAX_VALUE;
        for (int reversed = 0; reversed <= 1; reversed++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                float score = 0.0F;
                for (int vertex = 0; vertex < 4; vertex++) {
                    int next = (vertex + 1) & 3;
                    int sourceVertex = sourceVertex(vertex, rotation, reversed != 0);
                    int sourceNext = sourceVertex(next, rotation, reversed != 0);
                    score += directionDot(host, hostBase + vertex * stride, hostBase + next * stride,
                            contained, containedBase + sourceVertex * stride,
                            containedBase + sourceNext * stride, order);
                }
                if (score > bestScore) {
                    bestScore = score;
                    for (int vertex = 0; vertex < 4; vertex++) {
                        best[vertex] = sourceVertex(vertex, rotation, reversed != 0);
                    }
                }
            }
        }
        return best;
    }

    private static int sourceVertex(int hostVertex, int rotation, boolean reversed) {
        return reversed ? (rotation - hostVertex + 4) & 3 : (rotation + hostVertex) & 3;
    }

    private static float directionDot(byte[] first, int firstStart, int firstEnd,
                                      byte[] second, int secondStart, int secondEnd, ByteOrder order) {
        float firstX = bytesFloat(first, firstEnd, order) - bytesFloat(first, firstStart, order);
        float firstY = bytesFloat(first, firstEnd + 4, order) - bytesFloat(first, firstStart + 4, order);
        float firstZ = bytesFloat(first, firstEnd + 8, order) - bytesFloat(first, firstStart + 8, order);
        float secondX = bytesFloat(second, secondEnd, order) - bytesFloat(second, secondStart, order);
        float secondY = bytesFloat(second, secondEnd + 4, order) - bytesFloat(second, secondStart + 4, order);
        float secondZ = bytesFloat(second, secondEnd + 8, order) - bytesFloat(second, secondStart + 8, order);
        float firstLength = (float) Math.sqrt(firstX * firstX + firstY * firstY + firstZ * firstZ);
        float secondLength = (float) Math.sqrt(secondX * secondX + secondY * secondY + secondZ * secondZ);
        if (firstLength < 0.0001F || secondLength < 0.0001F) {
            return 0.0F;
        }
        return (firstX * secondX + firstY * secondY + firstZ * secondZ) / (firstLength * secondLength);
    }

    private static float[] emptyBounds() {
        return new float[]{Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
    }

    private static void includeBounds(float[] bounds, float x, float y, float z) {
        bounds[0] = Math.min(bounds[0], x);
        bounds[1] = Math.max(bounds[1], x);
        bounds[2] = Math.min(bounds[2], y);
        bounds[3] = Math.max(bounds[3], y);
        bounds[4] = Math.min(bounds[4], z);
        bounds[5] = Math.max(bounds[5], z);
    }

    private static String bounds(float[] values) {
        return String.format(Locale.ROOT, "%.3f..%.3f,%.3f..%.3f,%.3f..%.3f",
                values[0], values[1], values[2], values[3], values[4], values[5]);
    }
}
