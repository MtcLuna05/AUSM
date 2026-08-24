package com.luna.ausm.impl.pipeline.vertex;

import java.nio.ByteBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;

/**
 * Computes the derived normal, tangent, midpoint, and local-position attributes
 * for complete pipeline polygons.
 */
public final class PipelineVertexAttributeWriter {
    private static final ThreadLocal<float[]> NORMAL_SCRATCH = ThreadLocal.withInitial(() -> new float[3]);

    private PipelineVertexAttributeWriter() {
    }

    public static void writeBlockPolygon(ByteBuffer buffer, VertexFormat format, int firstVertex, int vertexAmount) {
        int stride = ExtendedVertexFormats.size(format);
        int base = firstVertex * stride;
        if (vertexAmount < 3 || base < 0
                || base + (vertexAmount - 1) * stride
                + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + Integer.BYTES > buffer.capacity()) {
            return;
        }

        PolygonVertices vertices = PolygonVertices.read(buffer, base, stride, vertexAmount);
        float[] normal = NORMAL_SCRATCH.get();
        vertices.computeFaceNormal(normal);
        int packedNormal = IrisVertexMath.packNormal(normal[0], normal[1], normal[2]);
        int packedTangent = vertices.computeTangent(normal);
        float midU = vertices.midU(vertexAmount);
        float midV = vertices.midV(vertexAmount);
        int packedLocalPosition = BlockRenderContext.packedLocalPosition();
        int midBlockEmission = BlockRenderContext.midBlockEmission();

        for (int vertex = 0; vertex < vertexAmount; vertex++) {
            int vertexBase = base + vertex * stride;
            int tangent = packedTangent;
            if (vertexAmount == 3) {
                int vertexNormal = buffer.getInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);
                tangent = vertices.computeSmoothTangent(vertexNormal);
            } else {
                buffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET, packedNormal);
            }
            buffer.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET, midU);
            buffer.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET + Float.BYTES, midV);
            buffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET, tangent);
            buffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET, BlockRenderContext.midBlock(
                    buffer.getFloat(vertexBase),
                    buffer.getFloat(vertexBase + Float.BYTES),
                    buffer.getFloat(vertexBase + 2 * Float.BYTES),
                    packedLocalPosition,
                    midBlockEmission
            ));
        }
    }

    public static void writeEntityPolygon(ByteBuffer buffer, VertexFormat format, int firstVertex, int vertexAmount) {
        int stride = ExtendedVertexFormats.size(format);
        int base = firstVertex * stride;
        if (vertexAmount < 3 || base < 0
                || base + (vertexAmount - 1) * stride
                + ExtendedVertexFormats.PIPELINE_ENTITY_TANGENT_OFFSET + Integer.BYTES > buffer.capacity()) {
            return;
        }

        PolygonVertices vertices = PolygonVertices.read(buffer, base, stride, vertexAmount);
        float[] normal = NORMAL_SCRATCH.get();
        vertices.computeFaceNormal(normal);
        int packedNormal = IrisVertexMath.packNormal(normal[0], normal[1], normal[2]);
        int packedTangent = vertices.computeTangent(normal);
        float midU = vertices.midU(vertexAmount);
        float midV = vertices.midV(vertexAmount);

        for (int vertex = 0; vertex < vertexAmount; vertex++) {
            int vertexBase = base + vertex * stride;
            int tangent = packedTangent;
            if (vertexAmount == 3) {
                int vertexNormal = buffer.getInt(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_NORMAL_OFFSET);
                tangent = vertices.computeSmoothTangent(vertexNormal);
            } else {
                buffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_NORMAL_OFFSET, packedNormal);
            }
            buffer.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_MID_TEX_COORD_OFFSET, midU);
            buffer.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_MID_TEX_COORD_OFFSET + Float.BYTES, midV);
            buffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_TANGENT_OFFSET, tangent);
        }
    }

    private record PolygonVertices(
            float v0x, float v0y, float v0z, float v0u, float v0v,
            float v1x, float v1y, float v1z, float v1u, float v1v,
            float v2x, float v2y, float v2z, float v2u, float v2v,
            float v3x, float v3y, float v3z, float v3u, float v3v) {

        private static PolygonVertices read(ByteBuffer buffer, int base, int stride, int vertexAmount) {
            int lastVertexOffset = vertexAmount == 4 ? 3 * stride : 2 * stride;
            return new PolygonVertices(
                    buffer.getFloat(base), buffer.getFloat(base + 4), buffer.getFloat(base + 8),
                    buffer.getFloat(base + 16), buffer.getFloat(base + 20),
                    buffer.getFloat(base + stride), buffer.getFloat(base + stride + 4),
                    buffer.getFloat(base + stride + 8), buffer.getFloat(base + stride + 16),
                    buffer.getFloat(base + stride + 20),
                    buffer.getFloat(base + 2 * stride), buffer.getFloat(base + 2 * stride + 4),
                    buffer.getFloat(base + 2 * stride + 8), buffer.getFloat(base + 2 * stride + 16),
                    buffer.getFloat(base + 2 * stride + 20),
                    buffer.getFloat(base + lastVertexOffset), buffer.getFloat(base + lastVertexOffset + 4),
                    buffer.getFloat(base + lastVertexOffset + 8), buffer.getFloat(base + lastVertexOffset + 16),
                    buffer.getFloat(base + lastVertexOffset + 20)
            );
        }

        private void computeFaceNormal(float[] normal) {
            IrisVertexMath.computeFaceNormal(normal,
                    v0x, v0y, v0z,
                    v1x, v1y, v1z,
                    v2x, v2y, v2z,
                    v3x, v3y, v3z);
        }

        private int computeTangent(float[] normal) {
            return IrisVertexMath.computeTangent(normal[0], normal[1], normal[2],
                    v0x, v0y, v0z, v0u, v0v,
                    v1x, v1y, v1z, v1u, v1v,
                    v2x, v2y, v2z, v2u, v2v);
        }

        private int computeSmoothTangent(int packedNormal) {
            return IrisVertexMath.computeSmoothTangent(
                    IrisVertexMath.unpackSnormByte(packedNormal),
                    IrisVertexMath.unpackSnormByte(packedNormal >> 8),
                    IrisVertexMath.unpackSnormByte(packedNormal >> 16),
                    v0x, v0y, v0z, v0u, v0v,
                    v1x, v1y, v1z, v1u, v1v,
                    v2x, v2y, v2z, v2u, v2v);
        }

        private float midU(int vertexAmount) {
            return vertexAmount == 4 ? (v0u + v1u + v2u + v3u) * 0.25f : (v0u + v1u + v2u) / 3.0f;
        }

        private float midV(int vertexAmount) {
            return vertexAmount == 4 ? (v0v + v1v + v2v + v3v) * 0.25f : (v0v + v1v + v2v) / 3.0f;
        }
    }
}
