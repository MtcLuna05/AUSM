package com.luna.ausm.impl.compat.nothirium;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.luna.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import com.luna.ausm.impl.pipeline.compat.TerrainCompileCoordinator;
import com.luna.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

abstract class NothiriumLayerCompileHooks extends NothiriumBloomCompileHooks {
    public static void ausm$beginBloomBaseRouteProbe(NothiriumRenderChunkCompileAccess self,
                                              IBlockState state,
                                              IBlockState effectiveState,
                                              BlockPos pos,
                                              RegionRenderCacheBuilder regionBuffers,
                                              PipelineContext pipeline
    ) {
        if (NothiriumCompileDiagnostics.BLOOM_BASE_ROUTE_PROBES.get() >= NothiriumCompileDiagnostics.BLOOM_BASE_ROUTE_PROBE_LIMIT) {
            return;
        }
        boolean fire = NothiriumLayerCompileHooks.ausm$isFireFallbackTarget(state) || NothiriumLayerCompileHooks.ausm$isFireFallbackTarget(effectiveState);
        boolean twilightPortal = pipeline.isCeleritasTwilightPortalState(state)
                || pipeline.isCeleritasTwilightPortalState(effectiveState);
        if (!fire && !twilightPortal) {
            return;
        }

        self.ausm$state().bloomBaseRouteProbeTarget = true;
        self.ausm$state().bloomBaseRouteProbeKind = fire && twilightPortal
                ? "fire+twilight-portal" : fire ? "fire" : "twilight-portal";
        self.ausm$state().bloomBaseRouteProbeEffectiveState = effectiveState;
        self.ausm$state().bloomBaseRouteProbeCurrentLayer =
                MinecraftReflectionCompat.currentRenderLayer();
        self.ausm$state().bloomBaseRouteProbeCurrentStart = NothiriumLayerCompileHooks.ausm$layerVertexCount(
                regionBuffers, self.ausm$state().bloomBaseRouteProbeCurrentLayer);
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        self.ausm$state().bloomBaseRouteProbeBloomStart = NothiriumLayerCompileHooks.ausm$layerVertexCount(regionBuffers, bloomLayer);

        IBlockState candidate = effectiveState != null ? effectiveState : state;
        self.ausm$state().bloomBaseRouteProbeBaseLayer = fire
                ? BlockRenderLayer.CUTOUT : NothiriumLayerCompileHooks.ausm$bloomFallbackLayer(candidate);
        self.ausm$state().bloomBaseRouteProbeBaseStart = NothiriumLayerCompileHooks.ausm$layerVertexCount(
                regionBuffers, self.ausm$state().bloomBaseRouteProbeBaseLayer);
    }

    public static void ausm$logBloomBaseRouteProbe(NothiriumRenderChunkCompileAccess self,
                                            String route,
                                            IBlockState state,
                                            BlockPos pos,
                                            RegionRenderCacheBuilder regionBuffers,
                                            PipelineContext pipeline
    ) {
        if (!self.ausm$state().bloomBaseRouteProbeTarget) {
            return;
        }
        int probe = NothiriumCompileDiagnostics.BLOOM_BASE_ROUTE_PROBES.incrementAndGet();
        if (probe > NothiriumCompileDiagnostics.BLOOM_BASE_ROUTE_PROBE_LIMIT) {
            return;
        }

        IBlockState effectiveState = self.ausm$state().bloomBaseRouteProbeEffectiveState;
        BlockRenderLayer currentLayer = self.ausm$state().bloomBaseRouteProbeCurrentLayer;
        BlockRenderLayer baseLayer = self.ausm$state().bloomBaseRouteProbeBaseLayer;
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        int currentEnd = NothiriumLayerCompileHooks.ausm$layerVertexCount(regionBuffers, currentLayer);
        int baseEnd = NothiriumLayerCompileHooks.ausm$layerVertexCount(regionBuffers, baseLayer);
        int bloomEnd = NothiriumLayerCompileHooks.ausm$layerVertexCount(regionBuffers, bloomLayer);
        MainMod.LOGGER.info(
                "[AUSMBloomBaseRouteProbe] call={} thread={} kind={} route={} pos={} currentLayer={} baseLayer={} bloomLayer={} currentStart={} currentEnd={} currentDelta={} baseStart={} baseEnd={} baseDelta={} bloomStart={} bloomEnd={} bloomDelta={} textureFallback={} pipelineActive={} forceVanilla={} original={} effective={} originalRenderType={} effectiveRenderType={} originalNatural={} effectiveNatural={} originalNativeBloomOnly={} effectiveNativeBloomOnly={} originalLayers={}/{}/{}/{}/{} effectiveLayers={}/{}/{}/{}/{} solidBuffer={} cutoutMippedBuffer={} cutoutBuffer={} translucentBuffer={} bloomBuffer={}",
                probe,
                Thread.currentThread().getName(),
                self.ausm$state().bloomBaseRouteProbeKind,
                route,
                pos,
                currentLayer,
                baseLayer,
                bloomLayer,
                self.ausm$state().bloomBaseRouteProbeCurrentStart,
                currentEnd,
                NothiriumLayerCompileHooks.ausm$delta(self.ausm$state().bloomBaseRouteProbeCurrentStart, currentEnd),
                self.ausm$state().bloomBaseRouteProbeBaseStart,
                baseEnd,
                NothiriumLayerCompileHooks.ausm$delta(self.ausm$state().bloomBaseRouteProbeBaseStart, baseEnd),
                self.ausm$state().bloomBaseRouteProbeBloomStart,
                bloomEnd,
                NothiriumLayerCompileHooks.ausm$delta(self.ausm$state().bloomBaseRouteProbeBloomStart, bloomEnd),
                pipeline.shouldRenderTextureBloomFallback(),
                pipeline.isPipelineActive(),
                pipeline.shouldForceVanillaTerrainRenderer(),
                NothiriumLayerCompileHooks.ausm$stateName(state),
                NothiriumLayerCompileHooks.ausm$stateName(effectiveState),
                MinecraftReflectionCompat.stateRenderType(state),
                MinecraftReflectionCompat.stateRenderType(effectiveState),
                NothiriumLayerCompileHooks.ausm$naturalRenderLayer(state),
                NothiriumLayerCompileHooks.ausm$naturalRenderLayer(effectiveState),
                NothiriumLayerCompileHooks.ausm$isNativeBloomOnlyBlock(state),
                NothiriumLayerCompileHooks.ausm$isNativeBloomOnlyBlock(effectiveState),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(state, BlockRenderLayer.SOLID),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(state, BlockRenderLayer.CUTOUT_MIPPED),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(state, BlockRenderLayer.CUTOUT),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(state, BlockRenderLayer.TRANSLUCENT),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(state, bloomLayer),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.SOLID),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.CUTOUT_MIPPED),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.CUTOUT),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.TRANSLUCENT),
                NothiriumLayerCompileHooks.ausm$canRenderStateInLayer(effectiveState, bloomLayer),
                NothiriumLayerCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.SOLID),
                NothiriumLayerCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.CUTOUT_MIPPED),
                NothiriumLayerCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.CUTOUT),
                NothiriumLayerCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.TRANSLUCENT),
                NothiriumLayerCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, bloomLayer)
        );
    }

    public static int ausm$layerVertexCount(RegionRenderCacheBuilder regionBuffers, BlockRenderLayer layer) {
        if (regionBuffers == null || layer == null) {
            return -1;
        }
        BufferBuilder buffer = MinecraftReflectionCompat.regionBufferForLayer(
                regionBuffers, layer);
        return buffer != null
                ? MinecraftReflectionCompat.bufferVertexCount(buffer) : -1;
    }

    public static int ausm$delta(int start, int end) {
        return start >= 0 && end >= 0 ? end - start : -1;
    }

    public static void ausm$resetBloomBaseRouteProbe(NothiriumRenderChunkCompileAccess self) {
        self.ausm$state().bloomBaseRouteProbeTarget = false;
        self.ausm$state().bloomBaseRouteProbeKind = "";
        self.ausm$state().bloomBaseRouteProbeEffectiveState = null;
        self.ausm$state().bloomBaseRouteProbeCurrentLayer = null;
        self.ausm$state().bloomBaseRouteProbeBaseLayer = null;
        self.ausm$state().bloomBaseRouteProbeCurrentStart = -1;
        self.ausm$state().bloomBaseRouteProbeBaseStart = -1;
        self.ausm$state().bloomBaseRouteProbeBloomStart = -1;
    }

    public static boolean ausm$renderMissingBloomOnlyBaseFallback(NothiriumRenderChunkCompileAccess self,
                                                           IBlockState originalState,
                                                           BlockPos pos,
                                                           RegionRenderCacheBuilder regionBuffers
    ) {
        IBlockState fallbackState = self.ausm$state().bloomOnlyBaseFallbackState;
        BlockRenderLayer baseLayer = self.ausm$state().bloomOnlyBaseFallbackLayer;
        int baseStart = self.ausm$state().bloomOnlyBaseFallbackStart;
        try {
            if (fallbackState == null || baseLayer == null || baseStart < 0
                    || pos == null || regionBuffers == null) {
                return false;
            }

            BufferBuilder buffer = MinecraftReflectionCompat.regionBufferForLayer(
                    regionBuffers, baseLayer);
            if (buffer == null) {
                return false;
            }
            int normalDelta = MinecraftReflectionCompat.bufferVertexCount(buffer) - baseStart;
            if (normalDelta > 0) {
                NothiriumLayerCompileHooks.ausm$logBloomOnlyBaseFallback("base-present", originalState, fallbackState, pos,
                        baseLayer, normalDelta, false, 0);
                return true;
            }

            if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
                MinecraftReflectionCompat.bufferBegin(buffer, 7,
                        NothiriumPipelineCompat.pipelineBlockFormat(
                                MinecraftReflectionCompat.blockFormat()));
                int originX = Math.floorDiv(MinecraftReflectionCompat.blockPosX(pos), 16) * 16;
                int originY = Math.floorDiv(MinecraftReflectionCompat.blockPosY(pos), 16) * 16;
                int originZ = Math.floorDiv(MinecraftReflectionCompat.blockPosZ(pos), 16) * 16;
                MinecraftReflectionCompat.bufferSetTranslation(
                        buffer, -originX, -originY, -originZ);
            }

            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            if (bloomLayer == null) {
                return false;
            }
            BlockRenderLayer previousLayer = MinecraftReflectionCompat.currentRenderLayer();
            int fallbackStart = MinecraftReflectionCompat.bufferVertexCount(buffer);
            boolean rendered;
            try {
                // CTM's layer=BLOOM removes these quads from the default pass.
                // Keep the bloom mesh and copy the same model into its normal
                // terrain buffer so bloom overlays scene geometry.
                MinecraftReflectionCompat.setCurrentRenderLayer(bloomLayer);
                BlockRendererDispatcher dispatcher = MinecraftReflectionCompat
                        .blockRendererDispatcher(MinecraftReflectionCompat.minecraft());
                rendered = dispatcher != null && MinecraftReflectionCompat.renderBlock(
                        dispatcher, fallbackState, pos, self.ausm$chunkCache(), buffer);
            } finally {
                MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
            }
            int fallbackDelta = MinecraftReflectionCompat.bufferVertexCount(buffer) - fallbackStart;
            NothiriumLayerCompileHooks.ausm$logBloomOnlyBaseFallback("stacked", originalState, fallbackState, pos,
                    baseLayer, normalDelta, rendered, fallbackDelta);
            return fallbackDelta > 0;
        } finally {
            self.ausm$state().bloomOnlyBaseFallbackStart = -1;
            self.ausm$state().bloomOnlyBaseFallbackLayer = null;
            self.ausm$state().bloomOnlyBaseFallbackState = null;
        }
    }

    public static void ausm$logBloomOnlyBaseFallback(
            String mode,
            IBlockState originalState,
            IBlockState fallbackState,
            BlockPos pos,
            BlockRenderLayer baseLayer,
            int normalDelta,
            boolean rendered,
            int fallbackDelta
    ) {
        int index = NothiriumCompileDiagnostics.BLOOM_ONLY_BASE_FALLBACK_LOGS.incrementAndGet();
        if (index > NothiriumCompileDiagnostics.BLOOM_ONLY_BASE_FALLBACK_LOG_LIMIT) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMBloomOnlyBaseFallback] mode={} pos={} original={} fallback={} baseLayer={} normalDelta={} rendered={} fallbackDelta={}",
                mode,
                pos,
                NothiriumLayerCompileHooks.ausm$stateName(originalState),
                NothiriumLayerCompileHooks.ausm$stateName(fallbackState),
                baseLayer,
                normalDelta,
                rendered,
                fallbackDelta
        );
    }



    public static BlockRenderLayer ausm$framedGeometryLayer(IBlockState framedState, IBlockState inheritedState) {
        BlockRenderLayer inheritedLayer = NothiriumLayerCompileHooks.ausm$bloomFallbackLayer(inheritedState);
        if (inheritedLayer != null && !AusmBloomLayer.isBloomLayer(inheritedLayer)) {
            return inheritedLayer;
        }
        BlockRenderLayer framedLayer = NothiriumLayerCompileHooks.ausm$naturalRenderLayer(framedState);
        if (framedLayer != null && !AusmBloomLayer.isBloomLayer(framedLayer)) {
            return framedLayer;
        }
        return BlockRenderLayer.SOLID;
    }

    public static boolean ausm$renderEmissiveFallbackWithLayer(NothiriumRenderChunkCompileAccess self, IBlockState state, IBlockState maskColorState, BlockPos pos, BufferBuilder buffer,
                                                        BlockRenderLayer layer, boolean bloomMaskFallback) {
        BlockRenderLayer previousLayer = MinecraftReflectionCompat.currentRenderLayer();
        try {
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.set(Boolean.TRUE);
            if (bloomMaskFallback) {
                BlockRenderContext.setBloomMaskFallback(true);
            }
            MinecraftReflectionCompat.setCurrentRenderLayer(layer);
            BlockRendererDispatcher dispatcher = MinecraftReflectionCompat.blockRendererDispatcher(MinecraftReflectionCompat.minecraft());
            return dispatcher != null && MinecraftReflectionCompat.renderBlock(dispatcher, state, pos, self.ausm$chunkCache(), buffer);
        } finally {
            MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
            BlockRenderContext.clearBloomMaskFallback();
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.remove();
        }
    }

    public static void ausm$logEmissiveFallback(NothiriumRenderChunkCompileAccess self, String mode, IBlockState originalState, IBlockState renderState,
                                         BlockPos pos, IBlockState sourceState,
                                         BlockRenderLayer fallbackLayer, BlockRenderLayer renderLayer,
                                         int normalDelta, boolean rendered, int fallbackDelta,
                                         BufferBuilder buffer, RegionRenderCacheBuilder regionBuffers) {
        if (NothiriumCompileDiagnostics.EMISSIVE_FALLBACK_LOG_LIMIT <= 0) {
            return;
        }
        if (PipelineContext.getInstance().isBlockcrafteryEditableState(originalState)
                && !NothiriumLayerCompileHooks.ausm$isEmissiveBloomFallbackTarget(sourceState)) {
            return;
        }
        int index = NothiriumCompileDiagnostics.EMISSIVE_FALLBACK_LOGS.incrementAndGet();
        if (index > NothiriumCompileDiagnostics.EMISSIVE_FALLBACK_LOG_LIMIT) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMEmissiveFallback] mode={} pos={} original={} source={} render={} fallbackLayer={} renderLayer={} normalDelta={} rendered={} fallbackDelta={} buffer={} regionBuffers={} cache={} framed={} bloomFallbackRender={} caller={}",
                mode,
                pos,
                NothiriumLayerCompileHooks.ausm$stateName(originalState),
                NothiriumLayerCompileHooks.ausm$stateName(sourceState),
                NothiriumLayerCompileHooks.ausm$stateName(renderState),
                fallbackLayer,
                renderLayer,
                normalDelta,
                rendered,
                fallbackDelta,
                NothiriumLayerCompileHooks.ausm$bufferDetails(buffer),
                regionBuffers != null ? regionBuffers.getClass().getName() : "null",
                self.ausm$chunkCache() != null ? self.ausm$chunkCache().getClass().getName() : "null",
                PipelineContext.getInstance().isFramedBlockDiagnosticTarget(originalState),
                BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get(),
                NothiriumLayerCompileHooks.ausm$externalCaller()
        );
    }

    public static String ausm$bufferDetails(BufferBuilder buffer) {
        if (buffer == null) {
            return "null";
        }
        VertexFormat format = MinecraftReflectionCompat.bufferVertexFormat(buffer);
        return Integer.toHexString(System.identityHashCode(buffer))
                + "{vertices=" + MinecraftReflectionCompat.bufferVertexCount(buffer)
                + ", drawing=" + ((IBufferBuilderExtension) buffer).ausm$isDrawing()
                + ", format=" + format
                + ", pipeline=" + ExtendedVertexFormats.isPipelineBlock(format)
                + ", stride=" + (format != null ? ExtendedVertexFormats.size(format) : -1)
                + "}";
    }

    public static String ausm$layerCompileBufferDetails(RegionRenderCacheBuilder regionBuffers, BlockRenderLayer layer) {
        BufferBuilder buffer = regionBuffers != null
                ? MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, layer)
                : null;
        return NothiriumLayerCompileHooks.ausm$bufferDetails(buffer);
    }

    public static boolean ausm$renderMissingFireCutoutFallback(NothiriumRenderChunkCompileAccess self,
                                                        IBlockState renderState,
                                                        IBlockState fallbackTarget,
                                                        BlockPos pos,
                                                        RegionRenderCacheBuilder regionBuffers
    ) {
        boolean fireTarget = NothiriumLayerCompileHooks.ausm$isFireFallbackTarget(fallbackTarget) || NothiriumLayerCompileHooks.ausm$isFireFallbackTarget(renderState);
        try {
            if (!fireTarget || pos == null || regionBuffers == null) {
                return false;
            }

            BufferBuilder buffer = MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, BlockRenderLayer.CUTOUT);
            if (buffer == null) {
                return true;
            }

            int start = self.ausm$state().fireCutoutFallbackStart;
            int normalDelta = start >= 0 ? MinecraftReflectionCompat.bufferVertexCount(buffer) - start : -1;
            IBlockState fallbackRenderState = NothiriumLayerCompileHooks.ausm$isFireFallbackTarget(fallbackTarget) ? fallbackTarget : renderState;
            if (normalDelta > 0) {
                NothiriumLayerCompileHooks.ausm$logFireFallback("normal-present", renderState, fallbackRenderState, pos, normalDelta, false, 0);
                return true;
            }

            if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
                MinecraftReflectionCompat.bufferBegin(buffer, 7, NothiriumPipelineCompat.pipelineBlockFormat(MinecraftReflectionCompat.blockFormat()));
                int originX = Math.floorDiv(MinecraftReflectionCompat.blockPosX(pos), 16) * 16;
                int originY = Math.floorDiv(MinecraftReflectionCompat.blockPosY(pos), 16) * 16;
                int originZ = Math.floorDiv(MinecraftReflectionCompat.blockPosZ(pos), 16) * 16;
                MinecraftReflectionCompat.bufferSetTranslation(buffer, -originX, -originY, -originZ);
            }

            int cutoutStart = MinecraftReflectionCompat.bufferVertexCount(buffer);
            boolean cutoutRendered = self.ausm$renderFireFallbackWithLayer(fallbackRenderState, pos, buffer, BlockRenderLayer.CUTOUT);
            int cutoutDelta = MinecraftReflectionCompat.bufferVertexCount(buffer) - cutoutStart;
            NothiriumLayerCompileHooks.ausm$logFireFallback("fallback-cutout", renderState, fallbackRenderState, pos, normalDelta, cutoutRendered,
                    cutoutDelta);
            if (cutoutDelta > 0) {
                return true;
            }

            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            if (bloomLayer == null) {
                NothiriumLayerCompileHooks.ausm$logFireFallback("fallback-bloom-unavailable", renderState, fallbackRenderState, pos,
                        normalDelta, false, 0);
                return true;
            }

            int bloomStart = MinecraftReflectionCompat.bufferVertexCount(buffer);
            boolean bloomRendered = self.ausm$renderFireFallbackWithLayer(fallbackRenderState, pos, buffer, bloomLayer);
            NothiriumLayerCompileHooks.ausm$logFireFallback("fallback-bloom-layer", renderState, fallbackRenderState, pos, normalDelta,
                    bloomRendered, MinecraftReflectionCompat.bufferVertexCount(buffer) - bloomStart);
            return true;
        } finally {
            self.ausm$state().fireCutoutFallbackStart = -1;
        }
    }

    public static boolean ausm$renderFireFallbackWithLayer(NothiriumRenderChunkCompileAccess self, IBlockState state, BlockPos pos, BufferBuilder buffer,
                                                    BlockRenderLayer layer) {
        BlockRenderLayer previousLayer = MinecraftReflectionCompat.currentRenderLayer();
        try {
            MinecraftReflectionCompat.setCurrentRenderLayer(layer);
            BlockRendererDispatcher dispatcher = MinecraftReflectionCompat.blockRendererDispatcher(MinecraftReflectionCompat.minecraft());
            return dispatcher != null && MinecraftReflectionCompat.renderBlock(dispatcher, state, pos, self.ausm$chunkCache(), buffer);
        } finally {
            MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
        }
    }

    public static void ausm$logFireFallback(String mode, IBlockState originalState, IBlockState renderState,
                                     BlockPos pos, int normalDelta, boolean rendered, int fallbackDelta) {
        if (NothiriumCompileDiagnostics.FIRE_FALLBACK_LOG_LIMIT <= 0) {
            return;
        }
        int index = NothiriumCompileDiagnostics.FIRE_FALLBACK_LOGS.incrementAndGet();
        if (index > NothiriumCompileDiagnostics.FIRE_FALLBACK_LOG_LIMIT) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMFire] Nothirium fire compile mode={} pos={} original={} render={} normalDelta={} rendered={} fallbackDelta={}",
                mode,
                pos,
                NothiriumLayerCompileHooks.ausm$stateName(originalState),
                NothiriumLayerCompileHooks.ausm$stateName(renderState),
                normalDelta,
                rendered,
                fallbackDelta
        );
    }

    public static boolean ausm$isNativeBloomOnlyBlock(IBlockState state) {
        Block block = NothiriumLayerCompileHooks.ausm$block(state);
        if (state == null || block == null || MinecraftReflectionCompat.stateRenderType(state) == EnumBlockRenderType.INVISIBLE) {
            return false;
        }

        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (bloomLayer == null
                || !MinecraftReflectionCompat.blockCanRenderInLayer(
                block, state, bloomLayer)) {
            return false;
        }

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (layer == null || AusmBloomLayer.isBloomLayer(layer)) {
                continue;
            }
            if (MinecraftReflectionCompat.blockCanRenderInLayer(
                    block, state, layer)) {
                return false;
            }
        }
        return true;
    }

    public static BlockRenderLayer ausm$bloomFallbackLayer(IBlockState state) {
        if (state == null) {
            return BlockRenderLayer.SOLID;
        }
        ResourceLocation name = NothiriumLayerCompileHooks.ausm$registryName(state);
        String path = MinecraftReflectionCompat.resourcePathLower(name);
        if (path.contains("fire") || MinecraftReflectionCompat.stateMaterialIsFire(state)) {
            return BlockRenderLayer.CUTOUT;
        }
        BlockRenderLayer naturalLayer = NothiriumLayerCompileHooks.ausm$naturalRenderLayer(state);
        if (naturalLayer != null && !AusmBloomLayer.isBloomLayer(naturalLayer)) {
            return naturalLayer;
        }
        if (path.contains("translucent") || !MinecraftReflectionCompat.callBoolean(state, new String[]{"func_185913_b", "isOpaqueCube"}, MinecraftReflectionCompat.NO_PARAMETERS, false) || !MinecraftReflectionCompat.callBoolean(state, new String[]{"func_185917_h", "isFullCube"}, MinecraftReflectionCompat.NO_PARAMETERS, false)) {
            return BlockRenderLayer.TRANSLUCENT;
        }
        return BlockRenderLayer.SOLID;
    }

    public static BlockRenderLayer ausm$naturalRenderLayer(IBlockState state) {
        try {
            Block block = NothiriumLayerCompileHooks.ausm$block(state);
            return block != null ? MinecraftReflectionCompat.blockRenderLayer(block) : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean ausm$canRenderInLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        try {
            return TerrainCompileCoordinator.canRenderInLayer(block, state, layer, PipelineContext.getInstance());
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean ausm$canRenderStateInLayer(IBlockState state, BlockRenderLayer layer) {
        return state != null && NothiriumLayerCompileHooks.ausm$canRenderInLayer(NothiriumLayerCompileHooks.ausm$block(state), state, layer);
    }

    public static boolean ausm$isEmissiveBloomFallbackTarget(IBlockState state) {
        return NothiriumLayerCompileHooks.ausm$isEmissiveBloomFallbackSource(state);
    }

    public static boolean ausm$isEmissiveBloomFallbackSource(IBlockState state) {
        Block block = NothiriumLayerCompileHooks.ausm$block(state);
        if (block == null || MinecraftReflectionCompat.stateRenderType(state) == EnumBlockRenderType.INVISIBLE) {
            return false;
        }
        if (PipelineContext.getInstance().isBlockcrafteryEditableState(state)) {
            return false;
        }
        return PipelineContext.getInstance().stateUsesTextureBloomSource(state);
    }

    public static boolean ausm$isFireFallbackTarget(IBlockState state) {
        ResourceLocation name = NothiriumLayerCompileHooks.ausm$registryName(state);
        if (name != null && "minecraft".equals(MinecraftReflectionCompat.resourceNamespace(name)) && "fire".equals(MinecraftReflectionCompat.resourcePath(name))) {
            return true;
        }
        return state != null && MinecraftReflectionCompat.stateMaterialIsFire(state);
    }

    public static ResourceLocation ausm$registryName(IBlockState state) {
        if (state == null) {
            return null;
        }
        Block block = NothiriumLayerCompileHooks.ausm$block(state);
        return block != null ? MinecraftReflectionCompat.blockRegistryName(block) : null;
    }

    public static Block ausm$block(IBlockState state) {
        if (state == null) {
            return null;
        }
        return MinecraftReflectionCompat.blockFromState(state);
    }

    public static void ausm$clearThreadCaches() {
        MinecraftReflectionCompat.clearHotThreadCaches();
    }

    public static String ausm$stateName(IBlockState state) {
        ResourceLocation name = NothiriumLayerCompileHooks.ausm$registryName(state);
        return name != null ? MinecraftReflectionCompat.resourceString(name) : String.valueOf(state);
    }

    public static String ausm$externalCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals("com.luna.ausm.impl.mixin.compat.NothiriumRenderChunkTaskCompileMixin")) {
                continue;
            }
            return className + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "unknown";
    }
}
