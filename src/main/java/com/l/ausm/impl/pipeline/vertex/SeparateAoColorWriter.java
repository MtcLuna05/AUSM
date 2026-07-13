package com.l.ausm.impl.pipeline.vertex;

import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SeparateAoColorWriter {

    private SeparateAoColorWriter() {
    }

    public static void rewriteExistingColor(BufferBuilder bufferBuilder, float redMultiplier, float greenMultiplier, float blueMultiplier, int vertexIndex) {
        VertexFormat vertexFormat = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder);
        if (!ExtendedVertexFormats.isPipelineBlock(vertexFormat)
                || !PipelineContext.getInstance().shouldSeparateAo()
                || !BlockRenderContext.separateAoEligible()
                || !BlockRenderContext.hasQuadAo()) {
            return;
        }

        float inferredAo = Math.max(redMultiplier, Math.max(greenMultiplier, blueMultiplier));
        float ao = clamp(BlockRenderContext.separateAoForVertex(vertexIndex, inferredAo), 0.0f, 1.0f);
        if (ao <= 0.0001f) {
            return;
        }

        ByteBuffer byteBuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferByteBuffer(bufferBuilder);
        int colorOffset = com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((bufferBuilder), new String[] {"func_78909_a", "getColorIndex"}, new Class<?>[] {int.class}, -1, (vertexIndex)) * 4;
        if (byteBuffer == null) {
            return;
        }
        if (colorOffset < 0 || colorOffset + 4 > byteBuffer.capacity()) {
            return;
        }

        int red = byteBuffer.get(colorOffset) & 0xFF;
        int green = byteBuffer.get(colorOffset + 1) & 0xFF;
        int blue = byteBuffer.get(colorOffset + 2) & 0xFF;
        int oldAlpha = byteBuffer.get(colorOffset + 3) & 0xFF;
        int alpha = Math.min(oldAlpha, Math.round(ao * 255.0f));

        byteBuffer.put(colorOffset, (byte) normalizeColor(red, ao));
        byteBuffer.put(colorOffset + 1, (byte) normalizeColor(green, ao));
        byteBuffer.put(colorOffset + 2, (byte) normalizeColor(blue, ao));
        byteBuffer.put(colorOffset + 3, (byte) alpha);
        if (vertexIndex == 1) {
            BlockRenderContext.clearQuadAo();
        }
    }

    public static void rewriteForgeQuadData(BufferBuilder bufferBuilder, int[] quadData) {
        VertexFormat vertexFormat = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder);
        if (!ExtendedVertexFormats.isPipelineBlock(vertexFormat)
                || !PipelineContext.getInstance().shouldSeparateAo()
                || !BlockRenderContext.separateAoEligible()
                || !BlockRenderContext.hasQuadAo()
                || quadData == null) {
            return;
        }

        int vertexStride = quadData.length % ExtendedVertexFormats.integerSize(vertexFormat) == 0 ? ExtendedVertexFormats.integerSize(vertexFormat) : 7;
        int colorOffset = ExtendedVertexFormats.colorOffset(vertexFormat) / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int colorIndex = vertex * vertexStride + colorOffset;
            if (colorIndex < 0 || colorIndex >= quadData.length) {
                continue;
            }
            float inferredAo = inferredAo(quadData[colorIndex]);
            float ao = clamp(BlockRenderContext.separateAoForVertex(4 - vertex, inferredAo), 0.0f, 1.0f);
            quadData[colorIndex] = separateAoColor(quadData[colorIndex], ao);
        }
        BlockRenderContext.clearQuadAo();
    }

    private static int separateAoColor(int color, float ao) {
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                ? separateAoLittleEndian(color, ao)
                : separateAoBigEndian(color, ao);
    }

    private static float inferredAo(int color) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            int red = color & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = (color >> 16) & 0xFF;
            return clamp(Math.max(red, Math.max(green, blue)) / 255.0f, 0.0f, 1.0f);
        }
        int red = (color >> 24) & 0xFF;
        int green = (color >> 16) & 0xFF;
        int blue = (color >> 8) & 0xFF;
        return clamp(Math.max(red, Math.max(green, blue)) / 255.0f, 0.0f, 1.0f);
    }

    private static int separateAoLittleEndian(int color, float ao) {
        int red = color & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = (color >> 16) & 0xFF;
        int oldAlpha = (color >>> 24) & 0xFF;
        if (ao <= 0.0001f) {
            return color;
        }
        int alpha = Math.min(oldAlpha, Math.round(ao * 255.0f));
        return (alpha << 24)
                | (normalizeColor(blue, ao) << 16)
                | (normalizeColor(green, ao) << 8)
                | normalizeColor(red, ao);
    }

    private static int separateAoBigEndian(int color, float ao) {
        int red = (color >> 24) & 0xFF;
        int green = (color >> 16) & 0xFF;
        int blue = (color >> 8) & 0xFF;
        int oldAlpha = color & 0xFF;
        if (ao <= 0.0001f) {
            return color;
        }
        int alpha = Math.min(oldAlpha, Math.round(ao * 255.0f));
        return (normalizeColor(red, ao) << 24)
                | (normalizeColor(green, ao) << 16)
                | (normalizeColor(blue, ao) << 8)
                | alpha;
    }

    private static int normalizeColor(int color, float ao) {
        return Math.min(255, Math.round(color / ao));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
