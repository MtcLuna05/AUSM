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
import com.l.ausm.impl.pipeline.compat.TerrainRenderProbeState;
import com.l.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import net.minecraft.client.renderer.BufferBuilder;
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

    @Unique
    private boolean ausm$shaderlessBloomMetadata;

    @Unique
    private static final ThreadLocal<int[]> AUSM$VERTEX_SCRATCH = ThreadLocal.withInitial(() -> new int[16]);

    @Unique
    private static final ThreadLocal<float[]> AUSM$NORMAL_SCRATCH = ThreadLocal.withInitial(() -> new float[3]);

    @Unique
    private static final boolean AUSM$LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    @Shadow(remap = false)
    private ByteBuffer field_179001_a;

    @Shadow(remap = false)
    private IntBuffer field_178999_b;

    @Shadow(remap = false)
    private int field_178997_d;

    @Shadow(remap = false)
    private int field_179006_k;

    @Shadow(remap = false)
    private VertexFormatElement field_181677_f;

    @Shadow(remap = false)
    private int field_181678_g;

    @Shadow(remap = false)
    private VertexFormat field_179011_q;

    @Shadow(remap = false)
    private boolean field_179010_r;

    @Shadow(remap = false)
    public native int func_78909_a(int vertexIndex);

    @Shadow(remap = false)
    private native void func_181670_b(int size);

    @Shadow(remap = false)
    private native int func_181664_j();

    @Shadow(remap = false)
    public native void func_178965_a();

    @Shadow(remap = false)
    public native void func_178981_a(int[] vertexData);

    @Shadow(remap = false)
    public native void func_178978_a(float redMultiplier, float greenMultiplier, float blueMultiplier, int vertexIndex);

    @Override
    public void ausm$forceResetDrawingState() {
        field_179010_r = false;
        func_178965_a();
    }

    @Override
    public boolean ausm$isDrawing() {
        return field_179010_r;
    }

    @Override
    public void ausm$truncateVertexCount(int vertexCount) {
        field_178997_d = Math.max(0, Math.min(vertexCount, field_178997_d));
    }

    @Override
    public void ausm$resetShaderlessBloomMetadata() {
        ausm$shaderlessBloomMetadata = false;
    }

    @Override
    public void ausm$markShaderlessBloomMetadata() {
        ausm$shaderlessBloomMetadata = true;
    }

    @Override
    public boolean ausm$hasShaderlessBloomMetadata() {
        return ausm$shaderlessBloomMetadata;
    }

    @Override
    public VertexFormat ausm$vertexFormat() {
        return field_179011_q;
    }

    @Override
    public ByteBuffer ausm$byteBuffer() {
        return field_179001_a;
    }

    @Override
    public int ausm$vertexCount() {
        return field_178997_d;
    }

    @Override
    public void ausm$addVertexData(int[] vertexData) {
        func_178981_a(vertexData);
    }

    @Override
    public void ausm$putColorMultiplier(float redMultiplier, float greenMultiplier,
                                        float blueMultiplier, int vertexIndex) {
        func_178978_a(redMultiplier, greenMultiplier, blueMultiplier, vertexIndex);
    }

    @ModifyVariable(method = "func_181668_a", at = @At("HEAD"), argsOnly = true)
    private VertexFormat ausm$usePipelineEntityFormat(VertexFormat original) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            return original;
        }
        if (ausm$isCodeChickenBakingBuffer()) {
            return original;
        }
        if (ausm$isVanillaItemVertexFormat(original) && PipelineContext.getInstance().shouldUsePipelineEntityFormat()) {
            return ExtendedVertexFormats.PIPELINE_ENTITY;
        }
        return original;
    }

    @Unique
    private boolean ausm$isVanillaItemVertexFormat(VertexFormat format) {
        return format != null
                && ExtendedVertexFormats.size(format) == 28
                && ExtendedVertexFormats.elementCount(format) == 4
                && ExtendedVertexFormats.hasColor(format)
                && ExtendedVertexFormats.hasNormal(format)
                && ExtendedVertexFormats.hasUvOffset(format, 0)
                && !ExtendedVertexFormats.hasUvOffset(format, 1);
    }

    @Unique
    private boolean ausm$isCodeChickenBakingBuffer() {
        return "codechicken.lib.render.buffer.BakingVertexBuffer".equals(((Object) this).getClass().getName());
    }

    @Inject(method = "putBulkData", at = @At("HEAD"), cancellable = true)
    private void ausm$expandBulkVanillaVertexData(ByteBuffer sourceBuffer, CallbackInfo ci) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            return;
        }
        if (!ExtendedVertexFormats.isPipelineBlock(field_179011_q) || sourceBuffer == null) {
            ausm$rewriteVanillaEmissiveBulkData(sourceBuffer, ci);
            return;
        }

        ByteBuffer source = sourceBuffer.slice();
        source.order(field_179001_a.order());
        int sourceBytes = source.remaining();
        int targetStride = ExtendedVertexFormats.size(field_179011_q);
        int sourceStride = ausm$pipelineBlockBulkStride(source, sourceBytes, targetStride);
        if (sourceStride < 0) {
            return;
        }

        int vertexBase = field_178997_d;
        int vertexTotal = sourceBytes / sourceStride;
        int targetIntStride = ExtendedVertexFormats.integerSize(field_179011_q);
        int sourceIntStride = sourceStride / Integer.BYTES;
        int[] scratch = ausm$vertexScratch(targetIntStride);
        boolean compatibilityEmissiveBoost = ausm$shouldApplyPipelineBlockCompatibilityEmissiveBoost();
        long packedEntityData = BlockRenderContext.packedEntityData();
        int packedEntity = (int) packedEntityData;
        int packedEntityHigh = (int) (packedEntityData >>> 32);
        int midBlockEmission = BlockRenderContext.midBlockEmission();
        int packedLocalPosition = BlockRenderContext.packedLocalPosition();
        boolean bloomMaskFallback = BlockRenderContext.bloomMaskFallback();
        int customLiquidTint = BlockRenderContext.customLiquidTint();
        int vanillaLightmapEmission = compatibilityEmissiveBoost ? BlockRenderContext.vanillaLightmapEmission() : 0;
        boolean agricraftCrop = BlockRenderContext.isAgricraftCrop();
        int agricraftPackedLight = agricraftCrop ? BlockRenderContext.packedLightmap() : 0;
        ausm$logVertexExpandProbe("bulk-in", sourceBytes, sourceStride, targetStride, vertexBase, vertexTotal, -1, compatibilityEmissiveBoost);
        func_181670_b(vertexTotal * targetStride + targetStride);
        field_178999_b.position(func_181664_j());
        for (int vertex = 0; vertex < vertexTotal; vertex++) {
            for (int sourceInt = 0; sourceInt < sourceIntStride; sourceInt++) {
                scratch[sourceInt] = source.getInt();
            }
            ausm$clearVertexScratchTail(scratch, sourceIntStride, targetIntStride);
            ausm$sanitizeAgricraftCropVertex(scratch, 0, sourceIntStride, agricraftCrop, agricraftPackedLight);
            ausm$applyBloomMaskVertexData(scratch, 0, bloomMaskFallback);
            ausm$applyCustomLiquidTintVertexData(scratch, 0, bloomMaskFallback, customLiquidTint);
            ausm$applyEmissiveLightmap(scratch, 0, vanillaLightmapEmission);
            scratch[ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET / Integer.BYTES] = packedEntity;
            scratch[ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET / Integer.BYTES + 1] = packedEntityHigh;
            scratch[ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET / Integer.BYTES] = BlockRenderContext.midBlock(
                    Float.intBitsToFloat(scratch[0]),
                    Float.intBitsToFloat(scratch[1]),
                    Float.intBitsToFloat(scratch[2]),
                    packedLocalPosition,
                    midBlockEmission
            );
            field_178999_b.put(scratch, 0, targetIntStride);
        }

        field_178997_d += vertexTotal;
        ausm$logVertexExpandProbe("bulk-out", sourceBytes, sourceStride, targetStride, vertexBase, vertexTotal, field_178997_d, compatibilityEmissiveBoost);

        for (int vertex = 0; vertex + 3 < vertexTotal; vertex += 4) {
            ausm$writeDerivedBlockAttributesForPolygon(vertexBase + vertex, 4);
        }

        ausm$markCurrentContextShaderlessBloomMetadata();
        ausm$resetPipelineVertexCursor();
        ci.cancel();
    }

    @Inject(method = "func_178981_a", at = @At("HEAD"), cancellable = true)
    private void ausm$expandVanillaQuadData(int[] vertexData, CallbackInfo ci) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            return;
        }
        if (vertexData == null) {
            return;
        }

        if (ausm$rewriteVanillaEmissiveVertexData(vertexData, ci)) {
            return;
        }

        if (ExtendedVertexFormats.isPipelineEntity(field_179011_q)) {
            ausm$expandPipelineEntityVertexData(vertexData, ci);
            return;
        }

        if (!ExtendedVertexFormats.isPipelineBlock(field_179011_q)) {
            return;
        }

        int targetStride = ExtendedVertexFormats.integerSize(field_179011_q);
        int sourceStride = ausm$pipelineBlockVertexStride(vertexData, targetStride);
        if (sourceStride < 0) {
            return;
        }

        int vertexBase = field_178997_d;
        int vertexTotal = vertexData.length / sourceStride;
        int[] scratch = ausm$vertexScratch(targetStride);
        boolean compatibilityEmissiveBoost = ausm$shouldApplyPipelineBlockCompatibilityEmissiveBoost();
        long packedEntityData = BlockRenderContext.packedEntityData();
        int packedEntity = (int) packedEntityData;
        int packedEntityHigh = (int) (packedEntityData >>> 32);
        int midBlockEmission = BlockRenderContext.midBlockEmission();
        int packedLocalPosition = BlockRenderContext.packedLocalPosition();
        boolean bloomMaskFallback = BlockRenderContext.bloomMaskFallback();
        int customLiquidTint = BlockRenderContext.customLiquidTint();
        int vanillaLightmapEmission = compatibilityEmissiveBoost ? BlockRenderContext.vanillaLightmapEmission() : 0;
        boolean agricraftCrop = BlockRenderContext.isAgricraftCrop();
        int agricraftPackedLight = agricraftCrop ? BlockRenderContext.packedLightmap() : 0;
        ausm$logVertexExpandProbe("quad-in", vertexData.length, sourceStride, targetStride, vertexBase, vertexTotal, -1, compatibilityEmissiveBoost);
        func_181670_b(vertexTotal * ExtendedVertexFormats.size(field_179011_q) + ExtendedVertexFormats.size(field_179011_q));
        field_178999_b.position(func_181664_j());
        for (int vertex = 0; vertex < vertexTotal; vertex++) {
            int source = vertex * sourceStride;
            int copyInts = Math.min(sourceStride, targetStride);
            System.arraycopy(vertexData, source, scratch, 0, copyInts);
            ausm$clearVertexScratchTail(scratch, copyInts, targetStride);
            ausm$sanitizeAgricraftCropVertex(scratch, 0, sourceStride, agricraftCrop, agricraftPackedLight);
            ausm$applyBloomMaskVertexData(scratch, 0, bloomMaskFallback);
            ausm$applyCustomLiquidTintVertexData(scratch, 0, bloomMaskFallback, customLiquidTint);
            ausm$applyEmissiveLightmap(scratch, 0, vanillaLightmapEmission);
            scratch[ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET / Integer.BYTES] = packedEntity;
            scratch[ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET / Integer.BYTES + 1] = packedEntityHigh;
            scratch[ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET / Integer.BYTES] = BlockRenderContext.midBlock(
                    Float.intBitsToFloat(scratch[0]),
                    Float.intBitsToFloat(scratch[1]),
                    Float.intBitsToFloat(scratch[2]),
                    packedLocalPosition,
                    midBlockEmission
            );
            field_178999_b.put(scratch, 0, targetStride);
        }

        field_178997_d += vertexTotal;
        ausm$logVertexExpandProbe("quad-out", vertexData.length, sourceStride, targetStride, vertexBase, vertexTotal, field_178997_d, compatibilityEmissiveBoost);

        for (int vertex = 0; vertex + 3 < vertexTotal; vertex += 4) {
            ausm$writeDerivedBlockAttributesForPolygon(vertexBase + vertex, 4);
        }

        ausm$markCurrentContextShaderlessBloomMetadata();
        ausm$resetPipelineVertexCursor();
        ci.cancel();
    }

    @Unique
    private void ausm$logVertexExpandProbe(String stage, int sourceSize, int sourceStride, int targetStride,
                                           int vertexBase, int vertexTotal, int vertexEnd,
                                           boolean compatibilityEmissiveBoost) {
        PipelineContext pipeline = PipelineContext.getInstance();
        int call = TerrainRenderProbeState.nextVertexExpandProbe(ausm$hasUsefulVertexProbeContext(pipeline));
        if (call < 0) {
            return;
        }
        com.l.ausm.impl.MainMod.LOGGER.info(
                "[AUSMVertexExpand] call={} stage={} thread={} sourceSize={} sourceStride={} targetStride={} vertexBase={} vertexTotal={} vertexEnd={} format={} mode={} layer={} blockId={} renderType={} metadata={} emission={} vanillaEmission={} bloomMask={} packedLight=0x{} pos={}/{}/{} pipelineActive={} forceVanilla={} compatBoost={}",
                call,
                stage,
                Thread.currentThread().getName(),
                sourceSize,
                sourceStride,
                targetStride,
                vertexBase,
                vertexTotal,
                vertexEnd,
                field_179011_q,
                field_179006_k,
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(),
                BlockRenderContext.blockEntityId(),
                BlockRenderContext.renderType(),
                BlockRenderContext.metadata(),
                BlockRenderContext.blockEmission(),
                BlockRenderContext.vanillaLightmapEmission(),
                BlockRenderContext.bloomMaskFallback(),
                Integer.toHexString(BlockRenderContext.packedLightmap()),
                BlockRenderContext.blockX(),
                BlockRenderContext.blockY(),
                BlockRenderContext.blockZ(),
                pipeline.isPipelineActive(),
                pipeline.shouldForceVanillaTerrainRenderer(),
                compatibilityEmissiveBoost
        );
    }

    @Unique
    private static boolean ausm$hasUsefulVertexProbeContext(PipelineContext pipeline) {
        if (pipeline.isPipelineActive()
                || BlockRenderContext.renderType() >= 0
                || BlockRenderContext.blockEntityId() != 0
                || BlockRenderContext.blockEmission() != 0
                || BlockRenderContext.packedLightmap() != 0
                || BlockRenderContext.blockX() != 0
                || BlockRenderContext.blockY() != 0
                || BlockRenderContext.blockZ() != 0) {
            return true;
        }
        String thread = Thread.currentThread().getName();
        return thread != null && thread.contains("Chunk");
    }

    @Unique
    private static int[] ausm$vertexScratch(int size) {
        int[] scratch = AUSM$VERTEX_SCRATCH.get();
        if (scratch.length < size) {
            scratch = new int[size];
            AUSM$VERTEX_SCRATCH.set(scratch);
        }
        return scratch;
    }

    @Unique
    private static void ausm$clearVertexScratchTail(int[] scratch, int from, int to) {
        if (scratch == null || from >= to) {
            return;
        }
        for (int index = Math.max(0, from); index < to; index++) {
            scratch[index] = 0;
        }
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

    private static void ausm$sanitizeAgricraftCropVertex(int[] expandedData, int target, int sourceStride,
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
        if (!ausm$canRewriteVanillaEmissiveData() || vertexData == null) {
            return false;
        }
        if (!ausm$shouldMutateVanillaEmissiveData()) {
            ausm$markCurrentContextShaderlessBloomMetadata();
            return false;
        }

        int targetStride = ExtendedVertexFormats.integerSize(field_179011_q);
        if (targetStride <= 0 || vertexData.length <= 0 || vertexData.length % targetStride != 0) {
            return false;
        }

        int vertexTotal = vertexData.length / targetStride;
        int[] scratch = ausm$vertexScratch(targetStride);
        boolean bloomMaskFallback = BlockRenderContext.bloomMaskFallback();
        int customLiquidTint = BlockRenderContext.customLiquidTint();
        int blockAlpha = BlockRenderContext.blockAlpha();
        int colorOffset = ExtendedVertexFormats.colorOffset(field_179011_q) / Integer.BYTES;
        int targetBytes = targetStride * Integer.BYTES;
        func_181670_b(vertexData.length * Integer.BYTES + targetBytes);
        field_178999_b.position(func_181664_j());
        for (int vertex = 0; vertex < vertexTotal; vertex++) {
            int source = vertex * targetStride;
            System.arraycopy(vertexData, source, scratch, 0, targetStride);
            ausm$applyVanillaEmissiveAttributes(scratch, 0, bloomMaskFallback, customLiquidTint, blockAlpha,
                    colorOffset);
            field_178999_b.put(scratch, 0, targetStride);
        }

        field_178997_d += vertexTotal;
        ausm$markCurrentContextShaderlessBloomMetadata();
        ci.cancel();
        return true;
    }

    private boolean ausm$rewriteVanillaEmissiveBulkData(ByteBuffer sourceBuffer, CallbackInfo ci) {
        if (!ausm$canRewriteVanillaEmissiveData() || sourceBuffer == null) {
            return false;
        }
        if (!ausm$shouldMutateVanillaEmissiveData()) {
            ausm$markCurrentContextShaderlessBloomMetadata();
            return false;
        }

        ByteBuffer source = sourceBuffer.slice();
        source.order(field_179001_a.order());
        int targetStride = ExtendedVertexFormats.integerSize(field_179011_q);
        int sourceBytes = source.remaining();
        if (targetStride <= 0 || sourceBytes <= 0 || sourceBytes % (targetStride * Integer.BYTES) != 0) {
            return false;
        }

        int vertexTotal = sourceBytes / (targetStride * Integer.BYTES);
        int[] scratch = ausm$vertexScratch(targetStride);
        boolean bloomMaskFallback = BlockRenderContext.bloomMaskFallback();
        int customLiquidTint = BlockRenderContext.customLiquidTint();
        int blockAlpha = BlockRenderContext.blockAlpha();
        int colorOffset = ExtendedVertexFormats.colorOffset(field_179011_q) / Integer.BYTES;
        int targetBytes = targetStride * Integer.BYTES;
        func_181670_b(sourceBytes + targetBytes);
        field_178999_b.position(func_181664_j());
        for (int vertex = 0; vertex < vertexTotal; vertex++) {
            for (int index = 0; index < targetStride; index++) {
                scratch[index] = source.getInt();
            }
            ausm$applyVanillaEmissiveAttributes(scratch, 0, bloomMaskFallback, customLiquidTint, blockAlpha,
                    colorOffset);
            field_178999_b.put(scratch, 0, targetStride);
        }

        field_178997_d += vertexTotal;
        ausm$markCurrentContextShaderlessBloomMetadata();
        ci.cancel();
        return true;
    }

    private boolean ausm$canRewriteVanillaEmissiveData() {
        return (BlockRenderContext.blockEmission() > 0
                || BlockRenderContext.bloomMaskFallback()
                || BlockRenderContext.customLiquidTint() >= 0
                || BlockRenderContext.blockAlpha() >= 0)
                && field_179011_q != null
                && !ExtendedVertexFormats.isPipelineBlock(field_179011_q)
                && !ExtendedVertexFormats.isPipelineEntity(field_179011_q)
                && ExtendedVertexFormats.hasColor(field_179011_q)
                && ExtendedVertexFormats.hasUvOffset(field_179011_q, 1)
                && ExtendedVertexFormats.colorOffset(field_179011_q) % Integer.BYTES == 0
                && ExtendedVertexFormats.uvOffsetById(field_179011_q, 1) % Integer.BYTES == 0;
    }

    @Unique
    private static boolean ausm$shouldMutateVanillaEmissiveData() {
        return BlockRenderContext.bloomMaskFallback()
                || BlockRenderContext.customLiquidTint() >= 0
                || BlockRenderContext.blockAlpha() >= 0;
    }

    @Unique
    private void ausm$markCurrentContextShaderlessBloomMetadata() {
        if (BlockRenderContext.blockEmission() <= 0 && !BlockRenderContext.bloomMaskFallback()) {
            return;
        }
        ausm$markShaderlessBloomMetadata();
        PipelineContext.getInstance().recordCurrentShaderlessBloomMetadata(com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer());
    }

    @Unique
    private boolean ausm$shouldApplyCompatibilityEmissiveBoost() {
        return !ExtendedVertexFormats.isPipelineBlock(field_179011_q)
                || ausm$shouldApplyPipelineBlockCompatibilityEmissiveBoost();
    }

    @Unique
    private static boolean ausm$shouldApplyPipelineBlockCompatibilityEmissiveBoost() {
        return !PipelineContext.getInstance().isPipelineActive();
    }

    private void ausm$applyVanillaEmissiveAttributes(int[] vertexData, int vertexBase) {
        ausm$applyVanillaEmissiveAttributes(vertexData, vertexBase, BlockRenderContext.bloomMaskFallback(),
                BlockRenderContext.customLiquidTint(), BlockRenderContext.blockAlpha(),
                ExtendedVertexFormats.colorOffset(field_179011_q) / Integer.BYTES);
    }

    private void ausm$applyVanillaEmissiveAttributes(int[] vertexData, int vertexBase, boolean bloomMaskFallback,
                                                     int customLiquidTint, int blockAlpha, int colorOffset) {
        ausm$applyBloomMaskVertexData(vertexData, vertexBase, bloomMaskFallback);
        if (bloomMaskFallback || vertexData == null || vertexBase < 0) {
            return;
        }

        int colorIndex = vertexBase + colorOffset;
        if (colorIndex >= 0 && colorIndex < vertexData.length) {
            int before = vertexData[colorIndex];
            int color = ausm$applyCustomLiquidTintColor(vertexData[colorIndex], customLiquidTint);
            vertexData[colorIndex] = ausm$applyBlockAlpha(color, blockAlpha);
            PipelineContext pipeline = PipelineContext.getInstance();
            if (pipeline.currentProblemProbesEnabled()) {
                pipeline.logCurrentRenderContextProbe("buffer-vanilla-data-color",
                        "vertexBase=" + vertexBase
                                + ", before=0x" + Integer.toHexString(before)
                                + ", after=0x" + Integer.toHexString(vertexData[colorIndex])
                                + ", colorIndex=" + colorIndex
                                + ", format=" + field_179011_q);
            }
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

    @Inject(method = "func_181675_d", at = @At("HEAD"))
    private void ausm$applyEmissiveLightmap(CallbackInfo ci) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            ausm$clearCurrentLiquidPipelineAttributes();
            return;
        }
        ausm$sanitizeCurrentAgricraftCropVertex();
        ausm$applyBloomMaskCurrentVertex();
        ausm$applyCustomLiquidTintCurrentVertex();
        ausm$recordPipelineEmissionBloomMetadata();

        int blockEmission = ausm$shouldApplyCompatibilityEmissiveBoost()
                ? BlockRenderContext.vanillaLightmapEmission()
                : 0;
        if (blockEmission <= 0 || field_179011_q == null || !ExtendedVertexFormats.hasUvOffset(field_179011_q, 1)) {
            return;
        }

        int offset = field_178997_d * ExtendedVertexFormats.size(field_179011_q) + ExtendedVertexFormats.uvOffsetById(field_179011_q, 1);
        if (offset < 0 || offset + 4 > field_179001_a.capacity()) {
            return;
        }

        int packed = field_179001_a.getShort(offset) & 0xFFFF;
        packed |= (field_179001_a.getShort(offset + 2) & 0xFFFF) << 16;
        packed = ausm$emissiveLightmap(packed, blockEmission);
        field_179001_a.putShort(offset, (short) (packed & 0xFFFF));
        field_179001_a.putShort(offset + 2, (short) ((packed >>> 16) & 0xFFFF));
    }

    @Unique
    private void ausm$recordPipelineEmissionBloomMetadata() {
        if (!ExtendedVertexFormats.isPipelineBlock(field_179011_q)
                || BlockRenderContext.blockEmission() <= 0) {
            return;
        }
        ausm$markCurrentContextShaderlessBloomMetadata();
    }

    private void ausm$sanitizeCurrentAgricraftCropVertex() {
        if (!BlockRenderContext.isAgricraftCrop()
                || !ExtendedVertexFormats.isPipelineBlock(field_179011_q)
                || !ExtendedVertexFormats.hasUvOffset(field_179011_q, 1)) {
            return;
        }

        int offset = field_178997_d * ExtendedVertexFormats.size(field_179011_q) + ExtendedVertexFormats.uvOffsetById(field_179011_q, 1);
        if (offset < 0 || offset + 4 > field_179001_a.capacity()) {
            return;
        }
        int packedLightmap = BlockRenderContext.packedLightmap();
        field_179001_a.putShort(offset, (short) (packedLightmap & 0xFFFF));
        field_179001_a.putShort(offset + 2, (short) ((packedLightmap >>> 16) & 0xFFFF));
    }

    @Inject(method = "func_181675_d", at = @At("HEAD"))
    private void ausm$writeBlockEntityAttribute(CallbackInfo ci) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            return;
        }
        if (!ExtendedVertexFormats.isPipelineBlock(field_179011_q) && !ExtendedVertexFormats.isPipelineEntity(field_179011_q)) {
            return;
        }

        int entityOffset = ExtendedVertexFormats.isPipelineEntity(field_179011_q)
                ? ExtendedVertexFormats.PIPELINE_ENTITY_MC_ENTITY_OFFSET
                : ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET;
        int offset = field_178997_d * ExtendedVertexFormats.size(field_179011_q) + entityOffset;
        if (offset < 0 || offset + 8 > field_179001_a.capacity()) {
            return;
        }

        short entityId = (short) (ExtendedVertexFormats.isPipelineEntity(field_179011_q)
                ? PipelineContext.getInstance().currentEntityId()
                : BlockRenderContext.blockEntityId());
        field_179001_a.putShort(offset, entityId);
        field_179001_a.putShort(offset + 2, BlockRenderContext.renderType());
        field_179001_a.putShort(offset + 4, ExtendedVertexFormats.isPipelineEntity(field_179011_q) ? (short) 0 : BlockRenderContext.metadata());
        field_179001_a.putShort(offset + 6, (short) 0);
    }

    @Inject(method = "func_181675_d", at = @At("RETURN"))
    private void ausm$writeDerivedBlockAttributes(CallbackInfo ci) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            return;
        }
        if (!ExtendedVertexFormats.isPipelineBlock(field_179011_q)) {
            return;
        }

        if (field_179006_k == GL11.GL_QUADS && field_178997_d >= 4 && field_178997_d % 4 == 0) {
            ausm$writeDerivedBlockAttributesForPolygon(field_178997_d - 4, 4);
            ausm$resetPipelineVertexCursor();
        } else if (field_179006_k == GL11.GL_TRIANGLES && field_178997_d >= 3 && field_178997_d % 3 == 0) {
            ausm$writeDerivedBlockAttributesForPolygon(field_178997_d - 3, 3);
            ausm$resetPipelineVertexCursor();
        }
    }

    @Inject(method = "func_181675_d", at = @At("RETURN"))
    private void ausm$writeDerivedEntityAttributes(CallbackInfo ci) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            return;
        }
        if (!ExtendedVertexFormats.isPipelineEntity(field_179011_q)) {
            return;
        }

        if (field_179006_k == GL11.GL_QUADS && field_178997_d >= 4 && field_178997_d % 4 == 0) {
            ausm$writeDerivedEntityAttributesForPolygon(field_178997_d - 4, 4);
            ausm$resetPipelineVertexCursor();
        } else if (field_179006_k == GL11.GL_TRIANGLES && field_178997_d >= 3 && field_178997_d % 3 == 0) {
            ausm$writeDerivedEntityAttributesForPolygon(field_178997_d - 3, 3);
            ausm$resetPipelineVertexCursor();
        }
    }

    @Inject(method = "func_181675_d", at = @At("RETURN"))
    private void ausm$resetPipelineVertexCursorAfterNonQuad(CallbackInfo ci) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            if (ExtendedVertexFormats.isPipelineBlock(field_179011_q)) {
                ausm$resetPipelineVertexCursor();
            }
            return;
        }
        if (ExtendedVertexFormats.isPipelineBlock(field_179011_q)
                && !((field_179006_k == GL11.GL_QUADS && field_178997_d % 4 == 0)
                || (field_179006_k == GL11.GL_TRIANGLES && field_178997_d % 3 == 0))) {
            ausm$resetPipelineVertexCursor();
        }
    }

    @Unique
    private void ausm$clearCurrentLiquidPipelineAttributes() {
        if (!ExtendedVertexFormats.isPipelineBlock(field_179011_q)) {
            return;
        }
        int stride = ExtendedVertexFormats.size(field_179011_q);
        int start = field_178997_d * stride + ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET;
        int end = field_178997_d * stride + stride;
        if (start < 0 || end > field_179001_a.capacity() || start >= end) {
            return;
        }
        for (int offset = start; offset < end; offset++) {
            field_179001_a.put(offset, (byte) 0);
        }
    }

    @Inject(method = "func_178987_a", at = @At("RETURN"))
    private void ausm$refreshMidBlockAfterRawQuadTranslation(double x, double y, double z, CallbackInfo ci) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            return;
        }
        if (!ExtendedVertexFormats.isPipelineBlock(field_179011_q) || field_178997_d < 4) {
            return;
        }

        int stride = ExtendedVertexFormats.size(field_179011_q);
        int base = (field_178997_d - 4) * stride;
        if (base < 0 || base + 3 * stride + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 4 > field_179001_a.capacity()) {
            return;
        }

        int packedLocalPosition = BlockRenderContext.packedLocalPosition();
        int midBlockEmission = BlockRenderContext.midBlockEmission();
        for (int vertex = 0; vertex < 4; vertex++) {
            int vertexBase = base + vertex * stride;
            field_179001_a.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET, BlockRenderContext.midBlock(
                    field_179001_a.getFloat(vertexBase),
                    field_179001_a.getFloat(vertexBase + 4),
                    field_179001_a.getFloat(vertexBase + 8),
                    packedLocalPosition,
                    midBlockEmission
            ));
        }
    }

    private void ausm$resetPipelineVertexCursor() {
        field_181678_g = 0;
        field_181677_f = ExtendedVertexFormats.element(field_179011_q, 0);
    }

    private void ausm$expandPipelineEntityVertexData(int[] vertexData, CallbackInfo ci) {
        int targetStride = ExtendedVertexFormats.integerSize(field_179011_q);
        int sourceStride;
        if (vertexData.length % targetStride == 0) {
            sourceStride = targetStride;
        } else if (vertexData.length % 7 == 0) {
            sourceStride = 7;
        } else {
            return;
        }

        int vertexBase = field_178997_d;
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

        func_181670_b(expandedData.length * Integer.BYTES + ExtendedVertexFormats.size(field_179011_q));
        field_178999_b.position(func_181664_j());
        field_178999_b.put(expandedData);
        field_178997_d += vertexTotal;

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
        int stride = ExtendedVertexFormats.size(field_179011_q);
        int base = firstVertex * stride;
        if (vertexAmount < 3 || base < 0 || base + (vertexAmount - 1) * stride + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 4 > field_179001_a.capacity()) {
            return;
        }

        float v0x = field_179001_a.getFloat(base);
        float v0y = field_179001_a.getFloat(base + 4);
        float v0z = field_179001_a.getFloat(base + 8);
        float v1x = field_179001_a.getFloat(base + stride);
        float v1y = field_179001_a.getFloat(base + stride + 4);
        float v1z = field_179001_a.getFloat(base + stride + 8);
        float v2x = field_179001_a.getFloat(base + 2 * stride);
        float v2y = field_179001_a.getFloat(base + 2 * stride + 4);
        float v2z = field_179001_a.getFloat(base + 2 * stride + 8);
        int lastVertexOffset = vertexAmount == 4 ? 3 * stride : 2 * stride;
        float v3x = field_179001_a.getFloat(base + lastVertexOffset);
        float v3y = field_179001_a.getFloat(base + lastVertexOffset + 4);
        float v3z = field_179001_a.getFloat(base + lastVertexOffset + 8);

        float v0u = field_179001_a.getFloat(base + 16);
        float v0v = field_179001_a.getFloat(base + 20);
        float v1u = field_179001_a.getFloat(base + stride + 16);
        float v1v = field_179001_a.getFloat(base + stride + 20);
        float v2u = field_179001_a.getFloat(base + 2 * stride + 16);
        float v2v = field_179001_a.getFloat(base + 2 * stride + 20);
        float v3u = field_179001_a.getFloat(base + lastVertexOffset + 16);
        float v3v = field_179001_a.getFloat(base + lastVertexOffset + 20);

        float[] normal = AUSM$NORMAL_SCRATCH.get();
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
        int packedLocalPosition = BlockRenderContext.packedLocalPosition();
        int midBlockEmission = BlockRenderContext.midBlockEmission();

        for (int vertex = 0; vertex < vertexAmount; vertex++) {
            int vertexBase = base + vertex * stride;
            int tangent = packedTangent;
            if (vertexAmount == 3) {
                int vertexNormal = field_179001_a.getInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);
                tangent = IrisVertexMath.computeSmoothTangent(IrisVertexMath.unpackSnormByte(vertexNormal),
                        IrisVertexMath.unpackSnormByte(vertexNormal >> 8),
                        IrisVertexMath.unpackSnormByte(vertexNormal >> 16),
                        v0x, v0y, v0z, v0u, v0v,
                        v1x, v1y, v1z, v1u, v1v,
                        v2x, v2y, v2z, v2u, v2v);
            } else {
                field_179001_a.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET, packedNormal);
            }
            field_179001_a.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET, midU);
            field_179001_a.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET + 4, midV);
            field_179001_a.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET, tangent);
            field_179001_a.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET, BlockRenderContext.midBlock(
                    field_179001_a.getFloat(vertexBase),
                    field_179001_a.getFloat(vertexBase + 4),
                    field_179001_a.getFloat(vertexBase + 8),
                    packedLocalPosition,
                    midBlockEmission
            ));
        }
    }

    private static void ausm$applyEmissiveLightmap(int[] vertexData, int vertexBase, boolean compatibilityBoost) {
        if (!compatibilityBoost) {
            return;
        }
        int blockEmission = BlockRenderContext.vanillaLightmapEmission();
        ausm$applyEmissiveLightmap(vertexData, vertexBase, blockEmission);
        if (blockEmission > 0) {
            PipelineContext.getInstance().recordCurrentShaderlessBloomMetadata(com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer());
        }
    }

    @Unique
    private static void ausm$applyEmissiveLightmap(int[] vertexData, int vertexBase, int blockEmission) {
        if (blockEmission <= 0 || vertexData == null || vertexBase < 0 || vertexBase + 6 >= vertexData.length) {
            return;
        }
        vertexData[vertexBase + 6] = ausm$emissiveLightmap(vertexData[vertexBase + 6], blockEmission);
    }

    private static int ausm$emissiveLightmap(int packedLightmap, int blockEmission) {
        int emissiveLevel = 240;
        int block = Math.max(packedLightmap & 0xFFFF, emissiveLevel);
        int sky = Math.max((packedLightmap >>> 16) & 0xFFFF, emissiveLevel);
        return (sky << 16) | block;
    }

    private void ausm$writeDerivedEntityAttributesForPolygon(int firstVertex, int vertexAmount) {
        int stride = ExtendedVertexFormats.size(field_179011_q);
        int base = firstVertex * stride;
        if (vertexAmount < 3 || base < 0 || base + (vertexAmount - 1) * stride + ExtendedVertexFormats.PIPELINE_ENTITY_TANGENT_OFFSET + 4 > field_179001_a.capacity()) {
            return;
        }

        float v0x = field_179001_a.getFloat(base);
        float v0y = field_179001_a.getFloat(base + 4);
        float v0z = field_179001_a.getFloat(base + 8);
        float v1x = field_179001_a.getFloat(base + stride);
        float v1y = field_179001_a.getFloat(base + stride + 4);
        float v1z = field_179001_a.getFloat(base + stride + 8);
        float v2x = field_179001_a.getFloat(base + 2 * stride);
        float v2y = field_179001_a.getFloat(base + 2 * stride + 4);
        float v2z = field_179001_a.getFloat(base + 2 * stride + 8);
        int lastVertexOffset = vertexAmount == 4 ? 3 * stride : 2 * stride;
        float v3x = field_179001_a.getFloat(base + lastVertexOffset);
        float v3y = field_179001_a.getFloat(base + lastVertexOffset + 4);
        float v3z = field_179001_a.getFloat(base + lastVertexOffset + 8);

        float v0u = field_179001_a.getFloat(base + 16);
        float v0v = field_179001_a.getFloat(base + 20);
        float v1u = field_179001_a.getFloat(base + stride + 16);
        float v1v = field_179001_a.getFloat(base + stride + 20);
        float v2u = field_179001_a.getFloat(base + 2 * stride + 16);
        float v2v = field_179001_a.getFloat(base + 2 * stride + 20);
        float v3u = field_179001_a.getFloat(base + lastVertexOffset + 16);
        float v3v = field_179001_a.getFloat(base + lastVertexOffset + 20);

        float[] normal = AUSM$NORMAL_SCRATCH.get();
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
                int vertexNormal = field_179001_a.getInt(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_NORMAL_OFFSET);
                tangent = IrisVertexMath.computeSmoothTangent(IrisVertexMath.unpackSnormByte(vertexNormal),
                        IrisVertexMath.unpackSnormByte(vertexNormal >> 8),
                        IrisVertexMath.unpackSnormByte(vertexNormal >> 16),
                        v0x, v0y, v0z, v0u, v0v,
                        v1x, v1y, v1z, v1u, v1v,
                        v2x, v2y, v2z, v2u, v2v);
            } else {
                field_179001_a.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_NORMAL_OFFSET, packedNormal);
            }
            field_179001_a.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_MID_TEX_COORD_OFFSET, midU);
            field_179001_a.putFloat(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_MID_TEX_COORD_OFFSET + 4, midV);
            field_179001_a.putInt(vertexBase + ExtendedVertexFormats.PIPELINE_ENTITY_TANGENT_OFFSET, tangent);
        }
    }

    @Inject(method = "func_178978_a", at = @At("HEAD"))
    private void ausm$captureTranslucentAlpha(float redMultiplier, float greenMultiplier, float blueMultiplier, int vertexIndex, CallbackInfo ci) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            ausm$capturedTranslucentAlpha = -1;
            ausm$capturedTranslucentAlphaOffset = -1;
            return;
        }
        ausm$capturedTranslucentAlpha = -1;
        ausm$capturedTranslucentAlphaOffset = -1;
        BlockRenderLayer layer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
        if ((layer != BlockRenderLayer.TRANSLUCENT && !AusmBloomLayer.isBloomLayer(layer))
                || vertexIndex <= 0
                || vertexIndex > field_178997_d
                || field_179011_q == null
                || !ExtendedVertexFormats.hasColor(field_179011_q)) {
            return;
        }

        int colorOffset = func_78909_a(vertexIndex) * Integer.BYTES;
        if (colorOffset < 0 || colorOffset + Integer.BYTES > field_179001_a.capacity()) {
            return;
        }

        int alpha = field_179001_a.get(colorOffset + 3) & 0xFF;
        if (alpha > 0 && alpha < 255) {
            ausm$capturedTranslucentAlpha = alpha;
            ausm$capturedTranslucentAlphaOffset = colorOffset + 3;
            PipelineContext pipeline = PipelineContext.getInstance();
            if (pipeline.currentProblemProbesEnabled()) {
                pipeline.logCurrentRenderContextProbe("buffer-alpha-capture",
                        "vertexIndex=" + vertexIndex
                                + ", alpha=" + alpha
                                + ", color=0x" + Integer.toHexString(field_179001_a.getInt(colorOffset))
                                + ", colorOffset=" + colorOffset
                                + ", format=" + field_179011_q);
            }
        }
    }

    @Inject(method = "func_178978_a", at = @At("RETURN"))
    private void ausm$separateAmbientOcclusion(float redMultiplier, float greenMultiplier, float blueMultiplier, int vertexIndex, CallbackInfo ci) {
        if (BlockRendererDispatcherHooks.LIQUID_RENDER.get() != null) {
            return;
        }
        if (vertexIndex <= 0 || vertexIndex > field_178997_d) {
            return;
        }
        if (ausm$capturedTranslucentAlpha >= 0
                && ausm$capturedTranslucentAlphaOffset >= 0
                && ausm$capturedTranslucentAlphaOffset < field_179001_a.capacity()) {
            field_179001_a.put(ausm$capturedTranslucentAlphaOffset, (byte) ausm$capturedTranslucentAlpha);
        }
        if (BlockRenderContext.bloomMaskFallback()) {
            ausm$applyBloomMaskExistingVertex(vertexIndex);
        }
        int aoColorOffset = func_78909_a(vertexIndex) * Integer.BYTES;
        SeparateAoColorWriter.rewriteExistingColor(field_179011_q, field_179001_a, aoColorOffset,
                redMultiplier, greenMultiplier, blueMultiplier, vertexIndex);
        ausm$applyCustomLiquidTintExistingVertex(vertexIndex);
        if (BlockRenderContext.bloomMaskFallback()) {
            ausm$applyBloomMaskExistingVertex(vertexIndex);
        }
    }

    @Unique
    private static void ausm$applyBloomMaskVertexData(int[] vertexData, int vertexBase) {
        boolean bloomMaskFallback = BlockRenderContext.bloomMaskFallback();
        ausm$applyBloomMaskVertexData(vertexData, vertexBase, bloomMaskFallback);
        if (bloomMaskFallback) {
            PipelineContext.getInstance().recordCurrentShaderlessBloomMetadata(com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer());
        }
    }

    @Unique
    private static void ausm$applyBloomMaskVertexData(int[] vertexData, int vertexBase, boolean bloomMaskFallback) {
        if (!bloomMaskFallback || vertexData == null || vertexBase < 0 || vertexBase + 6 >= vertexData.length) {
            return;
        }
        vertexData[vertexBase + 6] = ausm$emissiveLightmap(vertexData[vertexBase + 6], 15);
    }

    @Unique
    private static void ausm$applyCustomLiquidTintVertexData(int[] vertexData, int vertexBase) {
        ausm$applyCustomLiquidTintVertexData(vertexData, vertexBase, BlockRenderContext.bloomMaskFallback(),
                BlockRenderContext.customLiquidTint());
    }

    @Unique
    private static void ausm$applyCustomLiquidTintVertexData(int[] vertexData, int vertexBase,
                                                            boolean bloomMaskFallback, int customLiquidTint) {
        if (bloomMaskFallback || customLiquidTint < 0 || vertexData == null || vertexBase < 0
                || vertexBase + 3 >= vertexData.length) {
            return;
        }
        vertexData[vertexBase + 3] = ausm$applyCustomLiquidTintColor(vertexData[vertexBase + 3], customLiquidTint);
    }

    @Unique
    private void ausm$applyCustomLiquidTintCurrentVertex() {
        if (BlockRenderContext.bloomMaskFallback()
                || BlockRenderContext.customLiquidTint() < 0
                || field_179011_q == null
                || !ExtendedVertexFormats.hasColor(field_179011_q)) {
            return;
        }
        int colorOffset = field_178997_d * ExtendedVertexFormats.size(field_179011_q) + ExtendedVertexFormats.colorOffset(field_179011_q);
        if (colorOffset < 0 || colorOffset + Integer.BYTES > field_179001_a.capacity()) {
            return;
        }
        field_179001_a.putInt(colorOffset, ausm$applyCustomLiquidTintColor(field_179001_a.getInt(colorOffset)));
        ausm$writeBlockAlpha(colorOffset);
    }

    @Unique
    private void ausm$applyCustomLiquidTintExistingVertex(int vertexIndex) {
        if (BlockRenderContext.bloomMaskFallback()
                || BlockRenderContext.customLiquidTint() < 0
                || field_179011_q == null
                || !ExtendedVertexFormats.hasColor(field_179011_q)
                || vertexIndex <= 0
                || vertexIndex > field_178997_d) {
            return;
        }
        int colorOffset = func_78909_a(vertexIndex) * Integer.BYTES;
        if (colorOffset < 0 || colorOffset + Integer.BYTES > field_179001_a.capacity()) {
            return;
        }
        field_179001_a.putInt(colorOffset, ausm$applyCustomLiquidTintColor(field_179001_a.getInt(colorOffset)));
        ausm$writeBlockAlpha(colorOffset);
    }

    @Unique
    private void ausm$applyBloomMaskCurrentVertex() {
        if (!BlockRenderContext.bloomMaskFallback() || field_179011_q == null) {
            return;
        }
        int vertexOffset = field_178997_d * ExtendedVertexFormats.size(field_179011_q);
        if (vertexOffset < 0 || vertexOffset + 12 > field_179001_a.capacity()) {
            return;
        }
        if (ExtendedVertexFormats.hasUvOffset(field_179011_q, 1)) {
            int lightOffset = vertexOffset + ExtendedVertexFormats.uvOffsetById(field_179011_q, 1);
            if (lightOffset >= 0 && lightOffset + 4 <= field_179001_a.capacity()) {
                field_179001_a.putShort(lightOffset, (short) 240);
                field_179001_a.putShort(lightOffset + 2, (short) 240);
            }
        }
        ausm$markShaderlessBloomMetadata();
        PipelineContext.getInstance().recordCurrentShaderlessBloomMetadata(com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer());
    }

    @Unique
    private void ausm$applyBloomMaskExistingVertex(int vertexIndex) {
        if (field_179011_q == null || vertexIndex <= 0 || vertexIndex > field_178997_d) {
            return;
        }
        int vertexOffset = (vertexIndex - 1) * ExtendedVertexFormats.size(field_179011_q);
        if (vertexOffset < 0 || vertexOffset + 12 > field_179001_a.capacity()) {
            return;
        }
        if (ExtendedVertexFormats.hasUvOffset(field_179011_q, 1)) {
            int lightOffset = vertexOffset + ExtendedVertexFormats.uvOffsetById(field_179011_q, 1);
            if (lightOffset >= 0 && lightOffset + 4 <= field_179001_a.capacity()) {
                field_179001_a.putShort(lightOffset, (short) 240);
                field_179001_a.putShort(lightOffset + 2, (short) 240);
            }
        }
        ausm$markShaderlessBloomMetadata();
        PipelineContext.getInstance().recordCurrentShaderlessBloomMetadata(com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer());
    }

    @Unique
    private static int ausm$applyBlockAlpha(int color) {
        return ausm$applyBlockAlpha(color, BlockRenderContext.blockAlpha());
    }

    @Unique
    private static int ausm$applyBlockAlpha(int color, int alpha) {
        if (alpha < 0) {
            return color;
        }
        return AUSM$LITTLE_ENDIAN
                ? (color & 0x00FFFFFF) | (alpha << 24)
                : (color & 0xFFFFFF00) | alpha;
    }

    @Unique
    private static int ausm$applyCustomLiquidTintColor(int color) {
        return ausm$applyCustomLiquidTintColor(color, BlockRenderContext.customLiquidTint());
    }

    @Unique
    private static int ausm$applyCustomLiquidTintColor(int color, int tint) {
        if (tint < 0) {
            return color;
        }
        return AUSM$LITTLE_ENDIAN
                ? (color & 0xFF000000) | (tint & 0x00FFFFFF)
                : (tint & 0xFFFFFF00) | (color & 0x000000FF);
    }

    @Unique
    private void ausm$writeBlockAlpha(int colorOffset) {
        int alpha = BlockRenderContext.blockAlpha();
        if (alpha >= 0 && colorOffset >= 0 && colorOffset + 3 < field_179001_a.capacity()) {
            field_179001_a.put(colorOffset + 3, (byte) alpha);
        }
    }
}
