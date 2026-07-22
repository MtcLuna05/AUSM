package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.l.ausm.impl.pipeline.compat.TerrainRenderProbeState;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
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
    private static final AtomicInteger AUSM$LIQUID_PROBE_COUNT = new AtomicInteger();

    @Inject(method = "func_175018_a(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z", at = @At("HEAD"), remap = false, cancellable = true)
    private void ausm$beforeRenderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, CallbackInfoReturnable<Boolean> cir) {
        try {
            BlockRendererDispatcherHooks.LIQUID_RENDER.remove();
            Block block = MinecraftReflectionCompat.blockFromState(state);
            BlockRenderLayer naturalLayer = block != null
                    ? MinecraftReflectionCompat.blockRenderLayer(block)
                    : null;
            if (naturalLayer != null && MinecraftReflectionCompat.currentRenderLayer() == null) {
                MinecraftReflectionCompat.setCurrentRenderLayer(naturalLayer);
            }
            if (MinecraftReflectionCompat.stateIsLiquid(state)) {
                // Fluid rendering is already complete vanilla geometry. Do
                // not attach AUSM metadata writes to CCL/Nothirium's liquid
                // buffer; those buffers are also consumed by native GL draw
                // calls after the worker returns.
                BlockRendererDispatcherHooks.LIQUID_RENDER.set(Boolean.TRUE);
                TerrainRenderProbeState.clearTerrainDispatchStart();
                BlockRendererDispatcherHooks.PROBE_START_VERTEX.remove();
                BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.remove();
                BlockRendererDispatcherHooks.SOFT_VANILLA_SPECIAL_START_VERTEX.remove();
                BlockRenderContext.clear();
                boolean rendered = MinecraftReflectionCompat.renderLiquidBlock(
                        (BlockRendererDispatcher) (Object) this, blockAccess, state, pos, bufferBuilder);
                int probe = AUSM$LIQUID_PROBE_COUNT.incrementAndGet();
                if (probe <= 64) {
                    MainMod.LOGGER.info("[AUSMLiquidDispatchProbe] call={} stage=dispatcher-head thread={} pos={} state={} access={} buffer={} layer={} rendered={} fluidRendererField={}",
                            probe, Thread.currentThread().getName(), pos, state,
                            blockAccess != null ? blockAccess.getClass().getName() : "null",
                            bufferBuilder != null ? Integer.toHexString(System.identityHashCode(bufferBuilder)) : "null",
                            MinecraftReflectionCompat.currentRenderLayer(), rendered,
                            MinecraftReflectionCompat.hasField((BlockRendererDispatcher) (Object) this, "field_175025_e", "fluidRenderer"));
                }
                cir.setReturnValue(rendered);
                return;
            }
            PipelineContext pipeline = PipelineContext.getInstance();
            IBlockState actualState = pipeline.actualBlockRenderState(state, blockAccess, pos);
            IBlockState contextState = pipeline.effectiveBlockRenderState(state, actualState, blockAccess, pos);
            if (contextState == null) {
                contextState = state;
            }
            int startVertex = bufferBuilder != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1;
            TerrainRenderProbeState.setTerrainDispatchStart(startVertex);
            int blockEntityId = pipeline.blockEntityIdForActualState(actualState, blockAccess, pos);
            int blockEmission = pipeline.shouldUseShaderlessBloomEmission()
                ? pipeline.blockShaderlessBloomEmission(state, blockAccess, pos)
                : (BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null
                || pipeline.shouldInheritFramedEmissionInBasePass(state))
                        ? pipeline.blockRenderEmissionWithFramedInheritance(state, blockAccess, pos)
                        : pipeline.blockRenderEmission(state, blockAccess, pos);
        if (BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null) {
            blockEmission = Math.max(blockEmission, pipeline.framedBloomFallbackEmission(state, blockAccess, pos));
        }
        int framedShaderlessExtractionEmission = pipeline.shaderlessFramedBloomExtractionEmission(state, blockAccess, pos);
        blockEmission = Math.max(blockEmission, framedShaderlessExtractionEmission);
        BlockRenderContext.setBlockEntityId(blockEntityId);
        BlockRenderContext.setRenderType((short) com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderTypeOrdinal(contextState));
        BlockRenderContext.setMetadata(pipeline.blockMetadataForActualState(actualState));
        BlockRenderContext.setLocalBlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos));
        BlockRenderContext.setWorldBlockContext(blockAccess, pos);
        BlockRenderContext.setAgricraftCrop(ausm$isAgricraftCropState(contextState));
        int packedLightmap = ausm$packedLightmap(contextState, blockAccess, pos);
        BlockRenderContext.setPackedLightmap(packedLightmap);
        ausm$logShaderlessDispatchLightProbe(pipeline, state, contextState, blockAccess, pos, packedLightmap);
        BlockRenderContext.setBlockEmission(blockEmission);
        BlockRenderContext.setBloomOnlyEmission(framedShaderlessExtractionEmission > 0);
        BlockRenderContext.setBlockAlpha(pipeline.blockRenderAlpha(state, blockAccess, pos));
        BlockRenderContext.setCustomLiquidTint(pipeline.customLiquidTintColor(state, blockAccess, pos));
        BlockRenderContext.setCrystalOnlyEmission(pipeline.shouldUseCrystalOnlyEmission(actualState));
        BlockRenderContext.setSeparateAoEligible(pipeline.shouldSeparateBlockAo(contextState));
        if (pipeline.shouldProbeSoftVanillaSpecialBlock(state, contextState, blockAccess, pos)) {
            BlockRendererDispatcherHooks.SOFT_VANILLA_SPECIAL_START_VERTEX.set(startVertex);
            pipeline.logSoftVanillaSpecialBlockProbe("dispatcher-head", state, blockAccess, pos, startVertex, startVertex, null,
                    "context=" + pipeline.diagnosticStateName(contextState)
                            + ", emission=" + blockEmission
                            + ", blockId=" + blockEntityId
                            + ", alpha=" + BlockRenderContext.blockAlpha()
                            + ", packedLight=0x" + Integer.toHexString(packedLightmap)
                            + ", buffer=" + ausm$bufferDetails(bufferBuilder));
        } else {
            BlockRendererDispatcherHooks.SOFT_VANILLA_SPECIAL_START_VERTEX.remove();
        }
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.setBlockRenderDebugContext(state, blockAccess, pos);
        }
        pipeline.recordSyntheticLightCandidate(contextState, blockAccess, pos);
        ausm$logTerrainDispatchProbe("head", state, contextState, pos, blockAccess, bufferBuilder, startVertex, -1,
                null, blockEntityId, blockEmission, packedLightmap);
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
            BlockRendererDispatcherHooks.PROBE_START_VERTEX.set(com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder));
        } else {
            BlockRendererDispatcherHooks.PROBE_START_VERTEX.remove();
        }
            if (pipeline.isFramedBlockDiagnosticTarget(state) && bufferBuilder != null) {
                BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.set(com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder));
            } else {
                BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.remove();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // CCL and other dispatcher wrappers must be able to continue when
            // an optional AUSM compatibility lookup is unavailable.
            TerrainRenderProbeState.clearTerrainDispatchStart();
            BlockRendererDispatcherHooks.PROBE_START_VERTEX.remove();
            BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.remove();
            BlockRendererDispatcherHooks.SOFT_VANILLA_SPECIAL_START_VERTEX.remove();
            BlockRendererDispatcherHooks.LIQUID_RENDER.remove();
            BlockRenderContext.clear();
        }
    }

    @Unique
    private static void ausm$logTerrainDispatchProbe(String stage, IBlockState state, IBlockState contextState,
                                                     BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder,
                                                     int startVertex, int endVertex, Boolean result,
                                                     int blockEntityId, int blockEmission, int packedLightmap) {
        int call = TerrainRenderProbeState.nextTerrainDispatchProbe();
        if (call < 0) {
            return;
        }
        PipelineContext pipeline = PipelineContext.getInstance();
        Block block = state != null ? MinecraftReflectionCompat.blockFromState(state) : null;
        Block contextBlock = contextState != null ? MinecraftReflectionCompat.blockFromState(contextState) : null;
        MainMod.LOGGER.info(
                "[AUSMTerrainDispatch] call={} stage={} thread={} pos={} layer={} state={} context={} block={} contextBlock={} start={} end={} delta={} result={} buffer={} drawing={} format={} blockId={} emission={} packedLight=0x{} pipelineActive={} forceVanilla={} access={}",
                call,
                stage,
                Thread.currentThread().getName(),
                pos,
                MinecraftReflectionCompat.currentRenderLayer(),
                pipeline.diagnosticStateName(state),
                pipeline.diagnosticStateName(contextState),
                block != null ? MinecraftReflectionCompat.blockRegistryName(block) : null,
                contextBlock != null ? MinecraftReflectionCompat.blockRegistryName(contextBlock) : null,
                startVertex,
                endVertex,
                startVertex >= 0 && endVertex >= 0 ? endVertex - startVertex : -1,
                result,
                ausm$bufferDetails(bufferBuilder),
                bufferBuilder instanceof IBufferBuilderExtension extension && extension.ausm$isDrawing(),
                bufferBuilder != null ? MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder) : null,
                blockEntityId,
                blockEmission,
                Integer.toHexString(packedLightmap),
                pipeline.isPipelineActive(),
                pipeline.shouldForceVanillaTerrainRenderer(),
                blockAccess != null ? blockAccess.getClass().getName() : null
        );
    }

    @Unique
    private static boolean ausm$isAgricraftCropState(IBlockState state) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        ResourceLocation name = com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state));
        if (name == null) {
            return false;
        }
        if ("agricraft".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name)) && "crop".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name))) {
            return true;
        }
        return "natura".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name)) && "cotton_crop".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name));
    }

    @Unique
    private static int ausm$packedLightmap(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return 0;
        }
        try {
            int packedLightmap = com.l.ausm.impl.util.MinecraftReflectionCompat.statePackedLightmapCoords(state, blockAccess, pos);
            return PipelineContext.getInstance().repairShaderlessVoidWorldPackedLight(blockAccess, pos, packedLightmap);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    @Unique
    private static void ausm$logShaderlessDispatchLightProbe(
            PipelineContext pipeline,
            IBlockState originalState,
            IBlockState contextState,
            IBlockAccess blockAccess,
            BlockPos pos,
            int packedLightmap
    ) {
        // Probe disabled.
}

    @Unique
    private static int ausm$safeCombinedLight(IBlockAccess blockAccess, BlockPos pos, int lightValue) {
        if (blockAccess == null || pos == null) {
            return -1;
        }
        try {
            return com.l.ausm.impl.util.MinecraftReflectionCompat.blockAccessCombinedLight(blockAccess, pos, lightValue);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    @Unique
    private static int ausm$safeStateLightValue(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return 0;
        }
        try {
            return com.l.ausm.impl.util.MinecraftReflectionCompat.stateLightValue(state, blockAccess, pos);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    @Inject(method = "func_175018_a(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z", at = @At("RETURN"), remap = false, cancellable = true)
    private void ausm$afterRenderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, CallbackInfoReturnable<Boolean> cir) {
        if (ausm$appendBloomFallbackIfMissing(state, pos, blockAccess, bufferBuilder)) {
            cir.setReturnValue(true);
        }
        PipelineContext pipeline = PipelineContext.getInstance();
        Integer terrainStart = TerrainRenderProbeState.terrainDispatchStart();
        int terrainEnd = bufferBuilder != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1;
        ausm$logTerrainDispatchProbe("return", state, pipeline.effectiveBlockRenderState(state, blockAccess, pos), pos,
                blockAccess, bufferBuilder, terrainStart != null ? terrainStart : -1, terrainEnd, cir.getReturnValue(),
                BlockRenderContext.blockEntityId(), BlockRenderContext.blockEmission(), BlockRenderContext.packedLightmap());
        TerrainRenderProbeState.clearTerrainDispatchStart();
        ausm$logRenderProbe(state, pos, blockAccess, bufferBuilder, cir.getReturnValue());
        Integer framedStart = BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.get();
        if (framedStart != null && bufferBuilder != null) {
        // Probe disabled.
}
        Integer softVanillaSpecialStart = BlockRendererDispatcherHooks.SOFT_VANILLA_SPECIAL_START_VERTEX.get();
        if (softVanillaSpecialStart != null) {
            pipeline.logSoftVanillaSpecialBlockProbe("dispatcher-return", state, blockAccess, pos,
                    softVanillaSpecialStart,
                    bufferBuilder != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1,
                    cir.getReturnValue(),
                    "buffer=" + ausm$bufferDetails(bufferBuilder));
        }
        BlockRendererDispatcherHooks.PROBE_START_VERTEX.remove();
        BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.remove();
        BlockRendererDispatcherHooks.SOFT_VANILLA_SPECIAL_START_VERTEX.remove();
        BlockRendererDispatcherHooks.LIQUID_RENDER.remove();
        BlockRenderContext.clear();
    }

    @Unique
    private static boolean ausm$appendBloomFallbackIfMissing(IBlockState state, BlockPos pos, IBlockAccess blockAccess,
                                                            BufferBuilder bufferBuilder) {
        PipelineContext pipeline = PipelineContext.getInstance();
        if (!pipeline.isManualBloomExtractionEnabled()) {
            return false;
        }
        Integer start = BlockRendererDispatcherHooks.PROBE_START_VERTEX.get();
        boolean framedFallback = false;
        if (start == null && pipeline.isFramedBlockDiagnosticTarget(state)) {
            start = BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.get();
            framedFallback = true;
        }
        boolean framedFallbackCandidate = pipeline.isFramedBlockDiagnosticTarget(state);
        if (start == null) {
            ausm$logEmissiveDispatcherFallbackSkip("missing-start", state, null, null, pos,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer(), null, bufferBuilder, framedFallbackCandidate);
            return false;
        }
        if (bufferBuilder == null) {
            ausm$logEmissiveDispatcherFallbackSkip("missing-buffer", state, null, null, pos,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer(), start, null, framedFallbackCandidate);
            return false;
        }
        if (BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null) {
            ausm$logEmissiveDispatcherFallbackSkip("recursive-fallback", state, null, null, pos,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer(), start, bufferBuilder, framedFallbackCandidate);
            return false;
        }

        IBlockState inheritedState = pipeline.inheritedBloomRenderState(state, blockAccess, pos);
        int framedEmission = pipeline.framedBloomFallbackEmission(state, blockAccess, pos);
        ausm$logBlockcrafteryBloomFallbackProbe("candidate", state, inheritedState, null, pos, blockAccess,
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer(), start, -1, framedEmission,
                "buffer=" + ausm$bufferDetails(bufferBuilder));
        boolean forcedFramedBloom = framedFallbackCandidate
                && framedEmission > 0;
        IBlockState fallbackSourceState = ausm$isEmissiveBloomFallbackSource(inheritedState)
                ? inheritedState
                : forcedFramedBloom ? state : framedFallbackCandidate ? null : state;
        if (!forcedFramedBloom && !ausm$isEmissiveBloomFallbackSource(fallbackSourceState)) {
        // Probe disabled.
}
        IBlockState fallbackState = pipeline.inheritedBloomGeometryRenderState(state, fallbackSourceState);

        BlockRenderLayer layer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (layer == null || bloomLayer == null) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-missing-layer", state, inheritedState, fallbackSourceState,
                    pos, blockAccess, layer, bloomLayer, start, com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) - start,
                    framedEmission, "missing=" + (layer == null ? "current" : "bloom"));
            ausm$logEmissiveDispatcherFallbackSkip(layer == null ? "missing-current-layer" : "missing-bloom-layer",
                    state, inheritedState, fallbackSourceState, pos, layer, bloomLayer, start, bufferBuilder,
                    framedFallbackCandidate);
            return false;
        }
        if (framedFallback && layer != bloomLayer) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-non-bloom-layer", state, inheritedState, fallbackSourceState,
                    pos, blockAccess, layer, bloomLayer, start, com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) - start,
                    framedEmission, "framed=true");
            ausm$logEmissiveDispatcherFallbackSkip("framed-non-bloom-layer", state, inheritedState, fallbackSourceState,
                    pos, layer, bloomLayer, start, bufferBuilder, true);
            return false;
        }
        if (!framedFallback && layer == bloomLayer) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-already-bloom-layer", state, inheritedState,
                    fallbackSourceState, pos, blockAccess, layer, bloomLayer, start,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) - start, framedEmission, "framed=false");
            ausm$logEmissiveDispatcherFallbackSkip("nonframed-already-bloom-layer", state, inheritedState,
                    fallbackSourceState, pos, layer, bloomLayer, start, bufferBuilder, false);
            return false;
        }

        boolean textureBloomSource = pipeline.stateUsesTextureBloomSource(fallbackSourceState);
        boolean solidBloomMaskFallback = framedFallback && !textureBloomSource;
        IBlockState fallbackGeometryState = solidBloomMaskFallback ? state : fallbackState;
        int normalDelta = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) - start;
        if (normalDelta > 0 && solidBloomMaskFallback) {
            ((com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension) bufferBuilder).ausm$truncateVertexCount(start);
            ausm$logBlockcrafteryBloomFallbackProbe("replace-normal-geometry", state, inheritedState, fallbackSourceState,
                    pos, blockAccess, layer, bloomLayer, start, normalDelta, framedEmission,
                    "fallbackState=" + ausm$stateName(fallbackState));
            normalDelta = 0;
        } else if (normalDelta > 0) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-normal-geometry", state, inheritedState, fallbackSourceState,
                    pos, blockAccess, layer, bloomLayer, start, normalDelta, framedEmission,
                    "fallbackState=" + ausm$stateName(fallbackState));
            ausm$logEmissiveDispatcherFallbackSkip("normal-geometry-present", state, inheritedState, fallbackSourceState,
                    pos, layer, bloomLayer, start, bufferBuilder, framedFallbackCandidate);
            return false;
        }

        BlockRenderLayer previousLayer = layer;
        BlockRenderLayer fallbackRenderLayer = framedFallback && !textureBloomSource
                ? ausm$framedGeometryLayer(fallbackGeometryState, fallbackSourceState)
                : ausm$bloomFallbackLayer(fallbackSourceState);
        int fallbackStart = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder);
        boolean rendered = false;
        try {
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.set(Boolean.TRUE);
            if (solidBloomMaskFallback) {
                BlockRenderContext.setBloomMaskFallback(true);
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(fallbackRenderLayer);
            BlockRendererDispatcher dispatcher = com.l.ausm.impl.util.MinecraftReflectionCompat.blockRendererDispatcher(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
            rendered = dispatcher != null && com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlock(dispatcher, fallbackGeometryState, pos, blockAccess, bufferBuilder);
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
            BlockRenderContext.clearBloomMaskFallback();
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.remove();
        }

        int fallbackDelta = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) - fallbackStart;
        ausm$logBlockcrafteryBloomFallbackProbe(fallbackDelta > 0 ? "rendered" : "render-empty", state,
                inheritedState, fallbackSourceState, pos, blockAccess, previousLayer, bloomLayer, fallbackStart,
                fallbackDelta, framedEmission, "fallbackLayer=" + fallbackRenderLayer
                        + ", rendered=" + rendered
                        + ", fallbackState=" + ausm$stateName(fallbackGeometryState));
        ausm$logEmissiveDispatcherFallback(state, inheritedState, fallbackSourceState, fallbackGeometryState, pos, previousLayer,
                bloomLayer, fallbackRenderLayer, start, fallbackStart, normalDelta, bufferBuilder, framedFallback,
                rendered, fallbackDelta);
        return fallbackDelta > 0;
    }

    @Unique
    private static BlockRenderLayer ausm$framedGeometryLayer(IBlockState framedState, IBlockState inheritedState) {
        BlockRenderLayer inheritedLayer = ausm$bloomFallbackLayer(inheritedState);
        if (inheritedLayer != null && !AusmBloomLayer.isBloomLayer(inheritedLayer)) {
            return inheritedLayer;
        }
        BlockRenderLayer framedLayer = ausm$naturalRenderLayer(framedState);
        if (framedLayer != null && !AusmBloomLayer.isBloomLayer(framedLayer)) {
            return framedLayer;
        }
        return BlockRenderLayer.SOLID;
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
                bufferBuilder != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1,
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
        if (!PipelineContext.getInstance().isBlockcrafteryEditableState(state)) {
            return;
        }
    }

    @Unique
    private static String ausm$bufferDetails(BufferBuilder bufferBuilder) {
        if (bufferBuilder == null) {
            return "null";
        }
        VertexFormat format = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder);
        return Integer.toHexString(System.identityHashCode(bufferBuilder))
                + "{vertices=" + com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(bufferBuilder)
                + ", drawing=" + ((com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension) bufferBuilder).ausm$isDrawing()
                + ", format=" + format
                + ", pipeline=" + ExtendedVertexFormats.isPipelineBlock(format)
                + ", stride=" + (format != null ? ExtendedVertexFormats.size(format) : -1)
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
        String namespace = com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name);
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name).toLowerCase(java.util.Locale.ROOT) : "";
        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        String className = block != null ? block.getClass().getName().toLowerCase(java.util.Locale.ROOT) : "";
        return "minecraft".equals(namespace) && "fire".equals(path)
                || "architecturecraft".equals(namespace)
                || namespace.contains("architecture")
                || path.contains("architecture")
                || path.contains("fire")
                || path.contains("glass")
                || path.contains("translucent")
                || className.contains("architecture")
                || className.endsWith(".blockfire")
                || className.contains(".blockfire")
                || className.contains("glass")
                || className.contains("translucent")
                || com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterialIsFire(state);
    }

    @Unique
    private static boolean ausm$isEmissiveBloomFallbackTarget(IBlockState state) {
        return ausm$isEmissiveBloomFallbackSource(state);
    }

    @Unique
    private static boolean ausm$isEmissiveBloomFallbackSource(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null || name == null || com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name) == null) {
            return false;
        }
        if (PipelineContext.getInstance().isBlockcrafteryEditableState(state)) {
            return false;
        }
        if (PipelineContext.getInstance().stateHasShaderlessBloomSource(state)) {
            return true;
        }
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name);
        String namespace = com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name);
        return "lumenized".equals(namespace)
                || path.contains("lumenized");
    }

    @Unique
    private static BlockRenderLayer ausm$bloomFallbackLayer(IBlockState state) {
        BlockRenderLayer naturalLayer = ausm$naturalRenderLayer(state);
        if (naturalLayer != null && !AusmBloomLayer.isBloomLayer(naturalLayer)) {
            return naturalLayer;
        }
        if (state != null && (!com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((state), new String[] {"func_185913_b", "isOpaqueCube"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) || !com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((state), new String[] {"func_185917_h", "isFullCube"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false))) {
            return BlockRenderLayer.TRANSLUCENT;
        }
        return BlockRenderLayer.SOLID;
    }


    @Unique
    private static void ausm$logRenderProbe(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, Boolean result) {
        // Probe disabled.
}

    @Unique
    private static ResourceLocation ausm$registryName(IBlockState state) {
        return state != null && com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state)) : null;
    }

    @Unique
    private static Block ausm$registryBlock(ResourceLocation key) {
        if (key == null) {
            return null;
        }
        Object value = com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((ForgeRegistries.BLOCKS),
                new String[] {"func_82594_a", "getObject", "getValue"},
                new Class<?>[] {ResourceLocation.class}, key);
        return value instanceof Block ? (Block) value : null;
    }

    @Unique
    private static String ausm$stateName(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        return name != null ? name.toString() : String.valueOf(state);
    }

    @Unique
    private static int ausm$dimensionId(IBlockAccess blockAccess) {
        if (blockAccess instanceof World world && com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) != null) {
            return com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world));
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
            return state != null && com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) != null && layer != null && com.l.ausm.impl.util.MinecraftReflectionCompat.blockCanRenderInLayer(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state), state, layer);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Unique
    private static BlockRenderLayer ausm$naturalRenderLayer(IBlockState state) {
        try {
            return state != null && com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockRenderLayer(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state)) : null;
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
