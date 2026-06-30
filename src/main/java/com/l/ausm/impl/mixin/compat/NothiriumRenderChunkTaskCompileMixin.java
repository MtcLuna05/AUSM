package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.compat.BloomMaskColor;
import com.l.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.l.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import meldexun.nothirium.util.VisibilityGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.RenderChunkTaskCompile", remap = false)
public abstract class NothiriumRenderChunkTaskCompileMixin {
    @Unique
    private static final int AUSM_FIRE_FALLBACK_LOG_LIMIT = 0;

    @Unique
    private static final int AUSM_FIRE_COMPILE_LOG_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_FIRE_FALLBACK_LOGS = new AtomicInteger();

    @Unique
    private static final AtomicInteger AUSM_FIRE_COMPILE_LOGS = new AtomicInteger();

    @Unique
    private static final boolean AUSM_DEBUG_PROBES_ENABLED = Boolean.getBoolean("ausm.debugProbes");

    @Unique
    private static final int AUSM_EMISSIVE_FALLBACK_LOG_LIMIT = AUSM_DEBUG_PROBES_ENABLED ? 96 : 0;

    @Unique
    private static final AtomicInteger AUSM_EMISSIVE_FALLBACK_LOGS = new AtomicInteger();

    @Shadow(remap = false)
    private IBlockAccess chunkCache;

    @Unique
    private int ausm$fireCutoutFallbackStart = -1;

    @Unique
    private int ausm$emissiveFallbackStart = -1;

    @Unique
    private int ausm$framedDiagnosticStart = -1;

    @Unique
    private BlockRenderLayer ausm$framedDiagnosticLayer = null;

    @ModifyArg(
            method = "renderBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;func_181668_a(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V",
                    remap = false
            ),
            index = 1,
            remap = false
    )
    private VertexFormat ausm$usePipelineBlockFormat(VertexFormat original) {
        return NothiriumPipelineCompat.pipelineBlockFormat(original);
    }

    @Redirect(
            method = "renderBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;canRenderInLayer(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockRenderLayer;)Z",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private boolean ausm$forceEmissiveFallbackLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        if (ausm$canRenderInLayer(block, state, layer)) {
            return true;
        }
        return ausm$isEmissiveBloomFallbackTarget(state) && layer == ausm$bloomFallbackLayer(state);
    }

    @Inject(method = "renderBlockState", at = @At("HEAD"), remap = false)
    private void ausm$captureFireCutoutStart(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                             RegionRenderCacheBuilder regionBuffers, CallbackInfo ci) {
        ausm$fireCutoutFallbackStart = -1;
        ausm$emissiveFallbackStart = -1;
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState effectiveState = pipeline.effectiveBlockRenderState(state, chunkCache, pos);
        IBlockState inheritedBloomState = pipeline.inheritedBloomRenderState(state, chunkCache, pos);
        boolean originalFire = ausm$isFireFallbackTarget(state);
        boolean effectiveFire = ausm$isFireFallbackTarget(effectiveState);
        BufferBuilder buffer = regionBuffers != null ? regionBuffers.getWorldRendererByLayer(BlockRenderLayer.CUTOUT) : null;
        if (AUSM_FIRE_COMPILE_LOG_LIMIT > 0 && (originalFire || effectiveFire)) {
            ausm$logFireCompileProbe(state, effectiveState, pos, regionBuffers, buffer, originalFire, effectiveFire);
        }
        boolean framedState = pipeline.isFramedBlockDiagnosticTarget(state);
        IBlockState emissiveState = ausm$isEmissiveBloomFallbackTarget(inheritedBloomState)
                ? inheritedBloomState
                : framedState ? null : ausm$isEmissiveBloomFallbackTarget(effectiveState) ? effectiveState : state;
        if (!framedState && ausm$isEmissiveBloomFallbackTarget(emissiveState) && regionBuffers != null) {
            BufferBuilder emissiveBuffer = regionBuffers.getWorldRendererByLayer(ausm$bloomFallbackLayer(emissiveState));
            if (emissiveBuffer != null) {
                ausm$emissiveFallbackStart = emissiveBuffer.getVertexCount();
            }
        }
        if (!effectiveFire || buffer == null) {
            return;
        }

        ausm$fireCutoutFallbackStart = buffer.getVertexCount();
    }

    @Inject(
            method = "renderBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BlockRendererDispatcher;func_175018_a(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z",
                    shift = At.Shift.BEFORE,
                    remap = false
            ),
            remap = false
    )
    private void ausm$setPipelineBlockContext(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                              RegionRenderCacheBuilder bufferBuilder, CallbackInfo ci) {
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState contextState = pipeline.effectiveBlockRenderState(state, chunkCache, pos);
        if (contextState == null) {
            contextState = state;
        }

        int blockEntityId = pipeline.blockEntityId(state, chunkCache, pos);
        BlockRenderContext.setBlockEntityId(blockEntityId);
        BlockRenderContext.setRenderType((short) contextState.getRenderType().ordinal());
        BlockRenderContext.setMetadata(pipeline.blockMetadata(state, chunkCache, pos));
        BlockRenderContext.setLocalBlockPos(pos.getX(), pos.getY(), pos.getZ());
        BlockRenderContext.setWorldBlockContext(chunkCache, pos);
        BlockRenderContext.setAgricraftCrop(ausm$isAgricraftCropState(contextState));
        BlockRenderContext.setPackedLightmap(ausm$packedLightmap(contextState, chunkCache, pos));
        int blockEmission = pipeline.shouldInheritFramedEmissionInBasePass(state)
                || BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null
                ? pipeline.blockRenderEmissionWithFramedInheritance(state, chunkCache, pos)
                : pipeline.blockRenderEmission(state, chunkCache, pos);
        if (BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null) {
            blockEmission = Math.max(blockEmission, pipeline.framedBloomFallbackEmission(state, chunkCache, pos));
        }
        blockEmission = Math.max(blockEmission, pipeline.shaderlessFramedBloomExtractionEmission(state, chunkCache, pos));
        BlockRenderContext.setBlockEmission(blockEmission);
        BlockRenderContext.setBlockAlpha(pipeline.blockRenderAlpha(state, chunkCache, pos));
        BlockRenderContext.setCrystalOnlyEmission(pipeline.shouldUseCrystalOnlyEmission(state, chunkCache, pos));
        BlockRenderContext.setSeparateAoEligible(pipeline.shouldSeparateBlockAo(contextState, chunkCache, pos));
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.setBlockRenderDebugContext(state, chunkCache, pos);
        }
        pipeline.recordSyntheticLightCandidate(contextState, chunkCache, pos);

        ausm$framedDiagnosticStart = -1;
        ausm$framedDiagnosticLayer = null;
        if (pipeline.isFramedBlockDiagnosticTarget(state) && bufferBuilder != null) {
            ausm$framedDiagnosticLayer = MinecraftForgeClient.getRenderLayer();
            BufferBuilder layerBuffer = ausm$framedDiagnosticLayer != null
                    ? bufferBuilder.getWorldRendererByLayer(ausm$framedDiagnosticLayer)
                    : null;
            ausm$framedDiagnosticStart = layerBuffer != null ? layerBuffer.getVertexCount() : -1;
        }
        if (pipeline.currentProblemProbesEnabled()
                && (pipeline.isCurrentProblemProbeTarget(state)
                || pipeline.isCurrentProblemProbeTarget(contextState)
                || blockEmission > 0
                || blockEntityId != 0)) {
            BufferBuilder layerBuffer = bufferBuilder != null && MinecraftForgeClient.getRenderLayer() != null
                    ? bufferBuilder.getWorldRendererByLayer(MinecraftForgeClient.getRenderLayer())
                    : null;
            pipeline.logCurrentProblemProbe("nothirium-head", state, chunkCache, pos,
                    "context=" + pipeline.diagnosticStateName(contextState)
                            + ", blockEmission=" + blockEmission
                            + ", blockAlpha=" + BlockRenderContext.blockAlpha()
                            + ", layerBuffer=" + ausm$bufferDetails(layerBuffer));
        }
    }

    @Unique
    private static boolean ausm$isAgricraftCropState(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        ResourceLocation name = state.getBlock().getRegistryName();
        if (name == null) {
            return false;
        }
        if ("agricraft".equals(name.getNamespace()) && "crop".equals(name.getPath())) {
            return true;
        }
        return "natura".equals(name.getNamespace()) && "cotton_crop".equals(name.getPath());
    }

    @Unique
    private static int ausm$packedLightmap(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return 0;
        }
        try {
            return state.getPackedLightmapCoords(blockAccess, pos);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    @Inject(
            method = "renderBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BlockRendererDispatcher;func_175018_a(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            remap = false
    )
    private void ausm$clearPipelineBlockContext(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                                RegionRenderCacheBuilder bufferBuilder, CallbackInfo ci) {
        PipelineContext pipeline = PipelineContext.getInstance();
        if (pipeline.isFramedBlockDiagnosticTarget(state)) {
            BlockRenderLayer layer = ausm$framedDiagnosticLayer != null
                    ? ausm$framedDiagnosticLayer
                    : MinecraftForgeClient.getRenderLayer();
            BufferBuilder layerBuffer = layer != null && bufferBuilder != null
                    ? bufferBuilder.getWorldRendererByLayer(layer)
                    : null;
            if (pipeline.framedBlockDiagnosticsEnabled()) {
                pipeline.logFramedBlockDiagnostic(
                        "nothirium-dispatcher",
                        state,
                        chunkCache,
                        pos,
                        layer,
                        ausm$framedDiagnosticStart,
                        layerBuffer != null ? layerBuffer.getVertexCount() : -1,
                        null,
                        "buffer=" + (layerBuffer != null ? Integer.toHexString(System.identityHashCode(layerBuffer)) : "null")
                );
            }
            if (pipeline.currentProblemProbesEnabled()) {
                pipeline.logCurrentProblemProbe("nothirium-return", state, chunkCache, pos,
                        "layer=" + layer
                                + ", start=" + ausm$framedDiagnosticStart
                                + ", end=" + (layerBuffer != null ? layerBuffer.getVertexCount() : -1)
                                + ", buffer=" + ausm$bufferDetails(layerBuffer));
            }
        }
        ausm$framedDiagnosticStart = -1;
        ausm$framedDiagnosticLayer = null;
        BlockRenderContext.clear();
    }

    @Inject(method = "renderBlockState", at = @At("RETURN"), remap = false)
    private void ausm$renderBloomOnlyFallback(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                              RegionRenderCacheBuilder regionBuffers, CallbackInfo ci) {
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState effectiveState = pipeline.effectiveBlockRenderState(state, chunkCache, pos);
        IBlockState inheritedBloomState = pipeline.inheritedBloomRenderState(state, chunkCache, pos);
        if (ausm$renderMissingFireCutoutFallback(state, effectiveState, pos, regionBuffers)) {
            return;
        }
        IBlockState fallbackTarget = inheritedBloomState != null ? inheritedBloomState : effectiveState;
        if (ausm$renderStackedEmissiveBloomLayer(state, fallbackTarget, pos, regionBuffers)) {
            return;
        }
        if (!ausm$isBloomOnlyModelBlock(fallbackTarget) || pos == null || regionBuffers == null) {
            return;
        }

        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        BlockRenderLayer fallbackLayer = ausm$bloomFallbackLayer(fallbackTarget);
        BufferBuilder buffer = regionBuffers.getWorldRendererByLayer(fallbackLayer);
        if (buffer == null) {
            return;
        }

        if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
            buffer.begin(7, NothiriumPipelineCompat.pipelineBlockFormat(DefaultVertexFormats.BLOCK));
            int originX = Math.floorDiv(pos.getX(), 16) * 16;
            int originY = Math.floorDiv(pos.getY(), 16) * 16;
            int originZ = Math.floorDiv(pos.getZ(), 16) * 16;
            buffer.setTranslation(-originX, -originY, -originZ);
        }

        BlockRenderLayer previousLayer = MinecraftForgeClient.getRenderLayer();
        int start = buffer.getVertexCount();
        boolean rendered = false;
        try {
            // Keep the model in its native BLOOM render layer while storing the
            // resulting geometry in a vanilla Nothirium pass.
            ForgeHooksClient.setRenderLayer(bloomLayer);
            rendered = Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(fallbackTarget, pos, chunkCache, buffer);
        } finally {
            ForgeHooksClient.setRenderLayer(previousLayer);
        }

    }

    @Unique
    private boolean ausm$renderStackedEmissiveBloomLayer(
            IBlockState renderState,
            IBlockState fallbackTarget,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers
    ) {
        boolean framedState = PipelineContext.getInstance().isFramedBlockDiagnosticTarget(renderState);
        boolean forcedFramedBloom = framedState
                && PipelineContext.getInstance().framedBloomFallbackEmission(renderState, chunkCache, pos) > 0;
        IBlockState fallbackSourceState = ausm$isEmissiveBloomFallbackSource(fallbackTarget)
                ? fallbackTarget
                : forcedFramedBloom ? renderState : framedState ? null : renderState;
        boolean emissiveTarget = forcedFramedBloom || ausm$isEmissiveBloomFallbackSource(fallbackSourceState);
        IBlockState fallbackRenderState = PipelineContext.getInstance()
                .inheritedBloomGeometryRenderState(renderState, fallbackSourceState);
        try {
            if (!emissiveTarget) {
                ausm$logEmissiveFallback("skip-not-emissive-target", renderState, fallbackRenderState,
                        pos, fallbackSourceState, null, null, -1, false, 0, null, regionBuffers);
                return false;
            }
            if (pos == null || regionBuffers == null) {
                ausm$logEmissiveFallback(pos == null ? "skip-missing-pos" : "skip-missing-region-buffers",
                        renderState, fallbackRenderState, pos, fallbackSourceState, null, null, -1, false, 0,
                        null, regionBuffers);
                return false;
            }

            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            if (bloomLayer == null) {
                ausm$logEmissiveFallback("skip-missing-bloom-layer", renderState, fallbackRenderState,
                        pos, fallbackSourceState, null, null, -1, false, 0, null, regionBuffers);
                return false;
            }

            BlockRenderLayer bufferLayer = framedState ? bloomLayer : ausm$bloomFallbackLayer(fallbackSourceState);
            BufferBuilder buffer = regionBuffers.getWorldRendererByLayer(bufferLayer);
            if (buffer == null) {
                ausm$logEmissiveFallback("skip-missing-buffer", renderState, fallbackRenderState,
                        pos, fallbackSourceState, bufferLayer, null, -1, false, 0, null, regionBuffers);
                return false;
            }

            int start = ausm$emissiveFallbackStart;
            int normalDelta = start >= 0 ? buffer.getVertexCount() - start : 0;
            BlockRenderLayer renderLayer = framedState
                    ? ausm$framedGeometryLayer(fallbackRenderState, fallbackSourceState)
                    : bloomLayer;
            if (normalDelta > 0) {
                ausm$logEmissiveFallback("normal-or-dispatcher-present", renderState, fallbackRenderState, pos,
                        fallbackSourceState, bufferLayer, renderLayer, normalDelta, false, 0, buffer, regionBuffers);
                return true;
            }

            if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
                buffer.begin(7, NothiriumPipelineCompat.pipelineBlockFormat(DefaultVertexFormats.BLOCK));
                int originX = Math.floorDiv(pos.getX(), 16) * 16;
                int originY = Math.floorDiv(pos.getY(), 16) * 16;
                int originZ = Math.floorDiv(pos.getZ(), 16) * 16;
                buffer.setTranslation(-originX, -originY, -originZ);
            }

            int fallbackStart = buffer.getVertexCount();
            boolean rendered = ausm$renderEmissiveFallbackWithLayer(fallbackRenderState, fallbackSourceState, pos, buffer, renderLayer, framedState);
            int fallbackDelta = buffer.getVertexCount() - fallbackStart;
            String mode = normalDelta > 0 ? "stacked-bloom-layer" : "fallback-bloom-layer";
            ausm$logEmissiveFallback(mode, renderState, fallbackRenderState, pos,
                    fallbackSourceState, bufferLayer, renderLayer, normalDelta, rendered, fallbackDelta, buffer,
                    regionBuffers);
            return true;
        } finally {
            ausm$emissiveFallbackStart = -1;
        }
    }

    @Unique
    private static BlockRenderLayer ausm$framedGeometryLayer(IBlockState framedState, IBlockState inheritedState) {
        BlockRenderLayer framedLayer = ausm$naturalRenderLayer(framedState);
        if (framedLayer != null && !AusmBloomLayer.isBloomLayer(framedLayer)) {
            return framedLayer;
        }
        return ausm$bloomFallbackLayer(inheritedState);
    }

    @Unique
    private boolean ausm$renderEmissiveFallbackWithLayer(IBlockState state, IBlockState maskColorState, BlockPos pos, BufferBuilder buffer,
                                                        BlockRenderLayer layer, boolean bloomMaskFallback) {
        BlockRenderLayer previousLayer = MinecraftForgeClient.getRenderLayer();
        try {
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.set(Boolean.TRUE);
            if (bloomMaskFallback) {
                float[] uv = ausm$bloomMaskUv();
                BlockRenderContext.setBloomMaskFallback(true, uv[0], uv[1], BloomMaskColor.colorForState(maskColorState));
            }
            ForgeHooksClient.setRenderLayer(layer);
            return Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(state, pos, chunkCache, buffer);
        } finally {
            ForgeHooksClient.setRenderLayer(previousLayer);
            BlockRenderContext.clearBloomMaskFallback();
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.remove();
        }
    }

    @Unique
    private static float[] ausm$bloomMaskUv() {
        try {
            TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite("minecraft:blocks/quartz_block_top");
            if (sprite != null && !sprite.getIconName().contains("missingno")) {
                return new float[] {
                        (sprite.getMinU() + sprite.getMaxU()) * 0.5f,
                        (sprite.getMinV() + sprite.getMaxV()) * 0.5f
                };
            }
        } catch (RuntimeException ignored) {
            // Fallback UV is still safer than rendering a framed block's missing texture into bloom.
        }
        return new float[] {0.5f, 0.5f};
    }

    @Unique
    private static int ausm$bloomMaskColor(IBlockState sourceState) {
        if (sourceState == null) {
            return -1;
        }
        String color = ausm$statePropertyValue(sourceState, "color");
        if (color == null) {
            color = ausm$statePropertyValue(sourceState, "colour");
        }
        if (color == null) {
            return -1;
        }
        return ausm$dyeMaskColor(color);
    }

    @Unique
    private static String ausm$statePropertyValue(IBlockState state, String propertyName) {
        try {
            for (IProperty<?> property : state.getPropertyKeys()) {
                if (property != null && propertyName.equalsIgnoreCase(property.getName())) {
                    Object value = state.getValue(property);
                    return value != null ? value.toString().toLowerCase(java.util.Locale.ROOT) : null;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    @Unique
    private static int ausm$dyeMaskColor(String color) {
        String normalized = color == null ? "" : color.toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "red" -> ausm$packColor(0xFFDADA);
            case "orange", "brown" -> ausm$packColor(0xFFE2C8);
            case "yellow" -> ausm$packColor(0xFFFFC8);
            case "lime" -> ausm$packColor(0xDAFFDA);
            case "green" -> ausm$packColor(0xD0FFD0);
            case "cyan" -> ausm$packColor(0xD0FFFF);
            case "light_blue", "lightblue" -> ausm$packColor(0xD8ECFF);
            case "blue" -> ausm$packColor(0xDADAFF);
            case "purple" -> ausm$packColor(0xE8D8FF);
            case "magenta" -> ausm$packColor(0xFFD8FF);
            case "pink" -> ausm$packColor(0xFFE0F0);
            case "black", "gray", "grey", "silver", "light_gray", "light_grey", "white" -> -1;
            default -> -1;
        };
    }

    @Unique
    private static int ausm$packColor(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN
                ? (0xFF << 24) | (blue << 16) | (green << 8) | red
                : (red << 24) | (green << 16) | (blue << 8) | 0xFF;
    }

    @Unique
    private void ausm$logEmissiveFallback(String mode, IBlockState originalState, IBlockState renderState,
                                          BlockPos pos, IBlockState sourceState,
                                          BlockRenderLayer fallbackLayer, BlockRenderLayer renderLayer,
                                          int normalDelta, boolean rendered, int fallbackDelta,
                                          BufferBuilder buffer, RegionRenderCacheBuilder regionBuffers) {
        if (AUSM_EMISSIVE_FALLBACK_LOG_LIMIT <= 0) {
            return;
        }
        if (PipelineContext.getInstance().isBlockcrafteryEditableState(originalState)
                && !ausm$isEmissiveBloomFallbackTarget(sourceState)) {
            return;
        }
        int index = AUSM_EMISSIVE_FALLBACK_LOGS.incrementAndGet();
        if (index > AUSM_EMISSIVE_FALLBACK_LOG_LIMIT) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMEmissiveFallback] mode={} pos={} original={} source={} render={} fallbackLayer={} renderLayer={} normalDelta={} rendered={} fallbackDelta={} buffer={} regionBuffers={} cache={} framed={} bloomFallbackRender={} caller={}",
                mode,
                pos,
                ausm$stateName(originalState),
                ausm$stateName(sourceState),
                ausm$stateName(renderState),
                fallbackLayer,
                renderLayer,
                normalDelta,
                rendered,
                fallbackDelta,
                ausm$bufferDetails(buffer),
                regionBuffers != null ? regionBuffers.getClass().getName() : "null",
                chunkCache != null ? chunkCache.getClass().getName() : "null",
                PipelineContext.getInstance().isFramedBlockDiagnosticTarget(originalState),
                BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get(),
                ausm$externalCaller()
        );
    }

    @Unique
    private static String ausm$bufferDetails(BufferBuilder buffer) {
        if (buffer == null) {
            return "null";
        }
        VertexFormat format = buffer.getVertexFormat();
        return Integer.toHexString(System.identityHashCode(buffer))
                + "{vertices=" + buffer.getVertexCount()
                + ", drawing=" + ((IBufferBuilderExtension) buffer).ausm$isDrawing()
                + ", format=" + format
                + ", pipeline=" + ExtendedVertexFormats.isPipelineBlock(format)
                + ", stride=" + (format != null ? format.getSize() : -1)
                + "}";
    }

    @Unique
    private boolean ausm$renderMissingFireCutoutFallback(
            IBlockState renderState,
            IBlockState fallbackTarget,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers
    ) {
        boolean fireTarget = ausm$isFireFallbackTarget(fallbackTarget) || ausm$isFireFallbackTarget(renderState);
        try {
            if (!fireTarget || pos == null || regionBuffers == null) {
                return false;
            }

            BufferBuilder buffer = regionBuffers.getWorldRendererByLayer(BlockRenderLayer.CUTOUT);
            if (buffer == null) {
                return true;
            }

            int start = ausm$fireCutoutFallbackStart;
            int normalDelta = start >= 0 ? buffer.getVertexCount() - start : -1;
            IBlockState fallbackRenderState = ausm$isFireFallbackTarget(fallbackTarget) ? fallbackTarget : renderState;
            if (normalDelta > 0) {
                ausm$logFireFallback("normal-present", renderState, fallbackRenderState, pos, normalDelta, false, 0);
                return true;
            }

            if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
                buffer.begin(7, NothiriumPipelineCompat.pipelineBlockFormat(DefaultVertexFormats.BLOCK));
                int originX = Math.floorDiv(pos.getX(), 16) * 16;
                int originY = Math.floorDiv(pos.getY(), 16) * 16;
                int originZ = Math.floorDiv(pos.getZ(), 16) * 16;
                buffer.setTranslation(-originX, -originY, -originZ);
            }

            int cutoutStart = buffer.getVertexCount();
            boolean cutoutRendered = ausm$renderFireFallbackWithLayer(fallbackRenderState, pos, buffer, BlockRenderLayer.CUTOUT);
            int cutoutDelta = buffer.getVertexCount() - cutoutStart;
            ausm$logFireFallback("fallback-cutout", renderState, fallbackRenderState, pos, normalDelta, cutoutRendered,
                    cutoutDelta);
            if (cutoutDelta > 0) {
                return true;
            }

            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            if (bloomLayer == null) {
                ausm$logFireFallback("fallback-bloom-unavailable", renderState, fallbackRenderState, pos,
                        normalDelta, false, 0);
                return true;
            }

            int bloomStart = buffer.getVertexCount();
            boolean bloomRendered = ausm$renderFireFallbackWithLayer(fallbackRenderState, pos, buffer, bloomLayer);
            ausm$logFireFallback("fallback-bloom-layer", renderState, fallbackRenderState, pos, normalDelta,
                    bloomRendered, buffer.getVertexCount() - bloomStart);
            return true;
        } finally {
            ausm$fireCutoutFallbackStart = -1;
        }
    }

    @Unique
    private boolean ausm$renderFireFallbackWithLayer(IBlockState state, BlockPos pos, BufferBuilder buffer,
                                                     BlockRenderLayer layer) {
        BlockRenderLayer previousLayer = MinecraftForgeClient.getRenderLayer();
        try {
            ForgeHooksClient.setRenderLayer(layer);
            return Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(state, pos, chunkCache, buffer);
        } finally {
            ForgeHooksClient.setRenderLayer(previousLayer);
        }
    }

    @Unique
    private static void ausm$logFireFallback(String mode, IBlockState originalState, IBlockState renderState,
                                             BlockPos pos, int normalDelta, boolean rendered, int fallbackDelta) {
        if (AUSM_FIRE_FALLBACK_LOG_LIMIT <= 0) {
            return;
        }
        int index = AUSM_FIRE_FALLBACK_LOGS.incrementAndGet();
        if (index > AUSM_FIRE_FALLBACK_LOG_LIMIT) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMFire] Nothirium fire compile mode={} pos={} original={} render={} normalDelta={} rendered={} fallbackDelta={}",
                mode,
                pos,
                ausm$stateName(originalState),
                ausm$stateName(renderState),
                normalDelta,
                rendered,
                fallbackDelta
        );
    }

    @Unique
    private void ausm$logFireCompileProbe(IBlockState originalState, IBlockState effectiveState, BlockPos pos,
                                          RegionRenderCacheBuilder regionBuffers, BufferBuilder cutoutBuffer,
                                          boolean originalFire, boolean effectiveFire) {
        if (AUSM_FIRE_COMPILE_LOG_LIMIT <= 0) {
            return;
        }
        int index = AUSM_FIRE_COMPILE_LOGS.incrementAndGet();
        if (index > AUSM_FIRE_COMPILE_LOG_LIMIT) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMFireProbe] Nothirium compile call={} pos={} original={} effective={} originalFire={} effectiveFire={} cache={} buffers={} cutoutBuffer={} cutoutDrawing={} cutoutStart={} effectiveCanCutout={} effectiveCanSolid={} effectiveCanTranslucent={} effectiveRenderType={} effectiveMaterialFire={} caller={}",
                index,
                pos,
                ausm$stateName(originalState),
                ausm$stateName(effectiveState),
                originalFire,
                effectiveFire,
                chunkCache != null ? chunkCache.getClass().getName() : "null",
                regionBuffers != null ? regionBuffers.getClass().getName() : "null",
                cutoutBuffer != null ? Integer.toHexString(System.identityHashCode(cutoutBuffer)) : "null",
                cutoutBuffer != null && ((IBufferBuilderExtension) cutoutBuffer).ausm$isDrawing(),
                cutoutBuffer != null ? cutoutBuffer.getVertexCount() : -1,
                ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.CUTOUT),
                ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.SOLID),
                ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.TRANSLUCENT),
                effectiveState != null ? effectiveState.getRenderType() : null,
                effectiveState != null && effectiveState.getMaterial() == Material.FIRE,
                ausm$externalCaller()
        );
    }

    @Unique
    private static boolean ausm$isBloomOnlyModelBlock(IBlockState state) {
        if (state == null || state.getBlock() == null || state.getRenderType() == EnumBlockRenderType.INVISIBLE) {
            return false;
        }

        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (bloomLayer == null || !ausm$canRenderInLayer(state.getBlock(), state, bloomLayer)) {
            return false;
        }

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (layer == null || AusmBloomLayer.isBloomLayer(layer)) {
                continue;
            }
            if (ausm$canRenderInLayer(state.getBlock(), state, layer)) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private static BlockRenderLayer ausm$bloomFallbackLayer(IBlockState state) {
        if (state == null) {
            return BlockRenderLayer.SOLID;
        }
        ResourceLocation name = ausm$registryName(state);
        String path = name != null && name.getPath() != null ? name.getPath().toLowerCase(java.util.Locale.ROOT) : "";
        if (path.contains("fire") || state.getMaterial() == Material.FIRE) {
            return BlockRenderLayer.CUTOUT;
        }
        if (ausm$isRandomThingsLuminousState(state)) {
            return BlockRenderLayer.SOLID;
        }
        BlockRenderLayer naturalLayer = ausm$naturalRenderLayer(state);
        if (naturalLayer != null && !AusmBloomLayer.isBloomLayer(naturalLayer)) {
            return naturalLayer;
        }
        if (path.contains("translucent") || !state.isOpaqueCube() || !state.isFullCube()) {
            return BlockRenderLayer.TRANSLUCENT;
        }
        if (path.contains("luminous")) {
            return BlockRenderLayer.CUTOUT;
        }
        return BlockRenderLayer.SOLID;
    }

    @Unique
    private static boolean ausm$isRandomThingsLuminousState(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        return name != null
                && "randomthings".equals(name.getNamespace())
                && name.getPath() != null
                && name.getPath().toLowerCase(java.util.Locale.ROOT).contains("luminous");
    }

    @Unique
    private static BlockRenderLayer ausm$naturalRenderLayer(IBlockState state) {
        try {
            return state != null && state.getBlock() != null ? state.getBlock().getRenderLayer() : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Unique
    private static boolean ausm$canRenderInLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        try {
            return block != null && layer != null && block.canRenderInLayer(state, layer);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Unique
    private static boolean ausm$canRenderStateInLayer(IBlockState state, BlockRenderLayer layer) {
        return state != null && ausm$canRenderInLayer(state.getBlock(), state, layer);
    }

    @Unique
    private static boolean ausm$isEmissiveBloomFallbackTarget(IBlockState state) {
        return ausm$isEmissiveBloomFallbackSource(state);
    }

    @Unique
    private static boolean ausm$isEmissiveBloomFallbackSource(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        if (state == null || state.getBlock() == null || state.getRenderType() == EnumBlockRenderType.INVISIBLE) {
            return false;
        }
        if (PipelineContext.getInstance().isBlockcrafteryEditableState(state)) {
            return false;
        }
        if (PipelineContext.getInstance().blockIntrinsicEmission(state) > 0) {
            return true;
        }
        if (PipelineContext.getInstance().stateHasBloomLayerGeometry(state)) {
            return true;
        }
        if (name == null || name.getPath() == null) {
            return false;
        }
        String path = name.getPath().toLowerCase(java.util.Locale.ROOT);
        return path.contains("luminous") || path.contains("emissive") || path.contains("bloom");
    }

    @Unique
    private static boolean ausm$isFireFallbackTarget(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        if (name != null && "minecraft".equals(name.getNamespace()) && "fire".equals(name.getPath())) {
            return true;
        }
        return state != null && state.getMaterial() == Material.FIRE;
    }

    @Unique
    private static ResourceLocation ausm$registryName(IBlockState state) {
        return state != null && state.getBlock() != null ? state.getBlock().getRegistryName() : null;
    }

    @Unique
    private static String ausm$stateName(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        return name != null ? name.toString() : String.valueOf(state);
    }

    @Unique
    private static String ausm$externalCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(NothiriumRenderChunkTaskCompileMixin.class.getName())) {
                continue;
            }
            return className + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "unknown";
    }
}
