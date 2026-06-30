package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.compat.BloomMaskColor;
import com.l.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(BlockRendererDispatcher.class)
public class BlockRendererDispatcherMixin {
    @Unique
    private static final int AUSM_BLOCKCRAFTERY_FALLBACK_PROBE_INITIAL = 4;

    @Unique
    private static final int AUSM_BLOCKCRAFTERY_FALLBACK_PROBE_INTERVAL = 128;

    @Unique
    private static final int AUSM_BLOCKCRAFTERY_FALLBACK_PROBE_LIMIT = 8192;

    @Unique
    private static final AtomicInteger AUSM_BLOCKCRAFTERY_FALLBACK_PROBES = new AtomicInteger();

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void ausm$beforeRenderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, CallbackInfoReturnable<Boolean> cir) {
        if (BetterPortalsCompat.shouldSuppressOriginalPortalBlock(state)) {
            cir.setReturnValue(false);
            return;
        }

        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState contextState = pipeline.effectiveBlockRenderState(state, blockAccess, pos);
        if (contextState == null) {
            contextState = state;
        }
        int blockEntityId = pipeline.blockEntityId(state, blockAccess, pos);
        int blockEmission = BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null
                || pipeline.shouldInheritFramedEmissionInBasePass(state)
                ? pipeline.blockRenderEmissionWithFramedInheritance(state, blockAccess, pos)
                : pipeline.blockRenderEmission(state, blockAccess, pos);
        if (BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null) {
            blockEmission = Math.max(blockEmission, pipeline.framedBloomFallbackEmission(state, blockAccess, pos));
        }
        blockEmission = Math.max(blockEmission, pipeline.shaderlessFramedBloomExtractionEmission(state, blockAccess, pos));
        BlockRenderContext.setBlockEntityId(blockEntityId);
        BlockRenderContext.setRenderType((short) contextState.getRenderType().ordinal());
        BlockRenderContext.setMetadata(pipeline.blockMetadata(state, blockAccess, pos));
        BlockRenderContext.setLocalBlockPos(pos.getX(), pos.getY(), pos.getZ());
        BlockRenderContext.setWorldBlockContext(blockAccess, pos);
        BlockRenderContext.setAgricraftCrop(ausm$isAgricraftCropState(contextState));
        BlockRenderContext.setPackedLightmap(ausm$packedLightmap(contextState, blockAccess, pos));
        BlockRenderContext.setBlockEmission(blockEmission);
        BlockRenderContext.setBlockAlpha(pipeline.blockRenderAlpha(state, blockAccess, pos));
        BlockRenderContext.setCrystalOnlyEmission(pipeline.shouldUseCrystalOnlyEmission(state, blockAccess, pos));
        BlockRenderContext.setSeparateAoEligible(pipeline.shouldSeparateBlockAo(contextState, blockAccess, pos));
        if (pipeline.isBlockcrafteryEditableState(state)
                && AusmBloomLayer.isBloomLayer(MinecraftForgeClient.getRenderLayer())) {
            IBlockState inheritedBloomState = pipeline.inheritedBloomRenderState(state, blockAccess, pos);
            if (ausm$isEmissiveBloomFallbackSource(inheritedBloomState)) {
                float[] uv = ausm$bloomMaskUv();
                BlockRenderContext.setBloomMaskFallback(true, uv[0], uv[1], BloomMaskColor.colorForState(inheritedBloomState));
            }
        }
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.setBlockRenderDebugContext(state, blockAccess, pos);
        }
        pipeline.recordSyntheticLightCandidate(contextState, blockAccess, pos);
        if (pipeline.currentProblemProbesEnabled()
                && (pipeline.isCurrentProblemProbeTarget(state)
                || pipeline.isCurrentProblemProbeTarget(contextState)
                || blockEmission > 0
                || blockEntityId != 0)) {
            pipeline.logCurrentProblemProbe("dispatcher-head", state, blockAccess, pos,
                    "context=" + pipeline.diagnosticStateName(contextState)
                            + ", blockEmission=" + blockEmission
                            + ", blockAlpha=" + BlockRenderContext.blockAlpha()
                            + ", buffer=" + ausm$bufferDetails(bufferBuilder));
        }
        if (BlockRendererDispatcherHooks.RENDER_PROBE_LOG_LIMIT > 0 && ausm$isRenderProbeTarget(state) && bufferBuilder != null) {
            BlockRendererDispatcherHooks.PROBE_START_VERTEX.set(bufferBuilder.getVertexCount());
        } else {
            BlockRendererDispatcherHooks.PROBE_START_VERTEX.remove();
        }
        if (pipeline.isFramedBlockDiagnosticTarget(state) && bufferBuilder != null) {
            BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.set(bufferBuilder.getVertexCount());
        } else {
            BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.remove();
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

    @Inject(method = "renderBlock", at = @At("RETURN"), cancellable = true)
    private void ausm$afterRenderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, CallbackInfoReturnable<Boolean> cir) {
        if (ausm$appendBloomFallbackIfMissing(state, pos, blockAccess, bufferBuilder)) {
            cir.setReturnValue(true);
        }
        ausm$logRenderProbe(state, pos, blockAccess, bufferBuilder, cir.getReturnValue());
        Integer framedStart = BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.get();
        if (framedStart != null && bufferBuilder != null) {
            PipelineContext pipeline = PipelineContext.getInstance();
            if (pipeline.framedBlockDiagnosticsEnabled()) {
                pipeline.logFramedBlockDiagnostic(
                    "dispatcher",
                    state,
                    blockAccess,
                    pos,
                    MinecraftForgeClient.getRenderLayer(),
                    framedStart,
                    bufferBuilder.getVertexCount(),
                    cir.getReturnValue(),
                    "fallbackRender=" + String.valueOf(BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get())
                );
            }
            if (pipeline.currentProblemProbesEnabled()) {
                pipeline.logCurrentProblemProbe("dispatcher-return", state, blockAccess, pos,
                    "result=" + cir.getReturnValue()
                            + ", delta=" + (bufferBuilder.getVertexCount() - framedStart)
                            + ", fallbackRender=" + String.valueOf(BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get())
                            + ", buffer=" + ausm$bufferDetails(bufferBuilder));
            }
        }
        BlockRendererDispatcherHooks.PROBE_START_VERTEX.remove();
        BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.remove();
        BlockRenderContext.clear();
    }

    @Unique
    private static boolean ausm$appendBloomFallbackIfMissing(IBlockState state, BlockPos pos, IBlockAccess blockAccess,
                                                            BufferBuilder bufferBuilder) {
        PipelineContext pipeline = PipelineContext.getInstance();
        Integer start = BlockRendererDispatcherHooks.PROBE_START_VERTEX.get();
        boolean framedFallback = false;
        if (start == null && pipeline.isFramedBlockDiagnosticTarget(state)) {
            start = BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.get();
            framedFallback = true;
        }
        boolean framedFallbackCandidate = pipeline.isFramedBlockDiagnosticTarget(state);
        if (start == null) {
            ausm$logEmissiveDispatcherFallbackSkip("missing-start", state, null, null, pos,
                    MinecraftForgeClient.getRenderLayer(), AusmBloomLayer.layer(), null, bufferBuilder, framedFallbackCandidate);
            return false;
        }
        if (bufferBuilder == null) {
            ausm$logEmissiveDispatcherFallbackSkip("missing-buffer", state, null, null, pos,
                    MinecraftForgeClient.getRenderLayer(), AusmBloomLayer.layer(), start, null, framedFallbackCandidate);
            return false;
        }
        if (BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null) {
            ausm$logEmissiveDispatcherFallbackSkip("recursive-fallback", state, null, null, pos,
                    MinecraftForgeClient.getRenderLayer(), AusmBloomLayer.layer(), start, bufferBuilder, framedFallbackCandidate);
            return false;
        }

        IBlockState inheritedState = pipeline.inheritedBloomRenderState(state, blockAccess, pos);
        int framedEmission = pipeline.framedBloomFallbackEmission(state, blockAccess, pos);
        ausm$logBlockcrafteryBloomFallbackProbe("candidate", state, inheritedState, null, pos, blockAccess,
                MinecraftForgeClient.getRenderLayer(), AusmBloomLayer.layer(), start, -1, framedEmission,
                "buffer=" + ausm$bufferDetails(bufferBuilder));
        boolean forcedFramedBloom = framedFallbackCandidate
                && framedEmission > 0;
        IBlockState fallbackSourceState = ausm$isEmissiveBloomFallbackSource(inheritedState)
                ? inheritedState
                : forcedFramedBloom ? state : framedFallbackCandidate ? null : state;
        if (!forcedFramedBloom && !ausm$isEmissiveBloomFallbackSource(fallbackSourceState)) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-not-emissive", state, inheritedState, fallbackSourceState,
                    pos, blockAccess, MinecraftForgeClient.getRenderLayer(), AusmBloomLayer.layer(), start,
                    bufferBuilder.getVertexCount() - start, framedEmission, "forced=" + forcedFramedBloom);
            ausm$logEmissiveDispatcherFallbackSkip("not-emissive-target", state, inheritedState, fallbackSourceState, pos,
                    MinecraftForgeClient.getRenderLayer(), AusmBloomLayer.layer(), start, bufferBuilder, framedFallbackCandidate);
            return false;
        }
        IBlockState fallbackState = pipeline.inheritedBloomGeometryRenderState(state, fallbackSourceState);

        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (layer == null || bloomLayer == null) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-missing-layer", state, inheritedState, fallbackSourceState,
                    pos, blockAccess, layer, bloomLayer, start, bufferBuilder.getVertexCount() - start,
                    framedEmission, "missing=" + (layer == null ? "current" : "bloom"));
            ausm$logEmissiveDispatcherFallbackSkip(layer == null ? "missing-current-layer" : "missing-bloom-layer",
                    state, inheritedState, fallbackSourceState, pos, layer, bloomLayer, start, bufferBuilder,
                    framedFallbackCandidate);
            return false;
        }
        if (framedFallback && layer != bloomLayer) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-non-bloom-layer", state, inheritedState, fallbackSourceState,
                    pos, blockAccess, layer, bloomLayer, start, bufferBuilder.getVertexCount() - start,
                    framedEmission, "framed=true");
            ausm$logEmissiveDispatcherFallbackSkip("framed-non-bloom-layer", state, inheritedState, fallbackSourceState,
                    pos, layer, bloomLayer, start, bufferBuilder, true);
            return false;
        }
        if (!framedFallback && layer == bloomLayer) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-already-bloom-layer", state, inheritedState,
                    fallbackSourceState, pos, blockAccess, layer, bloomLayer, start,
                    bufferBuilder.getVertexCount() - start, framedEmission, "framed=false");
            ausm$logEmissiveDispatcherFallbackSkip("nonframed-already-bloom-layer", state, inheritedState,
                    fallbackSourceState, pos, layer, bloomLayer, start, bufferBuilder, false);
            return false;
        }

        int normalDelta = bufferBuilder.getVertexCount() - start;
        if (normalDelta > 0) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-normal-geometry", state, inheritedState, fallbackSourceState,
                    pos, blockAccess, layer, bloomLayer, start, normalDelta, framedEmission,
                    "fallbackState=" + ausm$stateName(fallbackState));
            ausm$logEmissiveDispatcherFallbackSkip("normal-geometry-present", state, inheritedState, fallbackSourceState,
                    pos, layer, bloomLayer, start, bufferBuilder, framedFallbackCandidate);
            return false;
        }

        BlockRenderLayer previousLayer = layer;
        BlockRenderLayer fallbackRenderLayer = framedFallback ? ausm$framedGeometryLayer(fallbackState, fallbackSourceState) : bloomLayer;
        int fallbackStart = bufferBuilder.getVertexCount();
        boolean rendered = false;
        try {
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.set(Boolean.TRUE);
            if (framedFallback) {
                float[] uv = ausm$bloomMaskUv();
                BlockRenderContext.setBloomMaskFallback(true, uv[0], uv[1], BloomMaskColor.colorForState(fallbackSourceState));
            }
            ForgeHooksClient.setRenderLayer(fallbackRenderLayer);
            rendered = Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(fallbackState, pos, blockAccess, bufferBuilder);
        } finally {
            ForgeHooksClient.setRenderLayer(previousLayer);
            BlockRenderContext.clearBloomMaskFallback();
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.remove();
        }

        int fallbackDelta = bufferBuilder.getVertexCount() - fallbackStart;
        ausm$logBlockcrafteryBloomFallbackProbe(fallbackDelta > 0 ? "rendered" : "render-empty", state,
                inheritedState, fallbackSourceState, pos, blockAccess, previousLayer, bloomLayer, fallbackStart,
                fallbackDelta, framedEmission, "fallbackLayer=" + fallbackRenderLayer
                        + ", rendered=" + rendered
                        + ", maskColor=0x" + Integer.toHexString(BloomMaskColor.colorForState(fallbackSourceState))
                        + ", fallbackState=" + ausm$stateName(fallbackState));
        ausm$logEmissiveDispatcherFallback(state, inheritedState, fallbackSourceState, fallbackState, pos, previousLayer,
                bloomLayer, fallbackRenderLayer, start, fallbackStart, normalDelta, bufferBuilder, framedFallback,
                rendered, fallbackDelta);
        return fallbackDelta > 0;
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
    private static void ausm$logEmissiveDispatcherFallback(IBlockState state, IBlockState inheritedState,
                                                          IBlockState fallbackSourceState, IBlockState fallbackState,
                                                          BlockPos pos, BlockRenderLayer baseLayer,
                                                          BlockRenderLayer bloomLayer, BlockRenderLayer renderLayer,
                                                          int start, int fallbackStart, int normalDelta,
                                                          BufferBuilder bufferBuilder, boolean framedFallback,
                                                          boolean rendered, int fallbackDelta) {
        int index = BlockRendererDispatcherHooks.EMISSIVE_DISPATCHER_FALLBACK_LOG_COUNT.incrementAndGet();
        if (index > BlockRendererDispatcherHooks.EMISSIVE_DISPATCHER_FALLBACK_LOG_LIMIT) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMEmissiveFallback] mode=dispatcher-bloom-layer call={} pos={} framed={} state={} inherited={} source={} render={} baseLayer={} bloomLayer={} renderLayer={} start={} fallbackStart={} normalDelta={} rendered={} fallbackDelta={} buffer={} caller={}",
                index,
                pos,
                framedFallback,
                ausm$stateName(state),
                ausm$stateName(inheritedState),
                ausm$stateName(fallbackSourceState),
                ausm$stateName(fallbackState),
                baseLayer,
                bloomLayer,
                renderLayer,
                start,
                fallbackStart,
                normalDelta,
                rendered,
                fallbackDelta,
                ausm$bufferDetails(bufferBuilder),
                ausm$externalCaller()
        );
    }

    @Unique
    private static void ausm$logEmissiveDispatcherFallbackSkip(String reason, IBlockState state,
                                                              IBlockState inheritedState,
                                                              IBlockState fallbackSourceState, BlockPos pos,
                                                              BlockRenderLayer layer, BlockRenderLayer bloomLayer,
                                                              Integer start, BufferBuilder bufferBuilder,
                                                              boolean framedCandidate) {
        if (!framedCandidate && !ausm$isRenderProbeTarget(state)) {
            return;
        }
        int index = BlockRendererDispatcherHooks.EMISSIVE_DISPATCHER_FALLBACK_SKIP_LOG_COUNT.incrementAndGet();
        if (index > BlockRendererDispatcherHooks.EMISSIVE_DISPATCHER_FALLBACK_SKIP_LOG_LIMIT) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMEmissiveFallbackSkip] mode=dispatcher call={} reason={} pos={} framed={} state={} inherited={} source={} layer={} bloomLayer={} start={} currentVertices={} buffer={} recursive={} caller={}",
                index,
                reason,
                pos,
                framedCandidate,
                ausm$stateName(state),
                ausm$stateName(inheritedState),
                ausm$stateName(fallbackSourceState),
                layer,
                bloomLayer,
                start,
                bufferBuilder != null ? bufferBuilder.getVertexCount() : -1,
                ausm$bufferDetails(bufferBuilder),
                BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get(),
                ausm$externalCaller()
        );
    }

    @Unique
    private static void ausm$logBlockcrafteryBloomFallbackProbe(String action, IBlockState state,
                                                               IBlockState inheritedState,
                                                               IBlockState fallbackSourceState,
                                                               BlockPos pos, IBlockAccess blockAccess,
                                                               BlockRenderLayer layer,
                                                               BlockRenderLayer bloomLayer,
                                                               Integer start, int delta,
                                                               int framedEmission, String detail) {
        PipelineContext pipeline = PipelineContext.getInstance();
        if (!pipeline.isBlockcrafteryEditableState(state) || framedEmission <= 0) {
            return;
        }
        int count = AUSM_BLOCKCRAFTERY_FALLBACK_PROBES.incrementAndGet();
        if (count > AUSM_BLOCKCRAFTERY_FALLBACK_PROBE_LIMIT
                || (count > AUSM_BLOCKCRAFTERY_FALLBACK_PROBE_INITIAL
                && count % AUSM_BLOCKCRAFTERY_FALLBACK_PROBE_INTERVAL != 0)) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMBlockcrafteryBloomFallbackProbe] call={} action={} pos={} layer={} bloomLayer={} start={} delta={} framedEmission={} state={} inherited={} source={} access={} detail={}",
                count,
                action,
                pos,
                layer,
                bloomLayer,
                start,
                delta,
                framedEmission,
                pipeline.diagnosticStateName(state),
                pipeline.diagnosticStateName(inheritedState),
                pipeline.diagnosticStateName(fallbackSourceState),
                blockAccess != null ? blockAccess.getClass().getName() : "null",
                detail
        );
    }

    @Unique
    private static String ausm$bufferDetails(BufferBuilder bufferBuilder) {
        if (bufferBuilder == null) {
            return "null";
        }
        VertexFormat format = bufferBuilder.getVertexFormat();
        return Integer.toHexString(System.identityHashCode(bufferBuilder))
                + "{vertices=" + bufferBuilder.getVertexCount()
                + ", drawing=" + ((com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension) bufferBuilder).ausm$isDrawing()
                + ", format=" + format
                + ", pipeline=" + ExtendedVertexFormats.isPipelineBlock(format)
                + ", stride=" + (format != null ? format.getSize() : -1)
                + "}";
    }

    @Unique
    private static boolean ausm$isRenderProbeTarget(IBlockState state) {
        if (BlockRendererDispatcherHooks.RENDER_PROBE_LOG_LIMIT <= 0
                && BlockRendererDispatcherHooks.EMISSIVE_DISPATCHER_FALLBACK_SKIP_LOG_LIMIT <= 0) {
            return false;
        }
        ResourceLocation name = ausm$registryName(state);
        if (name == null) {
            return false;
        }
        String namespace = name.getNamespace();
        String path = name.getPath() != null ? name.getPath().toLowerCase(java.util.Locale.ROOT) : "";
        Block block = state.getBlock();
        String className = block != null ? block.getClass().getName().toLowerCase(java.util.Locale.ROOT) : "";
        return "minecraft".equals(namespace) && "fire".equals(path)
                || "architecturecraft".equals(namespace)
                || namespace.contains("architecture")
                || path.contains("architecture")
                || path.contains("fire")
                || path.contains("luminous")
                || path.contains("glass")
                || path.contains("translucent")
                || className.contains("architecture")
                || className.endsWith(".blockfire")
                || className.contains(".blockfire")
                || className.contains("glass")
                || className.contains("translucent")
                || state.getMaterial() == Material.FIRE;
    }

    @Unique
    private static boolean ausm$isEmissiveBloomFallbackTarget(IBlockState state) {
        return ausm$isEmissiveBloomFallbackSource(state);
    }

    @Unique
    private static boolean ausm$isEmissiveBloomFallbackSource(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        if (state == null || state.getBlock() == null || name == null || name.getPath() == null) {
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
        String path = name.getPath().toLowerCase(java.util.Locale.ROOT);
        return path.contains("luminous") || path.contains("emissive") || path.contains("bloom");
    }

    @Unique
    private static BlockRenderLayer ausm$bloomFallbackLayer(IBlockState state) {
        if (ausm$isRandomThingsLuminousState(state)) {
            return BlockRenderLayer.SOLID;
        }
        BlockRenderLayer naturalLayer = ausm$naturalRenderLayer(state);
        if (naturalLayer != null && !AusmBloomLayer.isBloomLayer(naturalLayer)) {
            return naturalLayer;
        }
        if (state != null && (!state.isOpaqueCube() || !state.isFullCube())) {
            return BlockRenderLayer.TRANSLUCENT;
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
    private static void ausm$logRenderProbe(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, Boolean result) {
        Integer start = BlockRendererDispatcherHooks.PROBE_START_VERTEX.get();
        if (start == null || bufferBuilder == null || !ausm$isRenderProbeTarget(state)) {
            return;
        }

        ResourceLocation name = ausm$registryName(state);
        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
        String key = ausm$dimensionId(blockAccess) + "|" + String.valueOf(name) + "|" + String.valueOf(layer)
                + "|" + ausm$accessName(blockAccess);
        if (!BlockRendererDispatcherHooks.PROBE_LOGGED.add(key)) {
            return;
        }
        int logIndex = BlockRendererDispatcherHooks.PROBE_LOG_COUNT.incrementAndGet();
        if (logIndex > BlockRendererDispatcherHooks.RENDER_PROBE_LOG_LIMIT) {
            return;
        }

        int end = bufferBuilder.getVertexCount();
        int delta = end - start;
        VertexFormat format = bufferBuilder.getVertexFormat();
        int stride = format != null ? format.getSize() : -1;
        int color = 0;
        int alpha = -1;
        int lightU = -1;
        int lightV = -1;
        float x = Float.NaN;
        float y = Float.NaN;
        float z = Float.NaN;
        int blockEntity = 0;
        int renderType = 0;
        int midBlock = 0;
        float u = Float.NaN;
        float v = Float.NaN;

        if (delta > 0 && format != null && stride > 0) {
            ByteBuffer bytes = bufferBuilder.getByteBuffer();
            int base = start * stride;
            if (base >= 0 && base + stride <= bytes.capacity()) {
                x = bytes.getFloat(base);
                y = bytes.getFloat(base + 4);
                z = bytes.getFloat(base + 8);
                if (format.hasColor()) {
                    int offset = base + format.getColorOffset();
                    if (offset >= 0 && offset + 4 <= bytes.capacity()) {
                        color = bytes.getInt(offset);
                        alpha = (color >>> 24) & 0xFF;
                    }
                }
                if (format.hasUvOffset(0)) {
                    int offset = base + format.getUvOffsetById(0);
                    if (offset >= 0 && offset + 8 <= bytes.capacity()) {
                        u = bytes.getFloat(offset);
                        v = bytes.getFloat(offset + 4);
                    }
                }
                if (format.hasUvOffset(1)) {
                    int offset = base + format.getUvOffsetById(1);
                    if (offset >= 0 && offset + 4 <= bytes.capacity()) {
                        lightU = bytes.getShort(offset) & 0xFFFF;
                        lightV = bytes.getShort(offset + 2) & 0xFFFF;
                    }
                }
                if (ExtendedVertexFormats.isPipelineBlock(format)) {
                    int entityOffset = base + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET;
                    int midBlockOffset = base + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET;
                    if (entityOffset >= 0 && entityOffset + 8 <= bytes.capacity()) {
                        blockEntity = bytes.getShort(entityOffset) & 0xFFFF;
                        renderType = bytes.getShort(entityOffset + 2);
                    }
                    if (midBlockOffset >= 0 && midBlockOffset + 4 <= bytes.capacity()) {
                        midBlock = bytes.getInt(midBlockOffset);
                    }
                }
            }
        }

        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        MainMod.LOGGER.info(
                "[AUSMBlockProbe] dispatcher call={} dim={} pos={} state={} class={} access={} layer={} naturalLayer={} canLayer={} canSolid={} canCutoutMipped={} canCutout={} canTranslucent={} canBloom={} opaque={} full={} result={} delta={} format={} pipelineFormat={} stride={} alpha={} color={} uv={}/{} light={}/{} entity={} renderType={} midBlock={} firstVertex={},{},{} materialFire={} caller={}",
                logIndex,
                ausm$dimensionId(blockAccess),
                pos,
                name,
                state.getBlock() != null ? state.getBlock().getClass().getName() : "null",
                ausm$accessName(blockAccess),
                layer,
                ausm$naturalRenderLayer(state),
                ausm$canRenderInLayer(state, layer),
                ausm$canRenderInLayer(state, BlockRenderLayer.SOLID),
                ausm$canRenderInLayer(state, BlockRenderLayer.CUTOUT_MIPPED),
                ausm$canRenderInLayer(state, BlockRenderLayer.CUTOUT),
                ausm$canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT),
                bloomLayer != null && ausm$canRenderInLayer(state, bloomLayer),
                state.isOpaqueCube(),
                state.isFullCube(),
                result,
                delta,
                format,
                ExtendedVertexFormats.isPipelineBlock(format),
                stride,
                alpha,
                "0x" + Integer.toHexString(color),
                u,
                v,
                lightU,
                lightV,
                blockEntity,
                renderType,
                midBlock,
                x,
                y,
                z,
                state.getMaterial() == Material.FIRE,
                ausm$externalCaller()
        );
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
    private static int ausm$dimensionId(IBlockAccess blockAccess) {
        if (blockAccess instanceof World world && world.provider != null) {
            return world.provider.getDimension();
        }
        return Integer.MIN_VALUE;
    }

    @Unique
    private static String ausm$accessName(IBlockAccess blockAccess) {
        return blockAccess != null ? blockAccess.getClass().getName() : "null";
    }

    @Unique
    private static boolean ausm$canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        try {
            return state != null && state.getBlock() != null && layer != null && state.getBlock().canRenderInLayer(state, layer);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
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
    private static String ausm$externalCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(BlockRendererDispatcherMixin.class.getName())
                    || className.equals(BlockRendererDispatcher.class.getName())) {
                continue;
            }
            return className + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "unknown";
    }
}
