package com.luna.ausm.impl.compat.nothirium;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.luna.ausm.impl.pipeline.compat.BlockcrafteryContainedStateCompat;
import com.luna.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import com.luna.ausm.impl.pipeline.compat.TerrainCompileCoordinator;
import com.luna.ausm.impl.pipeline.compat.TerrainRenderProbeState;
import com.luna.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import meldexun.nothirium.util.VisibilityGraph;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

abstract class NothiriumBloomCompileHooks extends NothiriumRenderChunkCompileHooksBase {
    public static void ausm$resetShaderlessBloomLayerSummaries(NothiriumRenderChunkCompileAccess self, RegionRenderCacheBuilder regionBuffers, CallbackInfoReturnable<?> cir) {
        NothiriumLayerCompileHooks.ausm$clearThreadCaches();
        PipelineContext.getInstance().beginFramedMaterialCompileCache();
        TerrainCompileCoordinator.beginSection();
        NothiriumBloomCompileHooks.ausm$resetShaderlessBloomMetadata(regionBuffers);
    }

    public static void ausm$recordShaderlessBloomLayerSummaries(NothiriumRenderChunkCompileAccess self, RegionRenderCacheBuilder regionBuffers, CallbackInfoReturnable<?> cir) {
        try {
            Object result = cir.getReturnValue();
            if (result != null && !"SUCCESSFUL".equals(String.valueOf(result))) {
                return;
            }
            Object renderChunk = self.ausm$renderChunk();
            if (!(renderChunk instanceof meldexun.nothirium.mc.renderer.chunk.RenderChunk chunk)) {
                return;
            }
            int x = chunk.getX();
            int y = chunk.getY();
            int z = chunk.getZ();
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                BufferBuilder buffer = regionBuffers != null
                        ? MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, layer)
                        : null;
                boolean hasBloomMetadata = buffer instanceof IBufferBuilderExtension extension
                        && extension.ausm$hasShaderlessBloomMetadata();
                PipelineContext.getInstance().recordShaderlessBloomLayerSummary(x, y, z, layer, hasBloomMetadata);
            }
        } finally {
            NothiriumBloomCompileHooks.ausm$resetShaderlessBloomMetadata(regionBuffers);
            PipelineContext.getInstance().endFramedMaterialCompileCache();
            TerrainCompileCoordinator.endSection();
            NothiriumLayerCompileHooks.ausm$clearThreadCaches();
        }
    }

    public static void ausm$probeNothiriumCompileBuffers(NothiriumRenderChunkCompileAccess self, RegionRenderCacheBuilder regionBuffers,
                                                  CallbackInfoReturnable<?> cir) {
        int call = TerrainRenderProbeState.nextTerrainCompileBufferProbe();
        if (call < 0) {
            return;
        }
        Object renderChunk = self.ausm$renderChunk();
        String chunk = "n/a";
        if (renderChunk instanceof meldexun.nothirium.mc.renderer.chunk.RenderChunk nothiriumChunk) {
            chunk = nothiriumChunk.getX() + "," + nothiriumChunk.getY() + "," + nothiriumChunk.getZ();
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumCompileBuffers] call={} thread={} chunk={} currentLayer={} pipelineActive={} forceVanilla={} formatMode={} solid={} cutoutMipped={} cutout={} translucent={} bloom={}",
                call,
                Thread.currentThread().getName(),
                chunk,
                MinecraftReflectionCompat.currentRenderLayer(),
                PipelineContext.getInstance().isPipelineActive(),
                PipelineContext.getInstance().shouldForceVanillaTerrainRenderer(),
                NothiriumPipelineCompat.shouldUsePipelineBlockFormat(),
                NothiriumLayerCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.SOLID),
                NothiriumLayerCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.CUTOUT_MIPPED),
                NothiriumLayerCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.CUTOUT),
                NothiriumLayerCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.TRANSLUCENT),
                NothiriumLayerCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, AusmBloomLayer.layer())
        );
    }

    public static void ausm$resetShaderlessBloomMetadata(RegionRenderCacheBuilder regionBuffers) {
        if (regionBuffers == null) {
            return;
        }
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            BufferBuilder buffer = MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, layer);
            if (buffer instanceof IBufferBuilderExtension extension) {
                extension.ausm$resetShaderlessBloomMetadata();
            }
        }
    }

    public static VertexFormat ausm$usePipelineBlockFormat(NothiriumRenderChunkCompileAccess self, VertexFormat original) {
        return NothiriumPipelineCompat.pipelineBlockFormat(original);
    }

    public static VertexFormat ausm$usePipelineBlockFormatForSectionBuffers(NothiriumRenderChunkCompileAccess self, VertexFormat original) {
        return NothiriumPipelineCompat.pipelineBlockFormat(original);
    }

    public static boolean ausm$forceEmissiveFallbackLayer(NothiriumRenderChunkCompileAccess self, Block block,
                                                   IBlockState state,
                                                   BlockRenderLayer layer,
                                                   IBlockState renderState,
                                                   BlockPos pos,
                                                   VisibilityGraph visibilityGraph,
                                                   RegionRenderCacheBuilder regionBuffers) {
        PipelineContext pipeline = PipelineContext.getInstance();
        // EnderIO's fused-glass block itself reports SOLID, even though its
        // smart model may provide the material through CUTOUT/TRANSLUCENT.
        // This is also needed for native EnderIO glass, not just a copied
        // Blockcraftery contained state.
        BlockRenderLayer extractedEnderIoLayer = BlockcrafteryContainedStateCompat.enderIoGlassRenderLayer(state);
        if (extractedEnderIoLayer != null && layer != null && !AusmBloomLayer.isBloomLayer(layer)) {
            int enderIoProbe = NothiriumCompileDiagnostics.ENDERIO_GLASS_LAYER_PROBES.incrementAndGet();
            if (enderIoProbe <= NothiriumCompileDiagnostics.ENDERIO_GLASS_LAYER_PROBE_LIMIT) {
                MainMod.LOGGER.info("[AUSMEnderIoGlassLayerProbe] call={} pos={} requestedLayer={} extractedLayer={} declaredLayer={} state={}",
                        enderIoProbe, pos, layer, extractedEnderIoLayer,
                        MinecraftReflectionCompat.blockRenderLayer(block),
                        NothiriumLayerCompileHooks.ausm$stateName(state));
            }
            return layer == extractedEnderIoLayer;
        }
        if (pipeline.isBlockcrafteryEditableState(state)) {
            IBlockState contained = pipeline.inheritedBlockcrafteryRenderState(state, self.ausm$chunkCache(), pos);
            if (contained != null) {
                BlockRenderLayer containedEnderIoLayer = BlockcrafteryContainedStateCompat
                        .enderIoGlassRenderLayer(contained);
                if (containedEnderIoLayer != null && layer != null && !AusmBloomLayer.isBloomLayer(layer)) {
                    return layer == containedEnderIoLayer;
                }
                // Filled frames are the contained block for every terrain
                // layer, including native BLOOM.  No GPOM shape or material
                // route remains in this decision.
                return NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(contained, layer);
            }
            return NothiriumLayerCompileHooks.ausm$canRenderInLayer(block, state, layer);
        }
        if (NothiriumLayerCompileHooks.ausm$canRenderInLayer(block, state, layer)) {
            return true;
        }
        if (BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null
                && PipelineContext.getInstance().isFramedBlockDiagnosticTarget(state)
                && layer != null
                && !AusmBloomLayer.isBloomLayer(layer)) {
            return true;
        }
        return NothiriumLayerCompileHooks.ausm$isEmissiveBloomFallbackTarget(state) && layer == NothiriumLayerCompileHooks.ausm$bloomFallbackLayer(state);
    }

    public static void ausm$captureFireCutoutStart(NothiriumRenderChunkCompileAccess self, IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                            RegionRenderCacheBuilder regionBuffers, CallbackInfo ci) {
        self.ausm$state().fireCutoutFallbackStart = -1;
        self.ausm$state().bloomOnlyBaseFallbackStart = -1;
        self.ausm$state().bloomOnlyBaseFallbackLayer = null;
        self.ausm$state().bloomOnlyBaseFallbackState = null;
        self.ausm$state().emissiveFallbackStart = -1;
        self.ausm$state().nativeBloomProbeStart = -1;
        self.ausm$state().nativeBloomProbeLayer = null;
        self.ausm$resetFramedBloomRouteProbe();
        self.ausm$resetBloomBaseRouteProbe();
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState effectiveState = pipeline.effectiveBlockRenderState(state, self.ausm$chunkCache(), pos);
        self.ausm$beginFramedBloomRouteProbe(state, pos, regionBuffers, pipeline);
        self.ausm$beginBloomBaseRouteProbe(state, effectiveState, pos, regionBuffers, pipeline);
        if (pipeline.shouldForceVanillaTerrainRenderer()) {
            return;
        }
        IBlockState inheritedBloomState = pipeline.inheritedBloomRenderState(state, self.ausm$chunkCache(), pos);
        BlockRenderLayer currentLayer = MinecraftReflectionCompat.currentRenderLayer();
        if (NothiriumBloomCompileHooks.ausm$isRandomThingsLuminousState(state) && NothiriumBloomCompileHooks.ausm$isNativeBloomOverlayLayer(currentLayer)
                && regionBuffers != null) {
            // Quantum Things emits a SOLID base and a separate translucent
            // _t overlay. Only the latter is the native bloom source that a
            // framed material must reproduce; recording the base exhausted
            // the bounded diagnostic budget before that comparison existed.
            BufferBuilder nativeBloomBuffer = MinecraftReflectionCompat.regionBufferForLayer(
                    regionBuffers, currentLayer);
            self.ausm$state().nativeBloomProbeLayer = currentLayer;
            self.ausm$state().nativeBloomProbeStart = nativeBloomBuffer != null
                    ? MinecraftReflectionCompat.bufferVertexCount(nativeBloomBuffer)
                    : -1;
        }
        IBlockState bloomOnlyState = NothiriumLayerCompileHooks.ausm$isNativeBloomOnlyBlock(effectiveState)
                ? effectiveState
                : NothiriumLayerCompileHooks.ausm$isNativeBloomOnlyBlock(state) ? state : null;
        if (bloomOnlyState != null && !pipeline.isBlockcrafteryEditableState(state) && regionBuffers != null) {
            BlockRenderLayer baseLayer = NothiriumLayerCompileHooks.ausm$bloomFallbackLayer(bloomOnlyState);
            BufferBuilder baseBuffer = MinecraftReflectionCompat.regionBufferForLayer(
                    regionBuffers, baseLayer);
            if (baseBuffer != null) {
                self.ausm$state().bloomOnlyBaseFallbackState = bloomOnlyState;
                self.ausm$state().bloomOnlyBaseFallbackLayer = baseLayer;
                self.ausm$state().bloomOnlyBaseFallbackStart = MinecraftReflectionCompat.bufferVertexCount(baseBuffer);
                self.ausm$state().bloomBaseRouteProbeBaseLayer = baseLayer;
                self.ausm$state().bloomBaseRouteProbeBaseStart = self.ausm$state().bloomOnlyBaseFallbackStart;
            }
        }
        boolean originalFire = NothiriumLayerCompileHooks.ausm$isFireFallbackTarget(state);
        boolean effectiveFire = NothiriumLayerCompileHooks.ausm$isFireFallbackTarget(effectiveState);
        BufferBuilder buffer = regionBuffers != null ? MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, BlockRenderLayer.CUTOUT) : null;
        boolean framedState = pipeline.isFramedBlockDiagnosticTarget(state);
        boolean forcedFramedBloom = false;
        IBlockState emissiveState = NothiriumLayerCompileHooks.ausm$isEmissiveBloomFallbackTarget(inheritedBloomState)
                ? inheritedBloomState
                : forcedFramedBloom ? state : framedState ? null : NothiriumLayerCompileHooks.ausm$isEmissiveBloomFallbackTarget(effectiveState) ? effectiveState : state;
        boolean emissiveTarget = forcedFramedBloom || NothiriumLayerCompileHooks.ausm$isEmissiveBloomFallbackTarget(emissiveState);
        if (emissiveTarget && regionBuffers != null) {
            IBlockState fallbackRenderState = pipeline.inheritedBloomGeometryRenderState(state, emissiveState);
            BlockRenderLayer emissiveLayer = framedState
                    ? NothiriumLayerCompileHooks.ausm$framedGeometryLayer(fallbackRenderState, emissiveState)
                    : NothiriumLayerCompileHooks.ausm$bloomFallbackLayer(emissiveState);
            BufferBuilder emissiveBuffer = MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, emissiveLayer);
            if (emissiveBuffer != null) {
                self.ausm$state().emissiveFallbackStart = MinecraftReflectionCompat.bufferVertexCount(emissiveBuffer);
            }
        }
        if (!effectiveFire || buffer == null) {
            return;
        }

        self.ausm$state().fireCutoutFallbackStart = MinecraftReflectionCompat.bufferVertexCount(buffer);
        if (self.ausm$state().bloomBaseRouteProbeTarget && self.ausm$state().bloomBaseRouteProbeBaseStart < 0) {
            self.ausm$state().bloomBaseRouteProbeBaseLayer = BlockRenderLayer.CUTOUT;
            self.ausm$state().bloomBaseRouteProbeBaseStart = self.ausm$state().fireCutoutFallbackStart;
        }
    }

    public static void ausm$setPipelineBlockContext(NothiriumRenderChunkCompileAccess self, IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                             RegionRenderCacheBuilder bufferBuilder, CallbackInfo ci) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        BlockRenderLayer naturalLayer = block != null
                ? MinecraftReflectionCompat.blockRenderLayer(block)
                : null;
        if (naturalLayer != null
                && MinecraftReflectionCompat.currentRenderLayer() == null) {
            MinecraftReflectionCompat.setCurrentRenderLayer(naturalLayer);
        }
        self.ausm$captureFramedBloomRouteLayer(bufferBuilder);
        // The render-layer thread local is initialized immediately above on
        // Nothirium's first pass.  Capturing only at renderBlockState HEAD
        // therefore missed native luminous blocks on that pass and left the
        // framed/native comparison without its source data.
        if (self.ausm$state().nativeBloomProbeStart < 0 && NothiriumBloomCompileHooks.ausm$isRandomThingsLuminousState(state)
                && bufferBuilder != null) {
            BlockRenderLayer nativeLayer = MinecraftReflectionCompat.currentRenderLayer();
            BufferBuilder nativeBuffer = NothiriumBloomCompileHooks.ausm$isNativeBloomOverlayLayer(nativeLayer)
                    ? MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, nativeLayer)
                    : null;
            if (nativeBuffer != null) {
                self.ausm$state().nativeBloomProbeLayer = nativeLayer;
                self.ausm$state().nativeBloomProbeStart =
                        MinecraftReflectionCompat.bufferVertexCount(nativeBuffer);
            }
        }
        PipelineContext pipeline = PipelineContext.getInstance();
        // Nothirium bypasses BlockRendererDispatcher's context hook. Set the
        // native BLOOM marker before the diagnostics-only fast return so every
        // ordinary luminous block reaches Entree's coat exclusion path.
        BlockRenderContext.setFramedBloomBoost(pipeline.stateHasBloomLayerGeometry(state));
        self.ausm$state().terrainCompileProbeLayer = MinecraftReflectionCompat.currentRenderLayer();
        BufferBuilder terrainProbeBuffer = self.ausm$state().terrainCompileProbeLayer != null && bufferBuilder != null
                ? MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, self.ausm$state().terrainCompileProbeLayer)
                : null;
        self.ausm$state().terrainCompileProbeStart = terrainProbeBuffer != null
                ? MinecraftReflectionCompat.bufferVertexCount(terrainProbeBuffer)
                : -1;
        self.ausm$state().framedDiagnosticStart = -1;
        self.ausm$state().framedDiagnosticLayer = null;
        if (!pipeline.currentProblemProbesEnabled()
                && !pipeline.isFramedBlockDiagnosticTarget(state)
                && !pipeline.shouldProbeBlockcrafteryTransparency(state, self.ausm$chunkCache(), pos)) {
            return;
        }
        if (pipeline.shouldForceVanillaTerrainRenderer()) {
            BlockRenderContext.configureBlock(
                    0,
                    (short) MinecraftReflectionCompat.stateRenderTypeOrdinal(state),
                    0,
                    MinecraftReflectionCompat.blockPosX(pos),
                    MinecraftReflectionCompat.blockPosY(pos),
                    MinecraftReflectionCompat.blockPosZ(pos),
                    self.ausm$chunkCache(),
                    pos,
                    pipeline.isBlockcrafteryEditableState(state)
                            && !pipeline.shouldReplaceFilledBlockcrafteryFrame(state, self.ausm$chunkCache(), pos),
                    false, 0, 0, false, -1, -1, false, false);
            return;
        }
        IBlockState actualState = pipeline.actualBlockRenderState(state, self.ausm$chunkCache(), pos);
        IBlockState contextState = pipeline.effectiveBlockRenderState(state, actualState, self.ausm$chunkCache(), pos);
        if (contextState == null) {
            contextState = state;
        }

        int blockEntityId = pipeline.blockEntityIdForActualState(actualState, self.ausm$chunkCache(), pos);
        int packedLightmap = NothiriumBloomCompileHooks.ausm$packedLightmap(contextState, self.ausm$chunkCache(), pos);
        int blockEmission = pipeline.shouldUseShaderlessBloomEmission()
                ? pipeline.blockShaderlessBloomEmission(state, self.ausm$chunkCache(), pos)
                : pipeline.blockRenderEmission(state, self.ausm$chunkCache(), pos);
        // Nothirium builds terrain without BlockRendererDispatcher's context
        // hook.  Preserve the native BLOOM marker here as well so the shader
        // can reject coated-texture treatment before material resolution.
        BlockRenderContext.configureBlock(
                blockEntityId,
                (short) MinecraftReflectionCompat.stateRenderTypeOrdinal(contextState),
                pipeline.blockMetadataForActualState(actualState),
                MinecraftReflectionCompat.blockPosX(pos),
                MinecraftReflectionCompat.blockPosY(pos),
                MinecraftReflectionCompat.blockPosZ(pos),
                self.ausm$chunkCache(),
                pos,
                pipeline.isBlockcrafteryEditableState(state)
                        && !pipeline.shouldReplaceFilledBlockcrafteryFrame(state, self.ausm$chunkCache(), pos),
                NothiriumBloomCompileHooks.ausm$isAgricraftCropState(contextState),
                packedLightmap,
                blockEmission,
                pipeline.stateHasBloomLayerGeometry(contextState),
                pipeline.blockRenderAlpha(state, self.ausm$chunkCache(), pos),
                pipeline.customLiquidTintColor(state, self.ausm$chunkCache(), pos),
                pipeline.shouldUseCrystalOnlyEmission(actualState),
                pipeline.shouldSeparateBlockAo(contextState));
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.setBlockRenderDebugContext(state, self.ausm$chunkCache(), pos);
        }
        pipeline.recordSyntheticLightCandidate(contextState, self.ausm$chunkCache(), pos);

        if (pipeline.isFramedBlockDiagnosticTarget(state) && bufferBuilder != null) {
            self.ausm$state().framedDiagnosticLayer = MinecraftReflectionCompat.currentRenderLayer();
            BufferBuilder layerBuffer = self.ausm$state().framedDiagnosticLayer != null
                    ? MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, self.ausm$state().framedDiagnosticLayer)
                    : null;
            self.ausm$state().framedDiagnosticStart = layerBuffer != null ? MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : -1;
        }
        if (pipeline.shouldProbeBlockcrafteryTransparency(state, self.ausm$chunkCache(), pos)) {
            BlockRenderLayer layer = MinecraftReflectionCompat.currentRenderLayer();
            BufferBuilder layerBuffer = layer != null && bufferBuilder != null
                    ? MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, layer)
                    : null;
            pipeline.logBlockcrafteryTransparencyProbe(
                    "nothirium-head",
                    state,
                    self.ausm$chunkCache(),
                    pos,
                    layer,
                    layerBuffer != null ? MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : null,
                    layerBuffer != null ? MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : null,
                    null,
                    "context=" + pipeline.diagnosticStateName(contextState)
                            + ", blockAlpha=" + BlockRenderContext.blockAlpha()
                            + ", layerBuffer=" + NothiriumLayerCompileHooks.ausm$bufferDetails(layerBuffer)
            );
        }
        if (pipeline.currentProblemProbesEnabled()
                && (pipeline.isCurrentProblemProbeTarget(state)
                || pipeline.isCurrentProblemProbeTarget(contextState)
                || blockEmission > 0
                || blockEntityId != 0)) {
            BufferBuilder layerBuffer = bufferBuilder != null && MinecraftReflectionCompat.currentRenderLayer() != null
                    ? MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, MinecraftReflectionCompat.currentRenderLayer())
                    : null;
            pipeline.logCurrentProblemProbe("nothirium-head", state, self.ausm$chunkCache(), pos,
                    "context=" + pipeline.diagnosticStateName(contextState)
                            + ", blockEmission=" + blockEmission
                            + ", blockAlpha=" + BlockRenderContext.blockAlpha()
                            + ", layerBuffer=" + NothiriumLayerCompileHooks.ausm$bufferDetails(layerBuffer));
        }
    }

    public static boolean ausm$isAgricraftCropState(IBlockState state) {
        if (state == null || NothiriumLayerCompileHooks.ausm$block(state) == null) {
            return false;
        }
        ResourceLocation name = NothiriumLayerCompileHooks.ausm$registryName(state);
        if (name == null) {
            return false;
        }
        if ("agricraft".equals(MinecraftReflectionCompat.resourceNamespace(name)) && "crop".equals(MinecraftReflectionCompat.resourcePath(name))) {
            return true;
        }
        return "natura".equals(MinecraftReflectionCompat.resourceNamespace(name)) && "cotton_crop".equals(MinecraftReflectionCompat.resourcePath(name));
    }

    public static int ausm$packedLightmap(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return 0;
        }
        try {
            return MinecraftReflectionCompat.statePackedLightmapCoords(state, blockAccess, pos);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static int ausm$safeCombinedLight(IBlockAccess blockAccess, BlockPos pos, int lightValue) {
        if (blockAccess == null || pos == null) {
            return -1;
        }
        try {
            return MinecraftReflectionCompat.blockAccessCombinedLight(blockAccess, pos, lightValue);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    public static int ausm$safeStateLightValue(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return 0;
        }
        try {
            return MinecraftReflectionCompat.stateLightValue(state, blockAccess, pos);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static void ausm$clearPipelineBlockContext(NothiriumRenderChunkCompileAccess self, IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                               RegionRenderCacheBuilder bufferBuilder, CallbackInfo ci) {
        PipelineContext pipeline = PipelineContext.getInstance();
        if (pipeline.isFramedBlockDiagnosticTarget(state)) {
            BlockRenderLayer layer = self.ausm$state().framedDiagnosticLayer != null
                    ? self.ausm$state().framedDiagnosticLayer
                    : MinecraftReflectionCompat.currentRenderLayer();
            BufferBuilder layerBuffer = layer != null && bufferBuilder != null
                    ? MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, layer)
                    : null;
            if (pipeline.framedBlockDiagnosticsEnabled()) {
                pipeline.logFramedBlockDiagnostic(
                        "nothirium-dispatcher",
                        state,
                        self.ausm$chunkCache(),
                        pos,
                        layer,
                        self.ausm$state().framedDiagnosticStart,
                        layerBuffer != null ? MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : -1,
                        null,
                        "buffer=" + (layerBuffer != null ? Integer.toHexString(System.identityHashCode(layerBuffer)) : "null")
                );
            }
            if (pipeline.currentProblemProbesEnabled()) {
                pipeline.logCurrentProblemProbe("nothirium-return", state, self.ausm$chunkCache(), pos,
                        "layer=" + layer
                                + ", start=" + self.ausm$state().framedDiagnosticStart
                                + ", end=" + (layerBuffer != null ? MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : -1)
                                + ", buffer=" + NothiriumLayerCompileHooks.ausm$bufferDetails(layerBuffer));
            }
            if (pipeline.shouldProbeBlockcrafteryTransparency(state, self.ausm$chunkCache(), pos)) {
                pipeline.logBlockcrafteryTransparencyProbe(
                        "nothirium-return",
                        state,
                        self.ausm$chunkCache(),
                        pos,
                        layer,
                        self.ausm$state().framedDiagnosticStart >= 0 ? self.ausm$state().framedDiagnosticStart : null,
                        layerBuffer != null ? MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : null,
                        null,
                        "buffer=" + NothiriumLayerCompileHooks.ausm$bufferDetails(layerBuffer)
                );
            }
        }
        self.ausm$logNativeBloomVertexProbe(state, pos, bufferBuilder);
        self.ausm$logTerrainCompileBlockProbe(state, pos, bufferBuilder, ci.isCancelled());
        self.ausm$state().framedDiagnosticStart = -1;
        self.ausm$state().framedDiagnosticLayer = null;
        self.ausm$state().terrainCompileProbeStart = -1;
        self.ausm$state().terrainCompileProbeLayer = null;
        BlockRenderContext.clear();
    }

    public static void ausm$logTerrainCompileBlockProbe(NothiriumRenderChunkCompileAccess self, IBlockState state, BlockPos pos, RegionRenderCacheBuilder regionBuffers,
                                                 boolean cancelled) {
        int call = TerrainRenderProbeState.nextTerrainCompileBlockProbe();
        if (call < 0) {
            return;
        }
        BlockRenderLayer layer = self.ausm$state().terrainCompileProbeLayer;
        BufferBuilder buffer = layer != null && regionBuffers != null
                ? MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, layer)
                : null;
        int end = buffer != null ? MinecraftReflectionCompat.bufferVertexCount(buffer) : -1;
        Block block = MinecraftReflectionCompat.blockFromState(state);
        MainMod.LOGGER.info(
                "[AUSMNothiriumCompileBlock] call={} thread={} pos={} layer={} state={} block={} start={} end={} delta={} cancelled={} buffer={} drawing={} format={} contextBlockId={} contextEmission={} packedLight=0x{} pipelineActive={} forceVanilla={}",
                call,
                Thread.currentThread().getName(),
                pos,
                layer,
                PipelineContext.getInstance().diagnosticStateName(state),
                block != null ? MinecraftReflectionCompat.blockRegistryName(block) : null,
                self.ausm$state().terrainCompileProbeStart,
                end,
                self.ausm$state().terrainCompileProbeStart >= 0 && end >= 0 ? end - self.ausm$state().terrainCompileProbeStart : -1,
                cancelled,
                NothiriumLayerCompileHooks.ausm$bufferDetails(buffer),
                buffer instanceof IBufferBuilderExtension extension && extension.ausm$isDrawing(),
                buffer != null ? MinecraftReflectionCompat.bufferVertexFormat(buffer) : null,
                BlockRenderContext.blockEntityId(),
                BlockRenderContext.blockEmission(),
                Integer.toHexString(BlockRenderContext.packedLightmap()),
                PipelineContext.getInstance().isPipelineActive(),
                PipelineContext.getInstance().shouldForceVanillaTerrainRenderer()
        );
    }

    public static void ausm$renderBloomOnlyFallback(NothiriumRenderChunkCompileAccess self, IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                             RegionRenderCacheBuilder regionBuffers, CallbackInfo ci) {
        PipelineContext pipeline = PipelineContext.getInstance();
        String route = "none";
        try {
            if (pipeline.shouldReplaceFilledBlockcrafteryFrame(state, self.ausm$chunkCache(), pos)) {
                route = "contained-block-native";
                return;
            }
            IBlockState effectiveState = pipeline.effectiveBlockRenderState(state, self.ausm$chunkCache(), pos);
            IBlockState inheritedBloomState = pipeline.inheritedBloomRenderState(state, self.ausm$chunkCache(), pos);
            if (self.ausm$renderMissingBloomOnlyBaseFallback(state, pos, regionBuffers)) {
                route = "bloom-only-base";
                return;
            }
            if (self.ausm$renderMissingFireCutoutFallback(state, effectiveState, pos, regionBuffers)) {
                route = "fire-cutout";
                return;
            }
            if (!pipeline.isManualBloomExtractionEnabled()) {
                route = "skip-stacked-manual-disabled";
                return;
            }
            IBlockState fallbackTarget = inheritedBloomState != null ? inheritedBloomState : effectiveState;
            if (self.ausm$renderStackedEmissiveBloomLayer(state, fallbackTarget, pos, regionBuffers)) {
                route = "stacked-emissive";
                return;
            }
        } finally {
            self.ausm$logFramedBloomRouteProbe(state, pos, regionBuffers, pipeline, route);
            self.ausm$resetFramedBloomRouteProbe();
            self.ausm$logBloomBaseRouteProbe(route, state, pos, regionBuffers, pipeline);
            self.ausm$resetBloomBaseRouteProbe();
        }
    }

    public static void ausm$beginFramedBloomRouteProbe(NothiriumRenderChunkCompileAccess self, IBlockState state, BlockPos pos,
                                                RegionRenderCacheBuilder regionBuffers, PipelineContext pipeline) {
        if (regionBuffers == null || !pipeline.isBlockcrafteryEditableState(state)) {
            return;
        }
        IBlockState contained = pipeline.inheritedBlockcrafteryRenderState(state, self.ausm$chunkCache(), pos);
        // Shadered Bloom does not use the shaderless extractor, so its source
        // predicate is intentionally false while the actual framed source is
        // active.  Ask the shared framed decision instead; it covers native
        // Bloom-layer geometry and contained emission alike.
        if (contained == null || !pipeline.hasContainedFrameBloom(state, self.ausm$chunkCache(), pos)) {
            return;
        }
        self.ausm$state().framedBloomRouteProbeTarget = true;
        self.ausm$state().framedBloomRouteProbeContainedState = contained;
        self.ausm$state().framedBloomRouteProbeBloomStart = NothiriumLayerCompileHooks.ausm$layerVertexCount(regionBuffers, AusmBloomLayer.layer());
    }

    public static void ausm$captureFramedBloomRouteLayer(NothiriumRenderChunkCompileAccess self, RegionRenderCacheBuilder regionBuffers) {
        if (!self.ausm$state().framedBloomRouteProbeTarget || self.ausm$state().framedBloomRouteProbeCurrentStart >= 0 || regionBuffers == null) {
            return;
        }
        BlockRenderLayer layer = MinecraftReflectionCompat.currentRenderLayer();
        self.ausm$state().framedBloomRouteProbeCurrentLayer = layer;
        self.ausm$state().framedBloomRouteProbeCurrentStart = NothiriumLayerCompileHooks.ausm$layerVertexCount(regionBuffers, layer);
    }

    public static void ausm$logFramedBloomRouteProbe(NothiriumRenderChunkCompileAccess self, IBlockState state, BlockPos pos, RegionRenderCacheBuilder regionBuffers,
                                              PipelineContext pipeline, String route) {
        if (!self.ausm$state().framedBloomRouteProbeTarget) {
            return;
        }
        int call = NothiriumCompileDiagnostics.FRAMED_BLOOM_ROUTE_PROBES.incrementAndGet();
        if (call > NothiriumCompileDiagnostics.FRAMED_BLOOM_ROUTE_PROBE_LIMIT) {
            return;
        }
        IBlockState contained = self.ausm$state().framedBloomRouteProbeContainedState;
        BlockRenderLayer current = self.ausm$state().framedBloomRouteProbeCurrentLayer;
        BlockRenderLayer bloom = AusmBloomLayer.layer();
        int currentEnd = NothiriumLayerCompileHooks.ausm$layerVertexCount(regionBuffers, current);
        int bloomEnd = NothiriumLayerCompileHooks.ausm$layerVertexCount(regionBuffers, bloom);
        MainMod.LOGGER.info(
                "[AUSMFramedBloomRouteProbe] call={} pos={} route={} currentLayer={} bloomLayer={} currentDelta={} bloomDelta={} containedCurrent={} containedBloom={} replace={} manual={} host={} contained={}",
                call,
                pos,
                route,
                current,
                bloom,
                NothiriumLayerCompileHooks.ausm$delta(self.ausm$state().framedBloomRouteProbeCurrentStart, currentEnd),
                NothiriumLayerCompileHooks.ausm$delta(self.ausm$state().framedBloomRouteProbeBloomStart, bloomEnd),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(contained, current),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(contained, bloom),
                pipeline.shouldReplaceFilledBlockcrafteryFrame(state, self.ausm$chunkCache(), pos),
                pipeline.isManualBloomExtractionEnabled(),
                NothiriumLayerCompileHooks.ausm$stateName(state),
                NothiriumLayerCompileHooks.ausm$stateName(contained));
        NothiriumBloomCompileHooks.ausm$logFramedBloomFinalCompileProbe(pos, regionBuffers, bloom,
                self.ausm$state().framedBloomRouteProbeBloomStart, bloomEnd);
    }

    public static void ausm$logFramedBloomFinalCompileProbe(BlockPos pos, RegionRenderCacheBuilder regionBuffers,
                                                     BlockRenderLayer bloomLayer, int start, int end) {
        if (regionBuffers == null || start < 0 || end - start < 4
                || NothiriumCompileDiagnostics.FRAMED_BLOOM_FINAL_PROBES.get() >= NothiriumCompileDiagnostics.FRAMED_BLOOM_FINAL_PROBE_LIMIT) {
            return;
        }
        BufferBuilder buffer = bloomLayer != null
                ? MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, bloomLayer) : null;
        if (!(buffer instanceof IBufferBuilderExtension extension)) {
            return;
        }
        VertexFormat format = extension.ausm$vertexFormat();
        int stride = ExtendedVertexFormats.size(format);
        ByteBuffer raw = extension.ausm$byteBuffer();
        if (!ExtendedVertexFormats.isPipelineBlock(format) || stride <= 0 || raw == null
                || (long) end * stride > raw.capacity()) {
            return;
        }
        int entityOffset = ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET;
        int markerVertices = 0;
        int firstMarker = -1;
        ByteBuffer bytes = raw.duplicate().order(raw.order() != null ? raw.order() : ByteOrder.nativeOrder());
        for (int vertex = start; vertex < end; vertex++) {
            int offset = vertex * stride + entityOffset + 6;
            if (bytes.getShort(offset) == (short) BlockRenderContext.FRAMED_BLOOM_OVERLAY_PROBE_MARKER) {
                markerVertices++;
                if (firstMarker < 0) {
                    firstMarker = vertex;
                }
            }
        }
        int call = NothiriumCompileDiagnostics.FRAMED_BLOOM_FINAL_PROBES.incrementAndGet();
        if (markerVertices == 0) {
            MainMod.LOGGER.warn("[AUSMFramedBloomFinalProbe] call={} pos={} start={} end={} markerVertices=0 expectedMarker={}",
                    call, pos, start, end, BlockRenderContext.FRAMED_BLOOM_OVERLAY_PROBE_MARKER);
            return;
        }
        MainMod.LOGGER.info("[AUSMFramedBloomFinalProbe] call={} pos={} start={} end={} markerVertices={} firstMarker={} quad={}",
                call, pos, start, end, markerVertices, firstMarker,
                NothiriumBloomCompileHooks.ausm$describePipelineQuad(bytes, firstMarker * stride, stride));
    }

    public static void ausm$resetFramedBloomRouteProbe(NothiriumRenderChunkCompileAccess self) {
        self.ausm$state().framedBloomRouteProbeTarget = false;
        self.ausm$state().framedBloomRouteProbeContainedState = null;
        self.ausm$state().framedBloomRouteProbeCurrentLayer = null;
        self.ausm$state().framedBloomRouteProbeCurrentStart = -1;
        self.ausm$state().framedBloomRouteProbeBloomStart = -1;
    }

    public static boolean ausm$isRandomThingsLuminousState(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        ResourceLocation registryName = MinecraftReflectionCompat.blockRegistryName(block);
        return registryName != null
                && "randomthings".equals(MinecraftReflectionCompat.resourceNamespace(registryName))
                && "luminousblock".equals(MinecraftReflectionCompat.resourcePath(registryName));
    }

    public static boolean ausm$isNativeBloomOverlayLayer(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.TRANSLUCENT || AusmBloomLayer.isBloomLayer(layer);
    }

    public static void ausm$logNativeBloomVertexProbe(NothiriumRenderChunkCompileAccess self,
                                               IBlockState state,
                                               BlockPos pos,
                                               RegionRenderCacheBuilder regionBuffers
    ) {
        if (self.ausm$state().nativeBloomProbeStart < 0 || !NothiriumBloomCompileHooks.ausm$isRandomThingsLuminousState(state)
                || !NothiriumBloomCompileHooks.ausm$isNativeBloomOverlayLayer(self.ausm$state().nativeBloomProbeLayer)
                || NothiriumCompileDiagnostics.NATIVE_BLOOM_VERTEX_PROBES.get() >= NothiriumCompileDiagnostics.BLOOM_VERTEX_PROBE_LIMIT
                || regionBuffers == null) {
            return;
        }
        BlockRenderLayer layer = self.ausm$state().nativeBloomProbeLayer;
        BufferBuilder buffer = layer != null
                ? MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, layer)
                : null;
        if (!(buffer instanceof IBufferBuilderExtension extension)) {
            return;
        }
        int end = extension.ausm$vertexCount();
        VertexFormat format = extension.ausm$vertexFormat();
        int stride = ExtendedVertexFormats.size(format);
        ByteBuffer raw = extension.ausm$byteBuffer();
        if (end - self.ausm$state().nativeBloomProbeStart < 4
                || !ExtendedVertexFormats.isPipelineBlock(format)
                || stride <= 0
                || raw == null) {
            return;
        }
        int call = NothiriumCompileDiagnostics.NATIVE_BLOOM_VERTEX_PROBES.incrementAndGet();
        if (call <= NothiriumCompileDiagnostics.BLOOM_VERTEX_PROBE_LIMIT) {
            MainMod.LOGGER.info(
                    "[AUSMBloomVertexProbe] kind=native call={} pos={} layer={} bloomLayer={} start={} end={} quad={}",
                    call,
                    pos,
                    layer,
                    AusmBloomLayer.layer(),
                    self.ausm$state().nativeBloomProbeStart,
                    end,
                    NothiriumBloomCompileHooks.ausm$describePipelineQuad(raw, self.ausm$state().nativeBloomProbeStart * stride, stride));
        }
    }

    public static String ausm$describePipelineQuad(ByteBuffer raw, int byteStart, int stride) {
        ByteBuffer bytes = raw.duplicate().order(raw.order() != null ? raw.order() : ByteOrder.nativeOrder());
        StringBuilder result = new StringBuilder();
        for (int vertex = 0; vertex < 4; vertex++) {
            int base = byteStart + vertex * stride;
            if (vertex > 0) {
                result.append(';');
            }
            result.append(NothiriumBloomCompileHooks.ausm$describePipelineVertex(
                    bytes.getFloat(base),
                    bytes.getFloat(base + 4),
                    bytes.getFloat(base + 8),
                    bytes.getInt(base + 12),
                    bytes.getFloat(base + 16),
                    bytes.getFloat(base + 20),
                    bytes.getInt(base + 24),
                    bytes.getInt(base + ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET),
                    bytes.getLong(base + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET),
                    bytes.getFloat(base + ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET),
                    bytes.getFloat(base + ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET + 4),
                    bytes.getInt(base + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET)));
        }
        return result.toString();
    }

    public static String ausm$describePipelineVertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int light,
            int normal,
            long entity,
            float midU,
            float midV,
            int midBlock
    ) {
        return "p=" + x + "/" + y + "/" + z
                + ",c=" + Integer.toHexString(color)
                + ",uv=" + u + "/" + v
                + ",light=" + Integer.toHexString(light)
                + ",normal=" + Integer.toHexString(normal)
                + ",entity=" + (short) (entity & 0xFFFF) + "/"
                + (short) ((entity >>> 16) & 0xFFFF) + "/"
                + (short) ((entity >>> 32) & 0xFFFF) + "/"
                + (short) ((entity >>> 48) & 0xFFFF)
                + ",midUv=" + midU + "/" + midV
                + ",midBlock=" + Integer.toHexString(midBlock);
    }
}
