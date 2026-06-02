package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.pipeline.vertex.IrisVertexMath;
import com.l.ausm.impl.pipeline.vertex.SeparateAoColorWriter;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin {

    @Shadow
    private ByteBuffer byteBuffer;

    @Shadow
    private IntBuffer rawIntBuffer;

    @Shadow
    private int vertexCount;

    @Shadow
    private int drawMode;

    @Shadow
    private VertexFormatElement vertexFormatElement;

    @Shadow
    private int vertexFormatIndex;

    @Shadow
    private VertexFormat vertexFormat;

    @Shadow
    public native int getColorIndex(int vertexIndex);

    @Shadow
    private native void growBuffer(int size);

    @Shadow
    private native int getBufferSize();

    @ModifyVariable(method = "begin", at = @At("HEAD"), argsOnly = true)
    private VertexFormat ausm$usePipelineEntityFormat(VertexFormat original) {
        if (original == DefaultVertexFormats.ITEM && PipelineContext.getInstance().shouldUsePipelineEntityFormat()) {
            return ExtendedVertexFormats.PIPELINE_ENTITY;
        }
        return original;
    }

    @Inject(method = "putBulkData", at = @At("HEAD"), cancellable = true)
    private void ausm$expandBulkVanillaVertexData(ByteBuffer sourceBuffer, CallbackInfo ci) {
        if (!ExtendedVertexFormats.isPipelineBlock(vertexFormat) || sourceBuffer == null) {
            return;
        }

        ByteBuffer source = sourceBuffer.duplicate();
        source.order(byteBuffer.order());
        int sourceBytes = source.remaining();
        int targetStride = vertexFormat.getSize();
        if (sourceBytes % targetStride == 0) {
            return;
        }

        int optifineStride = 14 * Integer.BYTES;
        int vanillaStride = 7 * Integer.BYTES;
        int sourceStride;
        if (sourceBytes % optifineStride == 0) {
            sourceStride = optifineStride;
        } else if (sourceBytes % vanillaStride == 0) {
            sourceStride = vanillaStride;
        } else {
            return;
        }

        int vertexBase = vertexCount;
        int vertexTotal = sourceBytes / sourceStride;
        int targetIntStride = vertexFormat.getIntegerSize();
        int[] expandedData = new int[vertexTotal * targetIntStride];
        for (int vertex = 0; vertex < vertexTotal; vertex++) {
            int target = vertex * targetIntStride;
            for (int sourceInt = 0; sourceInt < sourceStride / Integer.BYTES; sourceInt++) {
                expandedData[target + sourceInt] = source.getInt();
            }
            expandedData[target + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET / Integer.BYTES] = packedEntity();
            expandedData[target + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET / Integer.BYTES + 1] = packedEntityHigh();
            expandedData[target + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET / Integer.BYTES] = BlockRenderContext.midBlock(
                    Float.intBitsToFloat(expandedData[target]),
                    Float.intBitsToFloat(expandedData[target + 1]),
                    Float.intBitsToFloat(expandedData[target + 2])
            );
        }

        growBuffer(expandedData.length * Integer.BYTES + targetStride);
        rawIntBuffer.position(getBufferSize());
        rawIntBuffer.put(expandedData);
        vertexCount += vertexTotal;

        for (int vertex = 0; vertex + 3 < vertexTotal; vertex += 4) {
            ausm$writeDerivedBlockAttributesForPolygon(vertexBase + vertex, 4);
        }

        ausm$resetPipelineVertexCursor();
        ci.cancel();
    }

    @Inject(method = "addVertexData", at = @At("HEAD"), cancellable = true)
    private void ausm$expandVanillaQuadData(int[] vertexData, CallbackInfo ci) {
        if (vertexData == null) {
            return;
        }

        if (ExtendedVertexFormats.isPipelineEntity(vertexFormat)) {
            ausm$expandPipelineEntityVertexData(vertexData, ci);
            return;
        }

        if (!ExtendedVertexFormats.isPipelineBlock(vertexFormat)) {
            return;
        }

        int targetStride = vertexFormat.getIntegerSize();
        int sourceStride;
        if (vertexData.length % targetStride == 0) {
            sourceStride = targetStride;
        } else if (vertexData.length % 14 == 0) {
            sourceStride = 14;
        } else if (vertexData.length % 7 == 0) {
            sourceStride = 7;
        } else {
            return;
        }

        int vertexBase = vertexCount;
        int vertexTotal = vertexData.length / sourceStride;
        int[] expandedData = new int[vertexTotal * targetStride];
        for (int vertex = 0; vertex < vertexTotal; vertex++) {
            int source = vertex * sourceStride;
            int target = vertex * targetStride;
            System.arraycopy(vertexData, source, expandedData, target, Math.min(sourceStride, targetStride));
            expandedData[target + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET / Integer.BYTES] = packedEntity();
            expandedData[target + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET / Integer.BYTES + 1] = packedEntityHigh();
            expandedData[target + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET / Integer.BYTES] = BlockRenderContext.midBlock(
                    Float.intBitsToFloat(expandedData[target]),
                    Float.intBitsToFloat(expandedData[target + 1]),
                    Float.intBitsToFloat(expandedData[target + 2])
            );
        }

        growBuffer(expandedData.length * Integer.BYTES + vertexFormat.getSize());
        rawIntBuffer.position(getBufferSize());
        rawIntBuffer.put(expandedData);
        vertexCount += vertexTotal;

        for (int vertex = 0; vertex + 3 < vertexTotal; vertex += 4) {
            ausm$writeDerivedBlockAttributesForPolygon(vertexBase + vertex, 4);
        }

        ausm$resetPipelineVertexCursor();
        ci.cancel();
    }

    @Inject(method = "endVertex", at = @At("HEAD"))
    private void ausm$writeBlockEntityAttribute(CallbackInfo ci) {
        if (!ExtendedVertexFormats.isPipelineBlock(vertexFormat) && !ExtendedVertexFormats.isPipelineEntity(vertexFormat)) {
            return;
        }

        int entityOffset = ExtendedVertexFormats.isPipelineEntity(vertexFormat)
                ? ExtendedVertexFormats.PIPELINE_ENTITY_MC_ENTITY_OFFSET
                : ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET;
        int offset = vertexCount * vertexFormat.getSize() + entityOffset;
        if (offset < 0 || offset + 8 > byteBuffer.capacity()) {
            return;
        }

        short entityId = (short) (ExtendedVertexFormats.isPipelineEntity(vertexFormat)
                ? PipelineContext.getInstance().currentEntityId()
                : BlockRenderContext.blockEntityId());
        byteBuffer.putShort(offset, entityId);
        byteBuffer.putShort(offset + 2, BlockRenderContext.renderType());
        byteBuffer.putShort(offset + 4, ExtendedVertexFormats.isPipelineEntity(vertexFormat) ? (short) 0 : BlockRenderContext.metadata());
        byteBuffer.putShort(offset + 6, (short) 0);
    }

    @Inject(method = "endVertex", at = @At("RETURN"))
    private void ausm$writeDerivedBlockAttributes(CallbackInfo ci) {
        if (!ExtendedVertexFormats.isPipelineBlock(vertexFormat)) {
            return;
        }

        if (drawMode == GL11.GL_QUADS && vertexCount >= 4 && vertexCount % 4 == 0) {
            ausm$writeDerivedBlockAttributesForPolygon(vertexCount - 4, 4);
            ausm$resetPipelineVertexCursor();
        } else if (drawMode == GL11.GL_TRIANGLES && vertexCount >= 3 && vertexCount % 3 == 0) {
            ausm$writeDerivedBlockAttributesForPolygon(vertexCount - 3, 3);
            ausm$resetPipelineVertexCursor();
        }
    }

    @Inject(method = "endVertex", at = @At("RETURN"))
    private void ausm$writeDerivedEntityAttributes(CallbackInfo ci) {
        if (!ExtendedVertexFormats.isPipelineEntity(vertexFormat)) {
            return;
        }

        if (drawMode == GL11.GL_QUADS && vertexCount >= 4 && vertexCount % 4 == 0) {
            ausm$writeDerivedEntityAttributesForPolygon(vertexCount - 4, 4);
            ausm$resetPipelineVertexCursor();
        } else if (drawMode == GL11.GL_TRIANGLES && vertexCount >= 3 && vertexCount % 3 == 0) {
            ausm$writeDerivedEntityAttributesForPolygon(vertexCount - 3, 3);
            ausm$resetPipelineVertexCursor();
        }
    }

    @Inject(method = "endVertex", at = @At("RETURN"))
    private void ausm$resetPipelineVertexCursorAfterNonQuad(CallbackInfo ci) {
        if (ExtendedVertexFormats.isPipelineBlock(vertexFormat)
                && !((drawMode == GL11.GL_QUADS && vertexCount % 4 == 0)
                || (drawMode == GL11.GL_TRIANGLES && vertexCount % 3 == 0))) {
            ausm$resetPipelineVertexCursor();
        }
    }

    private void ausm$resetPipelineVertexCursor() {
        vertexFormatIndex = 0;
        vertexFormatElement = vertexFormat.getElement(0);
    }

    private void ausm$expandPipelineEntityVertexData(int[] vertexData, CallbackInfo ci) {
        int targetStride = vertexFormat.getIntegerSize();
        int sourceStride;
        if (vertexData.length % targetStride == 0) {
            sourceStride = targetStride;
        } else if (vertexData.length % 7 == 0) {
            sourceStride = 7;
        } else {
            return;
        }

        int vertexBase = vertexCount;
        int vertexTotal = vertexData.length / sourceStride;
        int[] expandedData = new int[vertexTotal * targetStride];
        for (int vertex = 0; vertex < vertexTotal; vertex++) {
            int source = vertex * sourceStride;
            int target = vertex * targetStride;
            if (sourceStride == targetStride) {
                System.arraycopy(vertexData, source, expandedData, target, targetStride);
            } else {
                copyVanillaEntityVertex(vertexData, source, expandedData, target);
            }
            expandedData[target + ExtendedVertexFormats.PIPELINE_ENTITY_MC_ENTITY_OFFSET / Integer.BYTES] =
                    PipelineContext.getInstance().currentEntityId() & 0xFFFF;
            expandedData[target + ExtendedVertexFormats.PIPELINE_ENTITY_MC_ENTITY_OFFSET / Integer.BYTES + 1] = 0;
        }

        growBuffer(expandedData.length * Integer.BYTES + vertexFormat.getSize());
        rawIntBuffer.position(getBufferSize());
        rawIntBuffer.put(expandedData);
        vertexCount += vertexTotal;

        for (int vertex = 0; vertex + 3 < vertexTotal; vertex += 4) {
            ausm$writeDerivedEntityAttributesForPolygon(vertexBase + vertex, 4);
        }

        ausm$resetPipelineVertexCursor();
        ci.cancel();
    }

    private static void copyVanillaEntityVertex(int[] sourceData, int source, int[] targetData, int target) {
        targetData[target] = sourceData[source];
        targetData[target + 1] = sourceData[source + 1];
        targetData[target + 2] = sourceData[source + 2];
        targetData[target + 3] = sourceData[source + 3];
        targetData[target + 4] = sourceData[source + 4];
        targetData[target + 5] = sourceData[source + 5];
        targetData[target + 6] = sourceData[source + 6];
    }

    private void ausm$writeDerivedBlockAttributesForPolygon(int firstVertex, int vertexAmount) {
        int stride = vertexFormat.getSize();
        int base = firstVertex * stride;
        if (vertexAmount < 3 || base < 0 || base + (vertexAmount - 1) * stride + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 4 > byteBuffer.capacity()) {
            return;
        }

        float v0x = byteBuffer.getFloat(base);
        float v0y = byteBuffer.getFloat(base + 4);
        float v0z = byteBuffer.getFloat(base + 8);
        float v1x = byteBuffer.getFloat(base + stride);
        float v1y = byteBuffer.getFloat(base + stride + 4);
        float v1z = byteBuffer.getFloat(base + stride + 8);
        float v2x = byteBuffer.getFloat(base + 2 * stride);
        float v2y = byteBuffer.getFloat(base + 2 * stride + 4);
        float v2z = byteBuffer.getFloat(base + 2 * stride + 8);
        int lastVertexOffset = vertexAmount == 4 ? 3 * stride : 2 * stride;
        float v3x = byteBuffer.getFloat(base + lastVertexOffset);
        float v3y = byteBuffer.getFloat(base + lastVertexOffset + 4);
        float v3z = byteBuffer.getFloat(base + lastVertexOffset + 8);

        float v0u = byteBuffer.getFloat(base + 16);
        float v0v = byteBuffer.getFloat(base + 20);
        float v1u = byteBuffer.getFloat(base + stride + 16);
        float v1v = byteBuffer.getFloat(base + stride + 20);
        float v2u = byteBuffer.getFloat(base + 2 * stride + 16);
        float v2v = byteBuffer.getFloat(base + 2 * stride + 20);
        float v3u = byteBuffer.getFloat(base + lastVertexOffset + 16);
        float v3v = byteBuffer.getFloat(base + lastVertexOffset + 20);

        float[] normal = new float[3];
        IrisVertexMath.computeFaceNormal(normal,
                v0x, v0y, v0z,
                v1x, v1y, v1z,
                v2x, v2y, v2z,
                v3x, v3y, v3z);
        int packedNormal = IrisVertexMath.packNormal(normal[0], normal[1], normal[2]);
        int packedTangent = IrisVertexMath.computeTangent(normal[0], normal[1], normal[2],
                v0x, v0y, v0z, v0u, v0v,
                v1x, v1y, v1z, v1u, v1v,
                v2x, v2y, v2z, v2u, v2v);
        float midU = vertexAmount == 4 ? (v0u + v1u + v2u + v3u) * 0.25f : (v0u + v1u + v2u) / 3.0f;
        float midV = vertexAmount == 4 ? (v0v + v1v + v2v + v3v) * 0.25f : (v0v + v1v + v2v) / 3.0f;

        for (int vertex = 0; vertex < vertexAmount; vertex++) {
            int vertexBase = base + vertex * stride;
            int tangent = packedTangent;
            if (vertexAmount == 3) {
                int vertexNormal = byteBuffer.getInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);
                tangent = IrisVertexMath.computeSmoothTangent(IrisVertexMath.unpackSnormByte(vertexNormal),
                        IrisVertexMath.unpackSnormByte(vertexNormal >> 8),
                        IrisVertexMath.unpackSnormByte(vertexNormal >> 16),
                        v0x, v0y, v0z, v0u, v0v,
                        v1x, v1y, v1z, v1u, v1v,
                        v2x, v2y, v2z, v2u, v2v);
            } else {
                byteBuffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET, packedNormal);
            }
            byteBuffer.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET, midU);
            byteBuffer.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET + 4, midV);
            byteBuffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET, tangent);
            byteBuffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET, BlockRenderContext.midBlock(
                    byteBuffer.getFloat(vertexBase),
                    byteBuffer.getFloat(vertexBase + 4),
                    byteBuffer.getFloat(vertexBase + 8)
            ));
        }
    }

    private static int packedEntity() {
        return (BlockRenderContext.blockEntityId() & 0xFFFF) | (BlockRenderContext.renderType() << 16);
    }

    private static int packedEntityHigh() {
        return BlockRenderContext.metadata() & 0xFFFF;
    }

    private void ausm$writeDerivedEntityAttributesForPolygon(int firstVertex, int vertexAmount) {
        int stride = vertexFormat.getSize();
        int base = firstVertex * stride;
        if (vertexAmount < 3 || base < 0 || base + (vertexAmount - 1) * stride + ExtendedVertexFormats.PIPELINE_ENTITY_TANGENT_OFFSET + 4 > byteBuffer.capacity()) {
            return;
        }

        float v0x = byteBuffer.getFloat(base);
        float v0y = byteBuffer.getFloat(base + 4);
        float v0z = byteBuffer.getFloat(base + 8);
        float v1x = byteBuffer.getFloat(base + stride);
        float v1y = byteBuffer.getFloat(base + stride + 4);
        float v1z = byteBuffer.getFloat(base + stride + 8);
        float v2x = byteBuffer.getFloat(base + 2 * stride);
        float v2y = byteBuffer.getFloat(base + 2 * stride + 4);
        float v2z = byteBuffer.getFloat(base + 2 * stride + 8);
        int lastVertexOffset = vertexAmount == 4 ? 3 * stride : 2 * stride;
        float v3x = byteBuffer.getFloat(base + lastVertexOffset);
        float v3y = byteBuffer.getFloat(base + lastVertexOffset + 4);
        float v3z = byteBuffer.getFloat(base + lastVertexOffset + 8);

        float v0u = byteBuffer.getFloat(base + 16);
        float v0v = byteBuffer.getFloat(base + 20);
        float v1u = byteBuffer.getFloat(base + stride + 16);
        float v1v = byteBuffer.getFloat(base + stride + 20);
        float v2u = byteBuffer.getFloat(base + 2 * stride + 16);
        float v2v = byteBuffer.getFloat(base + 2 * stride + 20);
        float v3u = byteBuffer.getFloat(base + lastVertexOffset + 16);
        float v3v = byteBuffer.getFloat(base + lastVertexOffset + 20);

        float[] normal = new float[3];
        IrisVertexMath.computeFaceNormal(normal,
                v0x, v0y, v0z,
                v1x, v1y, v1z,
                v2x, v2y, v2z,
                v3x, v3y, v3z);
        int packedNormal = IrisVertexMath.packNormal(normal[0], normal[1], normal[2]);
        int packedTangent = IrisVertexMath.computeTangent(normal[0], normal[1], normal[2],
                v0x, v0y, v0z, v0u, v0v,
                v1x, v1y, v1z, v1u, v1v,
                v2x, v2y, v2z, v2u, v2v);
        float midU = vertexAmount == 4 ? (v0u + v1u + v2u + v3u) * 0.25f : (v0u + v1u + v2u) / 3.0f;
        float midV = vertexAmount == 4 ? (v0v + v1v + v2v + v3v) * 0.25f : (v0v + v1v + v2v) / 3.0f;

        for (int vertex = 0; vertex < vertexAmount; vertex++) {
            int vertexBase = base + vertex * stride;
            int tangent = packedTangent;
            if (vertexAmount == 3) {
                int vertexNormal = byteBuffer.getInt(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_NORMAL_OFFSET);
                tangent = IrisVertexMath.computeSmoothTangent(IrisVertexMath.unpackSnormByte(vertexNormal),
                        IrisVertexMath.unpackSnormByte(vertexNormal >> 8),
                        IrisVertexMath.unpackSnormByte(vertexNormal >> 16),
                        v0x, v0y, v0z, v0u, v0v,
                        v1x, v1y, v1z, v1u, v1v,
                        v2x, v2y, v2z, v2u, v2v);
            } else {
                byteBuffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_NORMAL_OFFSET, packedNormal);
            }
            byteBuffer.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_MID_TEX_COORD_OFFSET, midU);
            byteBuffer.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_MID_TEX_COORD_OFFSET + 4, midV);
            byteBuffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_TANGENT_OFFSET, tangent);
        }
    }

    @Inject(method = "putColorMultiplier", at = @At("RETURN"))
    private void ausm$separateAmbientOcclusion(float redMultiplier, float greenMultiplier, float blueMultiplier, int vertexIndex, CallbackInfo ci) {
        if (vertexIndex <= 0 || vertexIndex > vertexCount) {
            return;
        }
        SeparateAoColorWriter.rewriteExistingColor((BufferBuilder) (Object) this, redMultiplier, greenMultiplier, blueMultiplier, vertexIndex);
    }
}
