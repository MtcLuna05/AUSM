package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.GpomFramedQuadMetadata;
import com.luna.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_BLOCKCRAFTERY_ROUTE_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_SOFT_VANILLA_SPECIAL_BLOCK_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.SHADERLESS_BLOOM_GEOMETRY_EMISSION;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.SHADERLESS_LIGHT_EMITTING_BLOOM_GEOMETRY_EMISSION;

abstract class PipelineRuntimeCompatibilityState extends PipelineRuntimeTerrainFormatState {
    public void logBlockcrafteryRouteProbe(
            String stage,
            IBlockState state,
            IBlockAccess blockAccess,
            BlockPos pos,
            BufferBuilder buffer,
            int startVertex,
            int endVertex,
            Boolean result
    ) {
        if (blockcrafteryRouteProbeCount.get() >= MAX_BLOCKCRAFTERY_ROUTE_PROBE_LOGS) {
            return;
        }
        int probe = blockcrafteryRouteProbeCount.incrementAndGet();
        if (probe > MAX_BLOCKCRAFTERY_ROUTE_PROBE_LOGS) {
            return;
        }
        IBlockState actualState = self().actualBlockRenderState(state, blockAccess, pos);
        IBlockState effectiveState = self().effectiveBlockRenderState(state, actualState, blockAccess, pos);
        MainMod.LOGGER.info(
                "[AUSMBlockcrafteryRouteProbe] call={} stage={} thread={} pos={} layer={} result={} start={} end={} delta={} host={} actual={} effective={} containedPresent={} contained={} containedEmission={} containedBloom={} contextPresent={} contextPos={} sameContextAccess={} blockId={} renderType={} metadata={} emission={} bloomBoost={} buffer={} format={} access={} pipelineActive={}",
                probe,
                stage,
                Thread.currentThread().getName(),
                pos,
                MinecraftReflectionCompat.currentRenderLayer(),
                result,
                startVertex,
                endVertex,
                startVertex >= 0 && endVertex >= 0 ? endVertex - startVertex : -1,
                self().diagnosticStateName(state),
                self().diagnosticStateName(actualState),
                self().diagnosticStateName(effectiveState),
                effectiveState != null,
                self().diagnosticStateName(effectiveState),
                effectiveState != null ? self().blockRenderEmissionForState(effectiveState, blockAccess, pos) : 0,
                effectiveState != null && self().stateHasBloomLayerGeometry(effectiveState),
                BlockRenderContext.hasWorldBlockContext(),
                BlockRenderContext.blockPos(),
                BlockRenderContext.blockAccess() == blockAccess,
                BlockRenderContext.blockEntityId(),
                BlockRenderContext.renderType(),
                BlockRenderContext.metadata(),
                BlockRenderContext.blockEmission(),
                BlockRenderContext.framedBloomBoost(),
                buffer,
                buffer != null
                        ? MinecraftReflectionCompat.bufferVertexFormat(buffer) : null,
                blockAccess != null ? blockAccess.getClass().getName() : "null",
                isPipelineActive
        );
    }

    public boolean applyFramedQuadMaterial(BakedQuad quad, String spriteName) {
        if (!BlockRenderContext.isFramedMaterialOwner()) {
            return false;
        }
        BlockRenderContext.clearQuadOverrides();
        BlockRenderContext.setQuadFramedBloomBoost(false);

        GpomFramedQuadMetadata.Metadata metadata =
                GpomFramedQuadMetadata.get(quad);
        if (metadata == null) {
            return false;
        }

        IBlockState material = metadata.materialState();
        IBlockAccess blockAccess = BlockRenderContext.blockAccess();
        BlockPos pos = BlockRenderContext.blockPos();
        // GPOM's provenance is presentation metadata. In particular it
        // assigns RandomThings' shouldGlow materials an emission of 15 even
        // when the pack disables LuminousBlocksEmitLight. The direct material
        // path uses its real state light value, so do the same for framed
        // double-slope quads instead of promoting that synthetic value.
        int emission = self().blockRenderEmissionForState(material, blockAccess, pos);
        BlockRenderContext.setQuadBlockMetadata(
                self().blockEntityIdForActualState(material, blockAccess, pos),
                (short) MinecraftReflectionCompat.stateRenderTypeOrdinal(material),
                self().blockMetadataForActualState(material),
                emission
        );
        BlockRenderContext.setQuadFramedBloomBoost(metadata.bloom() || emission > 0);

        return true;
    }

    public boolean shouldUseCeleritasForgeFallback(IBlockState state) {
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        if (MinecraftReflectionCompat.stateMaterialIsFire(state)
                || self().isCeleritasTwilightPortalState(state)) {
            return true;
        }
        // ArchitectureCraft's shape model consumes TileShape material and
        // shape data through its Forge dispatcher. Celeritas's compact model
        // encoder only sees the BlockShape host and collapses it to a cube.
        if (PipelineRuntimeState.isArchitectureCraftShapeBlock(state)) {
            return true;
        }
        if (PipelineRuntimeState.isArchitectureCraftSawbench(state)) {
            return true;
        }
        // Celeritas's native path correctly encodes ordinary emissive models
        // into their real terrain layer. Sending them back through Forge loses
        // the extended vertex data that the shader uses for the base image and
        // bloom. Only renderers which actually require Forge's model contract
        // stay on the compatibility path.
        // With a shader pack, Lumenized deliberately folds its BLOOM quads
        // into the normal terrain pass. Celeritas's direct model route misses
        // that second quad query, leaving only the synthetic BLOOM mesh and
        // therefore a source with no opaque/base representation.
        if (self().stateHasBloomLayerGeometry(state)) {
            return true;
        }
        return false;
    }

    protected static boolean isArchitectureCraftSawbench(IBlockState state) {
        ResourceLocation name = PipelineRuntimeState.registryName(state);
        return name != null
                && "architecturecraft".equalsIgnoreCase(
                MinecraftReflectionCompat.resourceNamespace(name))
                && "sawbench".equals(
                MinecraftReflectionCompat.resourcePathLower(name));
    }

    public boolean shouldUseCeleritasForgeFallback(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (self().isBlockcrafteryEditableState(state)) {
            return true;
        }
        return self().shouldUseCeleritasForgeFallback(state);
    }

    public boolean shouldUseCeleritasLayerNeutralForgeDispatch(IBlockState state) {
        return false;
    }

    public boolean isCeleritasPortalState(IBlockState state) {
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        ResourceLocation name = PipelineRuntimeState.registryName(state);
        String path = name != null ? MinecraftReflectionCompat.resourcePathLower(name) : "";
        String blockClass = MinecraftReflectionCompat.blockFromState(state)
                .getClass().getName().toLowerCase(Locale.ROOT);
        return path.contains("portal") || blockClass.contains("portal");
    }

    public boolean isCeleritasTwilightPortalState(IBlockState state) {
        ResourceLocation name = PipelineRuntimeState.registryName(state);
        if (name == null) {
            return false;
        }
        String namespace = MinecraftReflectionCompat.resourceNamespace(name);
        String path = MinecraftReflectionCompat.resourcePathLower(name);
        return "twilightforest".equalsIgnoreCase(namespace) && path.contains("portal");
    }

    public boolean stateHasBloomLayerGeometry(IBlockState state) {
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        if (self().isExplicitBloomState(state)) {
            return true;
        }
        return self().stateHasBloomResourceGeometry(state);
    }

    /**
     * Allows Celeritas's BLOOM-only blocks to retain their normal base geometry.
     */
    public boolean shouldRenderBloomSourceInBaseLayer(IBlockState state, BlockRenderLayer layer) {
        if (state == null || layer == null
                || AusmBloomLayer.isBloomLayer(layer)) {
            return false;
        }
        // A compatibility mixin may move a BLOOM-only block back into a
        // vanilla layer. Preserve that declared layer before consulting the
        // block's original render-layer preference.
        if (PipelineRuntimeState.canRenderInLayer(state, layer)) {
            return true;
        }
        if (self().isCeleritasTwilightPortalState(state)) {
            return layer == BlockRenderLayer.TRANSLUCENT;
        }
        if (!(self().isExplicitBloomState(state) || self().stateHasBloomLayerGeometry(state)
                || self().stateHasBloomResourceGeometry(state) || self().isLumenizedBloomState(state))) {
            return false;
        }
        BlockRenderLayer naturalLayer = PipelineRuntimeState.safeRenderLayer(state);
        if (naturalLayer != null && !AusmBloomLayer.isBloomLayer(naturalLayer)) {
            return layer == naturalLayer;
        }
        return layer == BlockRenderLayer.CUTOUT;
    }

    public void logFramedBlockDiagnostic(String source, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                         BlockRenderLayer layer, int startVertex, int endVertex, Boolean result,
                                         String extra) {
        if (!PipelineRuntimeState.debugProbeLoggingEnabled()) {
            return;
        }
        if (!FRAMED_BLOCK_DIAGNOSTICS_ENABLED) {
            return;
        }
        if (!self().isFramedBlockDiagnosticTarget(state)) {
            return;
        }

        IBlockState effectiveState = self().effectiveBlockRenderState(state, blockAccess, pos);
        IBlockState inheritedBloomState = self().inheritedBloomRenderState(state, blockAccess, pos);
        IBlockState inheritedGeometryState = self().inheritedBloomGeometryRenderState(state, inheritedBloomState);
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        boolean priority = self().isPriorityFramedDiagnosticName(state)
                || self().isPriorityFramedDiagnosticState(effectiveState, blockAccess, pos, bloomLayer)
                || self().isPriorityFramedDiagnosticState(inheritedBloomState, blockAccess, pos, bloomLayer)
                || (inheritedGeometryState != state
                && self().isPriorityFramedDiagnosticState(inheritedGeometryState, blockAccess, pos, bloomLayer));

        String key = source
                + "|" + self().safeDimensionId(blockAccess instanceof World world ? world : null)
                + "|" + PipelineRuntimeState.formatBlockPos(pos)
                + "|" + PipelineRuntimeState.stateName(state)
                + "|" + String.valueOf(layer)
                + "|" + PipelineRuntimeState.stateName(effectiveState)
                + "|" + PipelineRuntimeState.stateName(inheritedBloomState)
                + "|" + String.valueOf(priority ? extra : "");
        if (!framedBlockDiagnosticKeys.add(key)) {
            return;
        }

        int count = self().nextFramedDiagnosticCount(state, priority);
        if (count < 0) {
            return;
        }

        int delta = startVertex >= 0 && endVertex >= 0 ? endVertex - startVertex : -1;

        MainMod.LOGGER.info(
                "[AUSMFramedDiag] call={} priority={} kind={} source={} dim={} pos={} layer={} bloomLayer={} result={} start={} end={} delta={} access={} extra={} original={} effective={} inheritedBloom={} inheritedGeometry={} inheritedStates={}",
                count,
                priority,
                self().framedDiagnosticKind(state),
                source,
                self().safeDimensionId(blockAccess instanceof World world ? world : null),
                PipelineRuntimeState.formatBlockPos(pos),
                layer,
                bloomLayer,
                result,
                startVertex,
                endVertex,
                delta,
                blockAccess != null ? blockAccess.getClass().getName() : "null",
                extra,
                self().framedDiagnosticState("original", state, blockAccess, pos, layer, bloomLayer),
                self().framedDiagnosticState("effective", effectiveState, blockAccess, pos, layer, bloomLayer),
                self().framedDiagnosticState("inheritedBloom", inheritedBloomState, blockAccess, pos, layer, bloomLayer),
                self().framedDiagnosticState("inheritedGeometry", inheritedGeometryState, blockAccess, pos, layer, bloomLayer),
                self().framedDiagnosticInheritedStates(state, blockAccess, pos, layer, bloomLayer)
        );
    }

    public int blockRenderEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return 0;
        }
        int emission = isPipelineActive && !shaderlessBloomExtractionActive
                ? self().explicitShaderedBlockEmission(state, blockAccess, pos)
                : self().blockRenderEmissionForState(state, blockAccess, pos);
        return PipelineRuntimeState.isBlockcrafteryEditableBlock(state)
                ? Math.max(emission, self().containedFrameEmission(state, blockAccess, pos))
                : emission;
    }

    public boolean shouldUseShaderlessBloomEmission() {
        return false;
    }

    public boolean isManualBloomExtractionEnabled() {
        return false;
    }

    public int blockShaderlessBloomEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return 0;
    }

    public boolean stateHasShaderlessBloomSource(IBlockState state) {
        if (isPipelineActive && !shaderlessBloomExtractionActive) {
            return false;
        }
        return self().blockShaderlessBloomEmission(state, null, null) > 0;
    }

    public boolean stateUsesTextureBloomSource(IBlockState state) {
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null || PipelineRuntimeState.isBlockcrafteryEditableBlock(state)) {
            return false;
        }
        return self().stateHasBloomResourceGeometry(state) || self().isLumenizedBloomState(state);
    }

    /**
     * Celeritas needs full-bright lightmap UVs for Lumenized-compatible bloom sources.
     */
    public boolean shouldForceCeleritasGeometryBloomFullbright(IBlockState state, BlockRenderLayer layer) {
        if (!self().stateHasBloomLayerGeometry(state) || self().stateUsesTextureBloomSource(state)) {
            return false;
        }
        return AusmBloomLayer.isBloomLayer(layer)
                || self().shouldRenderBloomSourceInBaseLayer(state, layer);
    }

    protected int explicitShaderlessBloomEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (self().stateHasBloomLayerGeometry(state) || self().stateHasBloomResourceGeometry(state) || self().isLumenizedBloomState(state)) {
            return self().shaderlessBloomGeometryEmission(state, blockAccess, pos);
        }
        if (self().shaderlessHighLightEmission(state, blockAccess, pos) > 0) {
            return SHADERLESS_BLOOM_GEOMETRY_EMISSION;
        }
        return 0;
    }

    protected int shaderlessHighLightEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return 0;
        }
        try {
            int light = blockAccess != null && pos != null
                    ? MinecraftReflectionCompat.stateLightValue(state, blockAccess, pos)
                    : PipelineRuntimeState.intrinsicBlockEmission(state);
            return light > 0 ? SHADERLESS_BLOOM_GEOMETRY_EMISSION : 0;
        } catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    protected int shaderlessBloomGeometryEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (self().blockRenderEmissionForState(state, blockAccess, pos) > 0) {
            return SHADERLESS_LIGHT_EMITTING_BLOOM_GEOMETRY_EMISSION;
        }
        return SHADERLESS_BLOOM_GEOMETRY_EMISSION;
    }

    public int blockRenderAlpha(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        IBlockState effectiveState = self().effectiveBlockRenderState(state, blockAccess, pos);
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return -1;
        }
        if (self().isCurrentProblemProbeTarget(state) || self().isCurrentProblemProbeTarget(effectiveState)) {
            self().logCurrentProblemProbe("alpha-query", state, blockAccess, pos,
                    "effective=" + PipelineRuntimeState.stateName(effectiveState)
                            + ", alpha=-1"
                            + ", originalOpaque=" + PipelineRuntimeState.safeOpaqueCube(state)
                            + ", originalFull=" + PipelineRuntimeState.safeFullCube(state)
                            + ", effectiveOpaque=" + PipelineRuntimeState.safeOpaqueCube(effectiveState)
                            + ", effectiveFull=" + PipelineRuntimeState.safeFullCube(effectiveState)
                            + ", originalLayer=" + PipelineRuntimeState.safeRenderLayer(state)
                            + ", effectiveLayer=" + PipelineRuntimeState.safeRenderLayer(effectiveState)
                            + ", layer=" + MinecraftReflectionCompat.currentRenderLayer());
        }
        return -1;
    }

    public void setBlockRenderDebugContext(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return;
        }
        IBlockState effectiveState = self().effectiveBlockRenderState(state, blockAccess, pos);
        BlockRenderContext.setDebugBlock(
                self().diagnosticBlockKind(state, effectiveState, blockAccess, pos),
                PipelineRuntimeState.stateName(state),
                PipelineRuntimeState.stateName(effectiveState)
        );
    }

    public String diagnosticStateName(IBlockState state) {
        return PipelineRuntimeState.stateName(state);
    }

    public String diagnosticBlockKind(IBlockState state, IBlockState effectiveState) {
        return self().diagnosticBlockKind(state, effectiveState, null, null);
    }

    protected String diagnosticBlockKind(IBlockState state, IBlockState effectiveState, IBlockAccess blockAccess, BlockPos pos) {
        if (PipelineRuntimeState.isArchitectureCraftShapeBlock(state)) {
            return "architecturecraft";
        }
        if (PipelineRuntimeState.isBlockcrafteryEditableBlock(state)) {
            return "blockcraftery";
        }
        if (self().isPriorityFramedDiagnosticName(state) || self().isPriorityFramedDiagnosticName(effectiveState)) {
            return "emissive-name";
        }
        if (self().blockRenderEmission(state, blockAccess, pos) > 0
                || self().blockRenderEmission(effectiveState, blockAccess, pos) > 0
                || self().blockEntityId(state, blockAccess, pos) != 0
                || self().blockEntityId(effectiveState, blockAccess, pos) != 0) {
            return "active-light-or-id";
        }
        return "other";
    }

    public boolean isCurrentProblemProbeTarget(IBlockState state) {
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return false;
        }
        return self().isPriorityFramedDiagnosticName(state)
                || PipelineRuntimeState.isAstralCrystalCluster(state)
                || PipelineRuntimeState.stateName(state).contains("lumenized")
                || PipelineRuntimeState.stateName(state).contains("glow")
                || PipelineRuntimeState.stateName(state).contains("emissive")
                || PipelineRuntimeState.stateName(state).contains("shimmer")
                || PipelineRuntimeState.stateName(state).contains("shinyflower")
                || PipelineRuntimeState.stateName(state).contains("nitor")
                || PipelineRuntimeState.stateName(state).contains("crystal");
    }

    public boolean shouldProbeSoftVanillaSpecialBlock(IBlockState state, IBlockState effectiveState,
                                                      IBlockAccess blockAccess, BlockPos pos) {
        if (!self().isComplementarySoftVanillaStartupFallbackActive()) {
            return false;
        }
        if (softVanillaSpecialBlockProbeLogs >= MAX_SOFT_VANILLA_SPECIAL_BLOCK_PROBE_LOGS) {
            return false;
        }
        return self().isSoftVanillaSpecialProbeState(state)
                || self().isSoftVanillaSpecialProbeState(effectiveState)
                || self().blockRenderEmission(state, blockAccess, pos) > 0
                || self().blockRenderEmission(effectiveState, blockAccess, pos) > 0
                || self().blockEntityId(state, blockAccess, pos) != 0
                || self().blockEntityId(effectiveState, blockAccess, pos) != 0;
    }

    protected boolean isSoftVanillaSpecialProbeState(IBlockState state) {
        if (state == null) {
            return false;
        }
        String name = PipelineRuntimeState.stateName(state).toLowerCase(Locale.ROOT);
        Block block = MinecraftReflectionCompat.blockFromState(state);
        String className = block != null ? block.getClass().getName().toLowerCase(Locale.ROOT) : "";
        return name.contains("quantumthings")
                || name.contains("lumen")
                || name.contains("portal")
                || name.contains("emissive")
                || name.contains("glow")
                || name.contains("nitor")
                || name.contains("shimmer")
                || name.contains("crystal")
                || name.contains("astral")
                || className.contains("quantumthings")
                || className.contains("lumen")
                || className.contains("portal")
                || className.contains("emissive")
                || className.contains("glow");
    }

    public void logSoftVanillaSpecialBlockProbe(String source, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                                int startVertex, int endVertex, Boolean result, String detail) {
        if (!self().isComplementarySoftVanillaStartupFallbackActive()) {
            return;
        }
        IBlockState effectiveState = self().effectiveBlockRenderState(state, blockAccess, pos);
        if (!self().shouldProbeSoftVanillaSpecialBlock(state, effectiveState, blockAccess, pos)) {
            return;
        }
        String key = source
                + "|" + self().safeDimensionId(blockAccess instanceof World world ? world : null)
                + "|" + PipelineRuntimeState.formatBlockPos(pos)
                + "|" + String.valueOf(MinecraftReflectionCompat.currentRenderLayer())
                + "|" + PipelineRuntimeState.stateName(state)
                + "|" + PipelineRuntimeState.stateName(effectiveState);
        if (!softVanillaSpecialBlockProbeKeys.add(key)) {
            return;
        }
        int count = ++softVanillaSpecialBlockProbeLogs;
        if (count > MAX_SOFT_VANILLA_SPECIAL_BLOCK_PROBE_LOGS) {
            return;
        }
        int delta = startVertex >= 0 && endVertex >= 0 ? endVertex - startVertex : -1;
        MainMod.LOGGER.info(
                "[AUSMSoftVanillaBlockProbe] call={} source={} dim={} pos={} layer={} frame={} phase={} state={} effective={} renderLayer={} effectiveRenderLayer={} emission={} effectiveEmission={} blockId={} effectiveBlockId={} start={} end={} delta={} result={} detail={}",
                count,
                source,
                self().safeDimensionId(blockAccess instanceof World world ? world : null),
                PipelineRuntimeState.formatBlockPos(pos),
                MinecraftReflectionCompat.currentRenderLayer(),
                pipelineFrameId,
                self().getPhase(),
                PipelineRuntimeState.stateName(state),
                PipelineRuntimeState.stateName(effectiveState),
                PipelineRuntimeState.safeRenderLayer(state),
                PipelineRuntimeState.safeRenderLayer(effectiveState),
                self().blockRenderEmission(state, blockAccess, pos),
                self().blockRenderEmission(effectiveState, blockAccess, pos),
                self().blockEntityId(state, blockAccess, pos),
                self().blockEntityId(effectiveState, blockAccess, pos),
                startVertex,
                endVertex,
                delta,
                result,
                detail
        );
    }
}
