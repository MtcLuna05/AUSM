package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import com.l.ausm.impl.pipeline.vertex.IrisVertexMath;
import com.l.ausm.impl.pipeline.vertex.SeparateAoColorWriter;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ByteOrder;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin implements IBufferBuilderExtension {
    @Unique
    private int ausm$capturedTranslucentAlpha = -1;

    @Unique
    private int ausm$capturedTranslucentAlphaOffset = -1;

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
    private boolean isDrawing;

    @Shadow
    public native int getColorIndex(int vertexIndex);

    @Shadow
    private native void growBuffer(int size);

    @Shadow
    private native int getBufferSize();

    @Override
    public void ausm$forceResetDrawingState() {
        isDrawing = false;
        ((BufferBuilder) (Object) this).reset();
    }

    @Override
    public boolean ausm$isDrawing() {
        return isDrawing;
    }

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
            ausm$rewriteVanillaEmissiveBulkData(sourceBuffer, ci);
            return;
        }

        ByteBuffer source = sourceBuffer.slice();
        source.order(byteBuffer.order());
        int sourceBytes = source.remaining();
        int targetStride = vertexFormat.getSize();
        int sourceStride = ausm$pipelineBlockBulkStride(source, sourceBytes, targetStride);
        if (sourceStride < 0) {
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
            ausm$applyBloomMaskVertexData(expandedData, target);
            ausm$applyEmissiveVertexColor(expandedData, target);
            ausm$applyEmissiveLightmap(expandedData, target);
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

        if (ausm$rewriteVanillaEmissiveVertexData(vertexData, ci)) {
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
        int sourceStride = ausm$pipelineBlockVertexStride(vertexData, targetStride);
        if (sourceStride < 0) {
            return;
        }

        int vertexBase = vertexCount;
        int vertexTotal = vertexData.length / sourceStride;
        int[] expandedData = new int[vertexTotal * targetStride];
        for (int vertex = 0; vertex < vertexTotal; vertex++) {
            int source = vertex * sourceStride;
            int target = vertex * targetStride;
            System.arraycopy(vertexData, source, expandedData, target, Math.min(sourceStride, targetStride));
            ausm$applyBloomMaskVertexData(expandedData, target);
            ausm$applyEmissiveVertexColor(expandedData, target);
            ausm$applyEmissiveLightmap(expandedData, target);
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

    private static int ausm$pipelineBlockVertexStride(int[] vertexData, int targetStride) {
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
        if (sourceInts % 7 == 0 && ausm$looksLikeVanillaIntStride(vertexData, 7)) {
            return 7;
        }
        if (sourceInts % 8 == 0 && ausm$looksLikeVanillaIntStride(vertexData, 8)) {
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

    private static int ausm$pipelineBlockBulkStride(ByteBuffer source, int sourceBytes, int targetStride) {
        int vanillaStride = 7 * Integer.BYTES;
        int forgeNormalStride = 8 * Integer.BYTES;
        int optifineStride = 14 * Integer.BYTES;
        if (sourceBytes == 4 * vanillaStride) {
            return vanillaStride;
        }
        if (sourceBytes == 4 * forgeNormalStride) {
            return forgeNormalStride;
        }
        if (sourceBytes % vanillaStride == 0 && ausm$looksLikeVanillaByteStride(source, sourceBytes, vanillaStride)) {
            return vanillaStride;
        }
        if (sourceBytes % forgeNormalStride == 0 && ausm$looksLikeVanillaByteStride(source, sourceBytes, forgeNormalStride)) {
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

    private boolean ausm$rewriteVanillaEmissiveVertexData(int[] vertexData, CallbackInfo ci) {
        if (!ausm$shouldRewriteVanillaEmissiveData() || vertexData == null) {
            return false;
        }

        int targetStride = vertexFormat.getIntegerSize();
        if (targetStride <= 0 || vertexData.length <= 0 || vertexData.length % targetStride != 0) {
            return false;
        }

        int[] rewrittenData = vertexData.clone();
        int vertexTotal = rewrittenData.length / targetStride;
        for (int vertex = 0; vertex < vertexTotal; vertex++) {
            ausm$applyBloomMaskVertexData(rewrittenData, vertex * targetStride);
            ausm$applyVanillaEmissiveAttributes(rewrittenData, vertex * targetStride);
        }

        growBuffer(rewrittenData.length * Integer.BYTES + vertexFormat.getSize());
        rawIntBuffer.position(getBufferSize());
        rawIntBuffer.put(rewrittenData);
        vertexCount += vertexTotal;
        ci.cancel();
        return true;
    }

    private boolean ausm$rewriteVanillaEmissiveBulkData(ByteBuffer sourceBuffer, CallbackInfo ci) {
        if (!ausm$shouldRewriteVanillaEmissiveData() || sourceBuffer == null) {
            return false;
        }

        ByteBuffer source = sourceBuffer.slice();
        source.order(byteBuffer.order());
        int targetStride = vertexFormat.getIntegerSize();
        int sourceBytes = source.remaining();
        if (targetStride <= 0 || sourceBytes <= 0 || sourceBytes % (targetStride * Integer.BYTES) != 0) {
            return false;
        }

        int vertexTotal = sourceBytes / (targetStride * Integer.BYTES);
        int[] rewrittenData = new int[vertexTotal * targetStride];
        for (int index = 0; index < rewrittenData.length; index++) {
            rewrittenData[index] = source.getInt();
        }
        for (int vertex = 0; vertex < vertexTotal; vertex++) {
            ausm$applyBloomMaskVertexData(rewrittenData, vertex * targetStride);
            ausm$applyVanillaEmissiveAttributes(rewrittenData, vertex * targetStride);
        }

        growBuffer(rewrittenData.length * Integer.BYTES + vertexFormat.getSize());
        rawIntBuffer.position(getBufferSize());
        rawIntBuffer.put(rewrittenData);
        vertexCount += vertexTotal;
        ci.cancel();
        return true;
    }

    private boolean ausm$shouldRewriteVanillaEmissiveData() {
        return BlockRenderContext.blockEmission() > 0
                && vertexFormat != null
                && !ExtendedVertexFormats.isPipelineBlock(vertexFormat)
                && !ExtendedVertexFormats.isPipelineEntity(vertexFormat)
                && vertexFormat.hasColor()
                && vertexFormat.hasUvOffset(1)
                && vertexFormat.getColorOffset() % Integer.BYTES == 0
                && vertexFormat.getUvOffsetById(1) % Integer.BYTES == 0;
    }

    private void ausm$applyVanillaEmissiveAttributes(int[] vertexData, int vertexBase) {
        int blockEmission = BlockRenderContext.blockEmission();
        if (vertexData == null || vertexBase < 0 || blockEmission <= 0) {
            return;
        }

        int colorIndex = vertexBase + vertexFormat.getColorOffset() / Integer.BYTES;
        int lightmapIndex = vertexBase + vertexFormat.getUvOffsetById(1) / Integer.BYTES;
        if (colorIndex >= 0 && colorIndex < vertexData.length) {
            int before = vertexData[colorIndex];
            vertexData[colorIndex] = ausm$applyBlockAlpha(ausm$brightenColorRgb(vertexData[colorIndex], blockEmission));
            PipelineContext.getInstance().logCurrentRenderContextProbe("buffer-vanilla-data-color",
                    "vertexBase=" + vertexBase
                            + ", before=0x" + Integer.toHexString(before)
                            + ", after=0x" + Integer.toHexString(vertexData[colorIndex])
                            + ", colorIndex=" + colorIndex
                            + ", format=" + vertexFormat);
        }
        if (lightmapIndex >= 0 && lightmapIndex < vertexData.length) {
            int before = vertexData[lightmapIndex];
            vertexData[lightmapIndex] = ausm$emissiveLightmap(vertexData[lightmapIndex], blockEmission);
            PipelineContext.getInstance().logCurrentRenderContextProbe("buffer-vanilla-data-light",
                    "vertexBase=" + vertexBase
                            + ", before=0x" + Integer.toHexString(before)
                            + ", after=0x" + Integer.toHexString(vertexData[lightmapIndex])
                            + ", lightmapIndex=" + lightmapIndex
                            + ", format=" + vertexFormat);
        }
    }

    private static boolean ausm$looksLikeVanillaIntStride(int[] data, int strideInts) {
        if (data == null || data.length < strideInts * 2 || data.length % strideInts != 0) {
            return false;
        }
        int vertices = Math.min(data.length / strideInts, 8);
        for (int vertex = 0; vertex < vertices; vertex++) {
            if (!ausm$looksLikeVanillaVertex(data, vertex * strideInts, strideInts)) {
                return false;
            }
        }
        return true;
    }

    private static boolean ausm$looksLikeVanillaByteStride(ByteBuffer data, int sourceBytes, int strideBytes) {
        if (data == null || sourceBytes < strideBytes * 2 || sourceBytes % strideBytes != 0) {
            return false;
        }
        int vertices = Math.min(sourceBytes / strideBytes, 8);
        for (int vertex = 0; vertex < vertices; vertex++) {
            if (!ausm$looksLikeVanillaVertex(data, vertex * strideBytes, strideBytes / Integer.BYTES)) {
                return false;
            }
        }
        return true;
    }

    private static boolean ausm$looksLikeVanillaVertex(int[] data, int base, int strideInts) {
        if (base < 0 || base + 6 >= data.length || strideInts < 7) {
            return false;
        }
        return ausm$looksLikePosition(data[base], data[base + 1], data[base + 2])
                && ausm$looksLikeColor(data[base + 3])
                && ausm$looksLikeUv(data[base + 4], data[base + 5]);
    }

    private static boolean ausm$looksLikeVanillaVertex(ByteBuffer data, int base, int strideInts) {
        if (base < 0 || base + 7 * Integer.BYTES > data.limit() || strideInts < 7) {
            return false;
        }
        return ausm$looksLikePosition(data.getInt(base), data.getInt(base + 4), data.getInt(base + 8))
                && ausm$looksLikeColor(data.getInt(base + 12))
                && ausm$looksLikeUv(data.getInt(base + 16), data.getInt(base + 20));
    }

    private static boolean ausm$looksLikePosition(int xBits, int yBits, int zBits) {
        float x = Float.intBitsToFloat(xBits);
        float y = Float.intBitsToFloat(yBits);
        float z = Float.intBitsToFloat(zBits);
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                && Math.abs(x) < 4096.0f
                && Math.abs(y) < 4096.0f
                && Math.abs(z) < 4096.0f;
    }

    private static boolean ausm$looksLikeUv(int uBits, int vBits) {
        float u = Float.intBitsToFloat(uBits);
        float v = Float.intBitsToFloat(vBits);
        return Float.isFinite(u) && Float.isFinite(v)
                && Math.abs(u) < 64.0f
                && Math.abs(v) < 64.0f;
    }

    private static boolean ausm$looksLikeColor(int color) {
        return ((color >>> 24) & 0xFF) > 0;
    }

    @Inject(method = "endVertex", at = @At("HEAD"))
    private void ausm$applyEmissiveLightmap(CallbackInfo ci) {
        ausm$applyBloomMaskCurrentVertex();
        ausm$applyEmissiveCurrentVertexColor();

        int blockEmission = BlockRenderContext.vanillaLightmapEmission();
        if (blockEmission <= 0 || vertexFormat == null || !vertexFormat.hasUvOffset(1)) {
            return;
        }

        int offset = vertexCount * vertexFormat.getSize() + vertexFormat.getUvOffsetById(1);
        if (offset < 0 || offset + 4 > byteBuffer.capacity()) {
            return;
        }

        int packed = byteBuffer.getShort(offset) & 0xFFFF;
        packed |= (byteBuffer.getShort(offset + 2) & 0xFFFF) << 16;
        packed = ausm$emissiveLightmap(packed, blockEmission);
        byteBuffer.putShort(offset, (short) (packed & 0xFFFF));
        byteBuffer.putShort(offset + 2, (short) ((packed >>> 16) & 0xFFFF));
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

    @Inject(method = "putPosition", at = @At("RETURN"))
    private void ausm$refreshMidBlockAfterRawQuadTranslation(double x, double y, double z, CallbackInfo ci) {
        if (!ExtendedVertexFormats.isPipelineBlock(vertexFormat) || vertexCount < 4) {
            return;
        }

        int stride = vertexFormat.getSize();
        int base = (vertexCount - 4) * stride;
        if (base < 0 || base + 3 * stride + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 4 > byteBuffer.capacity()) {
            return;
        }

        for (int vertex = 0; vertex < 4; vertex++) {
            int vertexBase = base + vertex * stride;
            byteBuffer.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET, BlockRenderContext.midBlock(
                    byteBuffer.getFloat(vertexBase),
                    byteBuffer.getFloat(vertexBase + 4),
                    byteBuffer.getFloat(vertexBase + 8)
            ));
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

    private static void ausm$applyEmissiveLightmap(int[] vertexData, int vertexBase) {
        int blockEmission = BlockRenderContext.vanillaLightmapEmission();
        if (blockEmission <= 0 || vertexData == null || vertexBase < 0 || vertexBase + 6 >= vertexData.length) {
            return;
        }
        vertexData[vertexBase + 6] = ausm$emissiveLightmap(vertexData[vertexBase + 6], blockEmission);
    }

    private static void ausm$applyEmissiveVertexColor(int[] vertexData, int vertexBase) {
        if (BlockRenderContext.bloomMaskFallback() || AusmBloomLayer.isBloomLayer(MinecraftForgeClient.getRenderLayer())) {
            return;
        }
        int blockEmission = BlockRenderContext.blockEmission();
        if (blockEmission <= 0 || vertexData == null || vertexBase < 0 || vertexBase + 3 >= vertexData.length) {
            return;
        }
        vertexData[vertexBase + 3] = ausm$applyBlockAlpha(ausm$brightenColorRgb(vertexData[vertexBase + 3], blockEmission));
    }

    private void ausm$applyEmissiveCurrentVertexColor() {
        if (BlockRenderContext.bloomMaskFallback() || AusmBloomLayer.isBloomLayer(MinecraftForgeClient.getRenderLayer())) {
            return;
        }
        int blockEmission = BlockRenderContext.blockEmission();
        if (blockEmission <= 0 || vertexFormat == null || !vertexFormat.hasColor()) {
            return;
        }

        int colorOffset = vertexCount * vertexFormat.getSize() + vertexFormat.getColorOffset();
        if (colorOffset < 0 || colorOffset + 3 >= byteBuffer.capacity()) {
            return;
        }

        int before = byteBuffer.getInt(colorOffset);
        byteBuffer.put(colorOffset, (byte) ausm$brightenColorComponent(byteBuffer.get(colorOffset) & 0xFF, blockEmission));
        byteBuffer.put(colorOffset + 1, (byte) ausm$brightenColorComponent(byteBuffer.get(colorOffset + 1) & 0xFF, blockEmission));
        byteBuffer.put(colorOffset + 2, (byte) ausm$brightenColorComponent(byteBuffer.get(colorOffset + 2) & 0xFF, blockEmission));
        ausm$writeBlockAlpha(colorOffset);
        PipelineContext.getInstance().logCurrentRenderContextProbe("buffer-current-vertex-color",
                "vertex=" + vertexCount
                        + ", before=0x" + Integer.toHexString(before)
                        + ", after=0x" + Integer.toHexString(byteBuffer.getInt(colorOffset))
                        + ", colorOffset=" + colorOffset
                        + ", format=" + vertexFormat);
    }

    private static int ausm$brightenColorRgb(int color, int blockEmission) {
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                ? ausm$brightenColorRgbLittleEndian(color, blockEmission)
                : ausm$brightenColorRgbBigEndian(color, blockEmission);
    }

    private static int ausm$brightenColorRgbLittleEndian(int color, int blockEmission) {
        int red = ausm$brightenColorComponent(color & 0xFF, blockEmission);
        int green = ausm$brightenColorComponent((color >> 8) & 0xFF, blockEmission);
        int blue = ausm$brightenColorComponent((color >> 16) & 0xFF, blockEmission);
        int alpha = (color >>> 24) & 0xFF;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private static int ausm$brightenColorRgbBigEndian(int color, int blockEmission) {
        int red = ausm$brightenColorComponent((color >> 24) & 0xFF, blockEmission);
        int green = ausm$brightenColorComponent((color >> 16) & 0xFF, blockEmission);
        int blue = ausm$brightenColorComponent((color >> 8) & 0xFF, blockEmission);
        int alpha = color & 0xFF;
        return (red << 24) | (green << 16) | (blue << 8) | alpha;
    }

    private static int ausm$brightenColorComponent(int component, int blockEmission) {
        float weight = Math.min(1.0f, Math.max(0.0f, blockEmission / 15.0f));
        return Math.min(255, Math.round(component + (255 - component) * weight));
    }

    private static int ausm$emissiveLightmap(int packedLightmap, int blockEmission) {
        int emissiveLevel = 240;
        int block = Math.max(packedLightmap & 0xFFFF, emissiveLevel);
        int sky = Math.max((packedLightmap >>> 16) & 0xFFFF, emissiveLevel);
        return (sky << 16) | block;
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

    @Inject(method = "putColorMultiplier", at = @At("HEAD"))
    private void ausm$captureTranslucentAlpha(float redMultiplier, float greenMultiplier, float blueMultiplier, int vertexIndex, CallbackInfo ci) {
        ausm$capturedTranslucentAlpha = -1;
        ausm$capturedTranslucentAlphaOffset = -1;
        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
        if ((layer != BlockRenderLayer.TRANSLUCENT && !AusmBloomLayer.isBloomLayer(layer))
                || vertexIndex <= 0
                || vertexIndex > vertexCount
                || vertexFormat == null
                || !vertexFormat.hasColor()) {
            return;
        }

        int colorOffset = getColorIndex(vertexIndex) * Integer.BYTES;
        if (colorOffset < 0 || colorOffset + Integer.BYTES > byteBuffer.capacity()) {
            return;
        }

        int alpha = byteBuffer.get(colorOffset + 3) & 0xFF;
        if (alpha > 0 && alpha < 255) {
            ausm$capturedTranslucentAlpha = alpha;
            ausm$capturedTranslucentAlphaOffset = colorOffset + 3;
            PipelineContext.getInstance().logCurrentRenderContextProbe("buffer-alpha-capture",
                    "vertexIndex=" + vertexIndex
                            + ", alpha=" + alpha
                            + ", color=0x" + Integer.toHexString(byteBuffer.getInt(colorOffset))
                            + ", colorOffset=" + colorOffset
                            + ", format=" + vertexFormat);
        }
    }

    @Inject(method = "putColorMultiplier", at = @At("RETURN"))
    private void ausm$separateAmbientOcclusion(float redMultiplier, float greenMultiplier, float blueMultiplier, int vertexIndex, CallbackInfo ci) {
        if (vertexIndex <= 0 || vertexIndex > vertexCount) {
            return;
        }
        if (ausm$capturedTranslucentAlpha >= 0
                && ausm$capturedTranslucentAlphaOffset >= 0
                && ausm$capturedTranslucentAlphaOffset < byteBuffer.capacity()) {
            byteBuffer.put(ausm$capturedTranslucentAlphaOffset, (byte) ausm$capturedTranslucentAlpha);
        }
        if (BlockRenderContext.bloomMaskFallback()) {
            ausm$applyBloomMaskExistingVertex(vertexIndex);
        }
        SeparateAoColorWriter.rewriteExistingColor((BufferBuilder) (Object) this, redMultiplier, greenMultiplier, blueMultiplier, vertexIndex);
        if (BlockRenderContext.blockEmission() > 0) {
            ausm$brightenExistingVertexColor(vertexIndex);
        }
        if (BlockRenderContext.bloomMaskFallback()) {
            ausm$applyBloomMaskExistingVertex(vertexIndex);
        }
    }

    @Unique
    private void ausm$brightenExistingVertexColor(int vertexIndex) {
        if (BlockRenderContext.bloomMaskFallback() || AusmBloomLayer.isBloomLayer(MinecraftForgeClient.getRenderLayer())) {
            return;
        }
        if (vertexFormat == null || !vertexFormat.hasColor()) {
            return;
        }

        int colorOffset = getColorIndex(vertexIndex) * Integer.BYTES;
        if (colorOffset < 0 || colorOffset + Integer.BYTES > byteBuffer.capacity()) {
            return;
        }

        int blockEmission = BlockRenderContext.blockEmission();
        int before = byteBuffer.getInt(colorOffset);
        byteBuffer.put(colorOffset, (byte) ausm$brightenColorComponent(byteBuffer.get(colorOffset) & 0xFF, blockEmission));
        byteBuffer.put(colorOffset + 1, (byte) ausm$brightenColorComponent(byteBuffer.get(colorOffset + 1) & 0xFF, blockEmission));
        byteBuffer.put(colorOffset + 2, (byte) ausm$brightenColorComponent(byteBuffer.get(colorOffset + 2) & 0xFF, blockEmission));
        ausm$writeBlockAlpha(colorOffset);
        PipelineContext.getInstance().logCurrentRenderContextProbe("buffer-existing-vertex-color",
                "vertexIndex=" + vertexIndex
                        + ", before=0x" + Integer.toHexString(before)
                        + ", after=0x" + Integer.toHexString(byteBuffer.getInt(colorOffset))
                        + ", colorOffset=" + colorOffset
                        + ", format=" + vertexFormat);
    }

    @Unique
    private static void ausm$applyBloomMaskVertexData(int[] vertexData, int vertexBase) {
        if (!BlockRenderContext.bloomMaskFallback() || vertexData == null || vertexBase < 0 || vertexBase + 6 >= vertexData.length) {
            return;
        }
        vertexData[vertexBase] = Float.floatToRawIntBits(ausm$expandedMaskCoordinate(Float.intBitsToFloat(vertexData[vertexBase]), BlockRenderContext.localX()));
        vertexData[vertexBase + 1] = Float.floatToRawIntBits(ausm$expandedMaskCoordinate(Float.intBitsToFloat(vertexData[vertexBase + 1]), BlockRenderContext.localY()));
        vertexData[vertexBase + 2] = Float.floatToRawIntBits(ausm$expandedMaskCoordinate(Float.intBitsToFloat(vertexData[vertexBase + 2]), BlockRenderContext.localZ()));
        vertexData[vertexBase + 3] = BlockRenderContext.bloomMaskColor();
        vertexData[vertexBase + 4] = Float.floatToRawIntBits(BlockRenderContext.bloomMaskU());
        vertexData[vertexBase + 5] = Float.floatToRawIntBits(BlockRenderContext.bloomMaskV());
        vertexData[vertexBase + 6] = ausm$emissiveLightmap(vertexData[vertexBase + 6], 15);
    }

    @Unique
    private void ausm$applyBloomMaskCurrentVertex() {
        if (!BlockRenderContext.bloomMaskFallback() || vertexFormat == null) {
            return;
        }
        int vertexOffset = vertexCount * vertexFormat.getSize();
        if (vertexOffset < 0 || vertexOffset + 12 > byteBuffer.capacity()) {
            return;
        }
        byteBuffer.putFloat(vertexOffset, ausm$expandedMaskCoordinate(byteBuffer.getFloat(vertexOffset), BlockRenderContext.localX()));
        byteBuffer.putFloat(vertexOffset + 4, ausm$expandedMaskCoordinate(byteBuffer.getFloat(vertexOffset + 4), BlockRenderContext.localY()));
        byteBuffer.putFloat(vertexOffset + 8, ausm$expandedMaskCoordinate(byteBuffer.getFloat(vertexOffset + 8), BlockRenderContext.localZ()));

        if (vertexFormat.hasColor()) {
            int colorOffset = vertexOffset + vertexFormat.getColorOffset();
            if (colorOffset >= 0 && colorOffset + 4 <= byteBuffer.capacity()) {
                byteBuffer.putInt(colorOffset, BlockRenderContext.bloomMaskColor());
            }
        }
        if (vertexFormat.hasUvOffset(0)) {
            int uvOffset = vertexOffset + vertexFormat.getUvOffsetById(0);
            if (uvOffset >= 0 && uvOffset + 8 <= byteBuffer.capacity()) {
                byteBuffer.putFloat(uvOffset, BlockRenderContext.bloomMaskU());
                byteBuffer.putFloat(uvOffset + 4, BlockRenderContext.bloomMaskV());
            }
        }
        if (vertexFormat.hasUvOffset(1)) {
            int lightOffset = vertexOffset + vertexFormat.getUvOffsetById(1);
            if (lightOffset >= 0 && lightOffset + 4 <= byteBuffer.capacity()) {
                byteBuffer.putShort(lightOffset, (short) 240);
                byteBuffer.putShort(lightOffset + 2, (short) 240);
            }
        }
    }

    @Unique
    private void ausm$applyBloomMaskExistingVertex(int vertexIndex) {
        if (vertexFormat == null || vertexIndex <= 0 || vertexIndex > vertexCount) {
            return;
        }
        int vertexOffset = (vertexIndex - 1) * vertexFormat.getSize();
        if (vertexOffset < 0 || vertexOffset + 12 > byteBuffer.capacity()) {
            return;
        }
        if (vertexFormat.hasColor()) {
            int colorOffset = vertexOffset + vertexFormat.getColorOffset();
            if (colorOffset >= 0 && colorOffset + 4 <= byteBuffer.capacity()) {
                byteBuffer.putInt(colorOffset, BlockRenderContext.bloomMaskColor());
            }
        }
        if (vertexFormat.hasUvOffset(0)) {
            int uvOffset = vertexOffset + vertexFormat.getUvOffsetById(0);
            if (uvOffset >= 0 && uvOffset + 8 <= byteBuffer.capacity()) {
                byteBuffer.putFloat(uvOffset, BlockRenderContext.bloomMaskU());
                byteBuffer.putFloat(uvOffset + 4, BlockRenderContext.bloomMaskV());
            }
        }
        if (vertexFormat.hasUvOffset(1)) {
            int lightOffset = vertexOffset + vertexFormat.getUvOffsetById(1);
            if (lightOffset >= 0 && lightOffset + 4 <= byteBuffer.capacity()) {
                byteBuffer.putShort(lightOffset, (short) 240);
                byteBuffer.putShort(lightOffset + 2, (short) 240);
            }
        }
    }

    @Unique
    private static float ausm$expandedMaskCoordinate(float value, int localBlockCoord) {
        if (!Float.isFinite(value)) {
            return value;
        }
        float center = localBlockCoord + 0.5f;
        float delta = value - center;
        if (Math.abs(delta) < 1.0e-4f) {
            return value;
        }
        return value + Math.copySign(0.0025f, delta);
    }

    @Unique
    private static int ausm$applyBlockAlpha(int color) {
        int alpha = BlockRenderContext.blockAlpha();
        if (alpha < 0) {
            return color;
        }
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                ? (color & 0x00FFFFFF) | (alpha << 24)
                : (color & 0xFFFFFF00) | alpha;
    }

    @Unique
    private void ausm$writeBlockAlpha(int colorOffset) {
        int alpha = BlockRenderContext.blockAlpha();
        if (alpha >= 0 && colorOffset >= 0 && colorOffset + 3 < byteBuffer.capacity()) {
            byteBuffer.put(colorOffset + 3, (byte) alpha);
        }
    }
}
