package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.l.ausm.impl.pipeline.compat.BlockcrafteryContainedShapeGeometry;
import com.l.ausm.impl.pipeline.compat.BlockcrafteryContainedStateCompat;
import com.l.ausm.impl.pipeline.compat.TerrainRenderProbeState;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.l.ausm.impl.pipeline.diagnostics.BlockRenderDiagnostics.ausm$bloomFallbackLayer;
import static com.l.ausm.impl.pipeline.diagnostics.BlockRenderDiagnostics.ausm$bufferDetails;
import static com.l.ausm.impl.pipeline.diagnostics.BlockRenderDiagnostics.ausm$canRenderInLayer;
import static com.l.ausm.impl.pipeline.diagnostics.BlockRenderDiagnostics.ausm$externalCaller;
import static com.l.ausm.impl.pipeline.diagnostics.BlockRenderDiagnostics.ausm$isEmissiveBloomFallbackSource;
import static com.l.ausm.impl.pipeline.diagnostics.BlockRenderDiagnostics.ausm$isRenderProbeTarget;
import static com.l.ausm.impl.pipeline.diagnostics.BlockRenderDiagnostics.ausm$naturalRenderLayer;
import static com.l.ausm.impl.pipeline.diagnostics.BlockRenderDiagnostics.ausm$stateName;

@Mixin(BlockRendererDispatcher.class)
public class BlockRendererDispatcherMixin {
    @Unique
    private static final ThreadLocal<Boolean> ausm$renderingContainedVisuals = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Boolean> ausm$renderingContainedHostShape = new ThreadLocal<>();

    /**
     * EnderIO's connected fused-quartz model only resolves its single-block
     * payload while Forge reports SOLID.  AUSM routes that payload to CUTOUT
     * because its texture is binary alpha; retain the caller layer here so
     * the model can be evaluated in its native context without moving the
     * destination BufferBuilder out of CUTOUT.
     */
    @Unique
    private static final ThreadLocal<BlockRenderLayer> ausm$enderIoSolidPayloadRestoreLayer = new ThreadLocal<>();

    @Unique
    private static final AtomicInteger ausm$containedEnderIoLayerProbeCount = new AtomicInteger();

    /**
     * Resolving EnderIO's model through the dispatcher is the only reliable
     * way to see its connected single-block geometry.  Keep a compact sample
     * of the emitted UV bounds so a frame mapper issue is distinguishable
     * from an incorrect render-layer selection.
     */
    @Unique
    private static final AtomicInteger ausm$enderIoResolvedQuadProbeCount = new AtomicInteger();

    @Inject(method = "func_175018_a(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z", at = @At("HEAD"), remap = false, cancellable = true)
    private void ausm$beforeRenderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, CallbackInfoReturnable<Boolean> cir) {
        if (ausm$beginContainedShapeCapture(state, pos, blockAccess, bufferBuilder, cir)) {
            return;
        }
        ausm$enterEnderIoSolidPayloadContext(state);
        ausm$logEnderIoResolvedQuadProbe((BlockRendererDispatcher) (Object) this, state, pos, blockAccess);
        try {
            BlockRendererDispatcherHooks.LIQUID_RENDER.remove();
            PipelineContext pipeline = PipelineContext.getInstance();
            boolean blockcrafteryHost = pipeline.isBlockcrafteryEditableState(state);
            if (blockcrafteryHost) {
                int start = bufferBuilder != null
                        ? MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1;
                pipeline.logBlockcrafteryRouteProbe(
                        "dispatcher-head", state, blockAccess, pos, bufferBuilder, start, start, null);
            }
            Block block = MinecraftReflectionCompat.blockFromState(state);
            BlockRenderLayer naturalLayer = block != null
                    ? MinecraftReflectionCompat.blockRenderLayer(block)
                    : null;
            if (naturalLayer != null && MinecraftReflectionCompat.currentRenderLayer() == null) {
                MinecraftReflectionCompat.setCurrentRenderLayer(naturalLayer);
            }
            // Vanilla's BlockFluidRenderer only supports BlockLiquid. Forge
            // BlockFluidBase fluids, such as Astral Liquid Starlight, render
            // through their extended model instead and must stay on the normal
            // dispatcher path.
            if (MinecraftReflectionCompat.stateIsVanillaLiquid(state)) {
                if (!pipeline.isPipelineActive()) {
                    // Shaderless rendering must retain Forge/Minecraft's
                    // native fluid path, especially for custom BlockFluidBase
                    // implementations such as Astral Sorcery's starlight.
                    BlockRendererDispatcherHooks.LIQUID_RENDER.remove();
                    return;
                }
                int liquidProbe = BlockRendererDispatcherHooks.LIQUID_DISPATCH_PROBE_COUNT.incrementAndGet();
                int liquidStart = bufferBuilder != null
                        ? MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1;
                if (liquidProbe <= 96 && ausm$isAstralLiquid(state)) {
                    MainMod.LOGGER.info(
                            "[AUSMAstralLiquidDispatchProbe] call={} stage=enter thread={} layer={} state={} pos={} buffer={} start={} format={} pipelineFormat={} access={}",
                            liquidProbe,
                            Thread.currentThread().getName(),
                            MinecraftReflectionCompat.currentRenderLayer(),
                            state,
                            pos,
                            bufferBuilder,
                            liquidStart,
                            bufferBuilder != null ? MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder) : null,
                            bufferBuilder != null && ExtendedVertexFormats.isPipelineBlock(
                                    MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder)),
                            blockAccess != null ? blockAccess.getClass().getName() : "null");
                }
                // Fluid rendering is already complete vanilla geometry. Do
                // not attach AUSM metadata writes to CCL/Nothirium's liquid
                // buffer; those buffers are also consumed by native GL draw
                // calls after the worker returns.
                BlockRendererDispatcherHooks.LIQUID_RENDER.set(Boolean.TRUE);
                TerrainRenderProbeState.clearTerrainDispatchStart();
                BlockRendererDispatcherHooks.PROBE_START_VERTEX.remove();
                BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.remove();
                BlockRendererDispatcherHooks.SOFT_VANILLA_SPECIAL_START_VERTEX.remove();
                IBlockState actualState = pipeline.actualBlockRenderState(state, blockAccess, pos);
                BlockRenderContext.setBlockEntityId(pipeline.blockEntityIdForActualState(actualState, blockAccess, pos));
                BlockRenderContext.setRenderType((short) MinecraftReflectionCompat.stateRenderTypeOrdinal(actualState));
                BlockRenderContext.setMetadata(pipeline.blockMetadataForActualState(actualState));
                BlockRenderContext.setLocalBlockPos(MinecraftReflectionCompat.blockPosX(pos),
                        MinecraftReflectionCompat.blockPosY(pos), MinecraftReflectionCompat.blockPosZ(pos));
                BlockRenderContext.setWorldBlockContext(blockAccess, pos);
                BlockRenderContext.setFramedMaterialOwner(false);
                BlockRenderContext.setPackedLightmap(ausm$packedLightmap(actualState, blockAccess, pos));
                BlockRenderContext.setBlockEmission(pipeline.blockRenderEmission(state, blockAccess, pos));
                BlockRenderContext.setFramedBloomBoost(false);
                BlockRenderContext.setBloomOnlyEmission(false);
                BlockRenderContext.setBlockAlpha(pipeline.blockRenderAlpha(state, blockAccess, pos));
                BlockRenderContext.setCustomLiquidTint(pipeline.customLiquidTintColor(state, blockAccess, pos));
                BlockRenderContext.setCrystalOnlyEmission(false);
                BlockRenderContext.setSeparateAoEligible(false);
                int startVertices = bufferBuilder != null
                        ? MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1;
                boolean rendered = MinecraftReflectionCompat.renderLiquidBlock(
                        (BlockRendererDispatcher) (Object) this, blockAccess, state, pos, bufferBuilder);
                // Several Forge fluid implementations append valid vertices
                // but return false. The caller uses the return value to decide
                // whether the translucent layer contains geometry.
                if (!rendered && bufferBuilder != null && startVertices >= 0) {
                    rendered = MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) > startVertices;
                }
                if (liquidProbe <= 96 && ausm$isAstralLiquid(state)) {
                    int liquidEnd = bufferBuilder != null
                            ? MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1;
                    MainMod.LOGGER.info(
                            "[AUSMAstralLiquidDispatchProbe] call={} stage=return rendered={} start={} end={} delta={} layer={} format={} pipelineFormat={}",
                            liquidProbe,
                            rendered,
                            liquidStart,
                            liquidEnd,
                            liquidStart >= 0 && liquidEnd >= 0 ? liquidEnd - liquidStart : -1,
                            MinecraftReflectionCompat.currentRenderLayer(),
                            bufferBuilder != null ? MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder) : null,
                            bufferBuilder != null && ExtendedVertexFormats.isPipelineBlock(
                                    MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder)));
                }
                cir.setReturnValue(rendered);
                return;
            }
            IBlockState actualState = pipeline.actualBlockRenderState(state, blockAccess, pos);
            IBlockState contextState = pipeline.effectiveBlockRenderState(state, actualState, blockAccess, pos);
            if (contextState == null) {
                contextState = state;
            }
            int startVertex = bufferBuilder != null ? MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1;
            TerrainRenderProbeState.setTerrainDispatchStart(startVertex);
            int blockEntityId = pipeline.blockEntityIdForActualState(contextState, blockAccess, pos);
            int blockEmission = pipeline.shouldUseShaderlessBloomEmission()
                    ? pipeline.blockShaderlessBloomEmission(state, blockAccess, pos)
                    : pipeline.blockRenderEmission(state, blockAccess, pos);
            int packedLightmap = ausm$packedLightmap(contextState, blockAccess, pos);
            ausm$logShaderlessDispatchLightProbe(pipeline, state, contextState, blockAccess, pos, packedLightmap);
            // Native BLOOM sources such as RandomThings luminous blocks expose no
            // vanilla light emission. Keep their base-pass vertices explicitly
            // marked so shader packs can distinguish them from an ordinary block
            // before optional material effects (notably coated textures) run.
            BlockRenderContext.configureBlock(
                    blockEntityId,
                    (short) MinecraftReflectionCompat.stateRenderTypeOrdinal(contextState),
                    pipeline.blockMetadataForActualState(contextState),
                    MinecraftReflectionCompat.blockPosX(pos),
                    MinecraftReflectionCompat.blockPosY(pos),
                    MinecraftReflectionCompat.blockPosZ(pos),
                    blockAccess,
                    pos,
                    // The frame supplies only the baked host geometry. Its contained
                    // block remains the sole visual/metadata source.
                    blockcrafteryHost && !pipeline.shouldReplaceFilledBlockcrafteryFrame(state, blockAccess, pos),
                    ausm$isAgricraftCropState(contextState),
                    packedLightmap,
                    blockEmission,
                    pipeline.stateHasBloomLayerGeometry(contextState),
                    pipeline.blockRenderAlpha(state, blockAccess, pos),
                    pipeline.customLiquidTintColor(state, blockAccess, pos),
                    pipeline.shouldUseCrystalOnlyEmission(actualState),
                    pipeline.shouldSeparateBlockAo(contextState));
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
                BlockRendererDispatcherHooks.PROBE_START_VERTEX.set(MinecraftReflectionCompat.bufferVertexCount(bufferBuilder));
            } else {
                BlockRendererDispatcherHooks.PROBE_START_VERTEX.remove();
            }
            if (pipeline.isFramedBlockDiagnosticTarget(state) && bufferBuilder != null) {
                BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.set(MinecraftReflectionCompat.bufferVertexCount(bufferBuilder));
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
    private static void ausm$logEnderIoResolvedQuadProbe(BlockRendererDispatcher dispatcher, IBlockState state,
                                                         BlockPos pos, IBlockAccess blockAccess) {
        if (!BlockcrafteryContainedStateCompat.isEnderIoFusedQuartzState(state)) {
            return;
        }
        int call = ausm$enderIoResolvedQuadProbeCount.incrementAndGet();
        if (call > 24) {
            return;
        }
        try {
            IBlockState extended = MinecraftReflectionCompat.blockExtendedState(state, blockAccess, pos);
            IBakedModel model = MinecraftReflectionCompat.blockModel(dispatcher, extended);
            StringBuilder quads = new StringBuilder();
            ausm$appendEnderIoQuadSummary(quads, "general", MinecraftReflectionCompat.bakedModelQuads(model, extended, null, 0L));
            for (EnumFacing face : EnumFacing.values()) {
                ausm$appendEnderIoQuadSummary(quads, face.name(),
                        MinecraftReflectionCompat.bakedModelQuads(model, extended, face, 0L));
            }
            MainMod.LOGGER.info("[AUSMEnderIoResolvedQuadProbe] call={} pos={} destinationLayer={} modelLayer={} state={} model={} quads={}",
                    call, pos, ausm$enderIoSolidPayloadRestoreLayer.get(), MinecraftReflectionCompat.currentRenderLayer(),
                    state, model != null ? model.getClass().getName() : "null", quads);
        } catch (RuntimeException | LinkageError ignored) {
            // Probe only: never let an optional EnderIO model inspection alter rendering.
        }
    }

    @Unique
    private static void ausm$appendEnderIoQuadSummary(StringBuilder output, String face, List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return;
        }
        if (!output.isEmpty()) {
            output.append(';');
        }
        output.append(face).append('=').append(quads.size()).append('[');
        for (int index = 0; index < Math.min(quads.size(), 2); index++) {
            if (index > 0) {
                output.append('|');
            }
            BakedQuad quad = quads.get(index);
            int[] data = MinecraftReflectionCompat.bakedQuadVertexData(quad);
            TextureAtlasSprite sprite = MinecraftReflectionCompat.bakedQuadSprite(quad);
            int stride = data != null && data.length >= 4 ? data.length / 4 : 0;
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            float minU = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;
            if (stride >= 6) {
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * stride;
                    float x = Float.intBitsToFloat(data[offset]);
                    float y = Float.intBitsToFloat(data[offset + 1]);
                    float z = Float.intBitsToFloat(data[offset + 2]);
                    float u = Float.intBitsToFloat(data[offset + 4]);
                    float v = Float.intBitsToFloat(data[offset + 5]);
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                    minU = Math.min(minU, u);
                    maxU = Math.max(maxU, u);
                    minV = Math.min(minV, v);
                    maxV = Math.max(maxV, v);
                }
            }
            output.append(sprite != null ? MinecraftReflectionCompat.spriteIconName(sprite) : "null")
                    .append(" p=").append(stride >= 6 ? String.format(Locale.ROOT, "%.3f..%.3f,%.3f..%.3f,%.3f..%.3f", minX, maxX, minY, maxY, minZ, maxZ) : "?")
                    .append(" uv=").append(stride >= 6 ? String.format(Locale.ROOT, "%.5f..%.5f,%.5f..%.5f", minU, maxU, minV, maxV) : "?");
        }
        output.append(']');
    }

    /**
     * Renders every contained visual layer through the host's native layer.
     * The ordinary dispatcher call can emit the contained cube without any
     * host quads on translucent and BLOOM passes, so pairing at RETURN leaves
     * a full-cube overlay behind.  Render both spans explicitly instead, with
     * the contained layer retained as payload and the host layer as geometry.
     */
    @Unique
    private boolean ausm$beginContainedShapeCapture(IBlockState state, BlockPos pos,
                                                    IBlockAccess blockAccess, BufferBuilder bufferBuilder,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (ausm$renderingContainedVisuals.get() != null
                || ausm$renderingContainedHostShape.get() != null
                || bufferBuilder == null) {
            return false;
        }
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState contained = pipeline.inheritedBlockcrafteryRenderState(state, blockAccess, pos);
        if (contained == null) {
            return false;
        }
        BlockRenderLayer layer = MinecraftReflectionCompat.currentRenderLayer();
        BlockRenderLayer extractedEnderIoLayer = BlockcrafteryContainedStateCompat.enderIoGlassRenderLayer(contained);
        if (layer != null && !AusmBloomLayer.isBloomLayer(layer)
                && extractedEnderIoLayer != null && layer != extractedEnderIoLayer) {
            cir.setReturnValue(false);
            return true;
        }
        if (layer != null && !AusmBloomLayer.isBloomLayer(layer)
                && extractedEnderIoLayer == null && !ausm$canRenderInLayer(contained, layer)) {
            cir.setReturnValue(false);
            return true;
        }
        int start = MinecraftReflectionCompat.bufferVertexCount(bufferBuilder);
        BlockRenderLayer payloadModelLayer = extractedEnderIoLayer != null
                ? BlockRenderLayer.SOLID : layer;
        try {
            ausm$renderingContainedVisuals.set(Boolean.TRUE);
            if (payloadModelLayer != layer) {
                MinecraftReflectionCompat.setCurrentRenderLayer(payloadModelLayer);
            }
            MinecraftReflectionCompat.renderBlock((BlockRendererDispatcher) (Object) this,
                    contained, pos, blockAccess, bufferBuilder);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        } finally {
            if (payloadModelLayer != layer) {
                MinecraftReflectionCompat.setCurrentRenderLayer(layer);
            }
            ausm$renderingContainedVisuals.remove();
        }
        int containedEnd = MinecraftReflectionCompat.bufferVertexCount(bufferBuilder);
        if (containedEnd <= start) {
            cir.setReturnValue(false);
            return true;
        }

        BlockRenderLayer hostLayer = ausm$naturalRenderLayer(state);
        BlockRenderLayer selectedHostLayer = ausm$renderContainedHostGeometry(
                state, pos, blockAccess, bufferBuilder, containedEnd, layer, hostLayer);
        int hostEnd = MinecraftReflectionCompat.bufferVertexCount(bufferBuilder);
        if (extractedEnderIoLayer != null) {
            int enderIoProbe = ausm$containedEnderIoLayerProbeCount.incrementAndGet();
            if (enderIoProbe <= 16) {
                MainMod.LOGGER.info("[AUSMContainedEnderIoLayerProbe] call={} pos={} destinationLayer={} modelLayer={} selectedHostLayer={} contained={} containedVertices={} hostVertices={}",
                        enderIoProbe, pos, layer, payloadModelLayer, selectedHostLayer,
                        pipeline.diagnosticStateName(contained), containedEnd - start, hostEnd - containedEnd);
            }
        }
        boolean preserveHostSeparateAo = pipeline.shouldSeparateBlockAo(contained, blockAccess, pos);
        // RandomThings luminous blocks advertise their brightness through a
        // native BLOOM model layer and therefore report zero through the
        // vanilla block-emission query. Treat that proven native Bloom route
        // as emissive before deciding whose colour/AO and lightmap survive
        // shape mapping; otherwise a framed source inherits the dark host
        // payload while the direct source remains full-bright.
        boolean containedFrameBloom = pipeline.hasContainedFrameBloom(state, blockAccess, pos);
        // The payload vertices relocate from a cube to arbitrary frame shape
        // geometry. Their light samples must therefore follow the host even
        // when this shader path does not use separate AO; otherwise one host
        // corner interpolates a cube sample into an unrelated shaped face.
        boolean preserveHostLightmap = !containedFrameBloom
                && pipeline.blockRenderEmission(contained, blockAccess, pos) <= 0;
        // The shape mapper retains the contained visual payload, including
        // mc_Entity.  Mark the remapped base payload here, rather than the
        // temporary host draw, so Complimentary sees the same contained
        // emissive material after the host positions replace the cube.
        // RandomThings luminous blocks declare their brightness through a
        // native BLOOM model layer, not vanilla light emission.  Their
        // block-light query is therefore zero even though the native overlay
        // is present.  Use the exact shared frame-bloom predicate for both
        // inherited base emission and copied-overlay depth separation.
        boolean markFramedEmission = !AusmBloomLayer.isBloomLayer(layer) && containedFrameBloom;
        boolean liftBloomOverlay = AusmBloomLayer.isBloomLayer(layer) && containedFrameBloom;
        boolean shaped = BlockcrafteryContainedShapeGeometry
                .replaceWithContainedVisuals(bufferBuilder, start, containedEnd, hostEnd,
                        preserveHostSeparateAo, preserveHostLightmap,
                        extractedEnderIoLayer != null, markFramedEmission, liftBloomOverlay, contained);
        if (!shaped && bufferBuilder instanceof IBufferBuilderExtension extension) {
            extension.ausm$truncateVertexCount(start);
        }
        cir.setReturnValue(shaped);
        return true;
    }

    /**
     * A filled-frame host can advertise SOLID while its baked model only
     * emits in CUTOUT or TRANSLUCENT.  Keep the contained payload in the
     * caller's destination buffer, but ask the host for geometry through the
     * first layer which actually writes vertices.  Bloom remains pinned to
     * the host's ordinary geometry layer, since the Bloom pseudo-layer is a
     * payload pass rather than a baked-model layer.
     */
    @Unique
    private BlockRenderLayer ausm$renderContainedHostGeometry(IBlockState state, BlockPos pos,
                                                              IBlockAccess blockAccess, BufferBuilder bufferBuilder,
                                                              int hostStart, BlockRenderLayer payloadLayer,
                                                              BlockRenderLayer nativeHostLayer) {
        BlockRenderLayer[] candidates = AusmBloomLayer.isBloomLayer(payloadLayer)
                ? new BlockRenderLayer[]{nativeHostLayer, BlockRenderLayer.SOLID}
                : new BlockRenderLayer[]{payloadLayer, nativeHostLayer, BlockRenderLayer.CUTOUT,
                BlockRenderLayer.CUTOUT_MIPPED, BlockRenderLayer.TRANSLUCENT, BlockRenderLayer.SOLID};
        BlockRenderLayer selected = null;
        try {
            ausm$renderingContainedHostShape.set(Boolean.TRUE);
            for (int candidateIndex = 0; candidateIndex < candidates.length; candidateIndex++) {
                BlockRenderLayer candidate = candidates[candidateIndex];
                if (candidate == null || AusmBloomLayer.isBloomLayer(candidate)) {
                    continue;
                }
                boolean duplicate = false;
                for (int priorIndex = 0; priorIndex < candidateIndex; priorIndex++) {
                    if (candidates[priorIndex] == candidate) {
                        duplicate = true;
                        break;
                    }
                }
                if (duplicate) {
                    continue;
                }
                MinecraftReflectionCompat.setCurrentRenderLayer(candidate);
                try {
                    MinecraftReflectionCompat.renderBlock((BlockRendererDispatcher) (Object) this,
                            state, pos, blockAccess, bufferBuilder);
                } catch (RuntimeException | LinkageError ignored) {
                    // Try the next baked-model layer; a partial host span is
                    // still valid and is retained below.
                }
                if (MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) > hostStart) {
                    selected = candidate;
                    break;
                }
            }
        } finally {
            MinecraftReflectionCompat.setCurrentRenderLayer(payloadLayer);
            ausm$renderingContainedHostShape.remove();
        }
        return selected;
    }


    @Unique
    private static boolean ausm$isAstralLiquid(IBlockState state) {
        return state != null && state.toString().toLowerCase(Locale.ROOT).contains("liquidstarlight");
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
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        ResourceLocation name = MinecraftReflectionCompat.blockRegistryName(MinecraftReflectionCompat.blockFromState(state));
        if (name == null) {
            return false;
        }
        if ("agricraft".equals(MinecraftReflectionCompat.resourceNamespace(name)) && "crop".equals(MinecraftReflectionCompat.resourcePath(name))) {
            return true;
        }
        return "natura".equals(MinecraftReflectionCompat.resourceNamespace(name)) && "cotton_crop".equals(MinecraftReflectionCompat.resourcePath(name));
    }

    @Unique
    private static int ausm$packedLightmap(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return 0;
        }
        try {
            int packedLightmap = MinecraftReflectionCompat.statePackedLightmapCoords(state, blockAccess, pos);
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
            return MinecraftReflectionCompat.blockAccessCombinedLight(blockAccess, pos, lightValue);
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
            return MinecraftReflectionCompat.stateLightValue(state, blockAccess, pos);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    @Unique
    private static void ausm$enterEnderIoSolidPayloadContext(IBlockState state) {
        BlockRenderLayer destinationLayer = MinecraftReflectionCompat.currentRenderLayer();
        if (destinationLayer != BlockRenderLayer.CUTOUT
                || !BlockcrafteryContainedStateCompat.isEnderIoFusedQuartzState(state)) {
            return;
        }
        ausm$enderIoSolidPayloadRestoreLayer.set(destinationLayer);
        MinecraftReflectionCompat.setCurrentRenderLayer(BlockRenderLayer.SOLID);
    }

    @Unique
    private static void ausm$leaveEnderIoSolidPayloadContext(IBlockState state) {
        BlockRenderLayer restoreLayer = ausm$enderIoSolidPayloadRestoreLayer.get();
        if (restoreLayer == null || !BlockcrafteryContainedStateCompat.isEnderIoFusedQuartzState(state)) {
            return;
        }
        try {
            MinecraftReflectionCompat.setCurrentRenderLayer(restoreLayer);
        } finally {
            ausm$enderIoSolidPayloadRestoreLayer.remove();
        }
    }

    @Inject(method = "func_175018_a(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z", at = @At("RETURN"), remap = false, cancellable = true)
    private void ausm$afterRenderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, CallbackInfoReturnable<Boolean> cir) {
        ausm$leaveEnderIoSolidPayloadContext(state);
        PipelineContext pipeline = PipelineContext.getInstance();
        Integer framedStart = BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.get();
        if (ausm$appendBloomFallbackIfMissing(state, pos, blockAccess, bufferBuilder)) {
            cir.setReturnValue(true);
        }
        Integer terrainStart = TerrainRenderProbeState.terrainDispatchStart();
        int terrainEnd = bufferBuilder != null ? MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1;
        ausm$logTerrainDispatchProbe("return", state, pipeline.effectiveBlockRenderState(state, blockAccess, pos), pos,
                blockAccess, bufferBuilder, terrainStart != null ? terrainStart : -1, terrainEnd, cir.getReturnValue(),
                BlockRenderContext.blockEntityId(), BlockRenderContext.blockEmission(), BlockRenderContext.packedLightmap());
        TerrainRenderProbeState.clearTerrainDispatchStart();
        ausm$logRenderProbe(state, pos, blockAccess, bufferBuilder, cir.getReturnValue());
        if (framedStart != null && bufferBuilder != null) {
            pipeline.logBlockcrafteryRouteProbe(
                    "dispatcher-return", state, blockAccess, pos, bufferBuilder,
                    framedStart,
                    MinecraftReflectionCompat.bufferVertexCount(bufferBuilder),
                    cir.getReturnValue());
        }
        Integer softVanillaSpecialStart = BlockRendererDispatcherHooks.SOFT_VANILLA_SPECIAL_START_VERTEX.get();
        if (softVanillaSpecialStart != null) {
            pipeline.logSoftVanillaSpecialBlockProbe("dispatcher-return", state, blockAccess, pos,
                    softVanillaSpecialStart,
                    bufferBuilder != null ? MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1,
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
        boolean framedFallbackCandidate = pipeline.isFramedBlockDiagnosticTarget(state);
        // Filled frames compile directly from their contained block; no host
        // geometry is copied into the bloom pass.
        if (framedFallbackCandidate && pipeline.hasNothiriumBloomBackend()) {
            return false;
        }
        if (!pipeline.isManualBloomExtractionEnabled()) {
            return false;
        }
        BlockRenderLayer layer = MinecraftReflectionCompat.currentRenderLayer();
        int framedEmission = 0;
        boolean targetedFramedBloom = false;
        Integer start = BlockRendererDispatcherHooks.PROBE_START_VERTEX.get();
        boolean framedFallback = false;
        if (start == null && pipeline.isFramedBlockDiagnosticTarget(state)) {
            start = BlockRendererDispatcherHooks.FRAMED_DIAGNOSTIC_START_VERTEX.get();
            framedFallback = true;
        }
        if (start == null) {
            ausm$logEmissiveDispatcherFallbackSkip("missing-start", state, null, null, pos,
                    MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer(), null, bufferBuilder, framedFallbackCandidate);
            return false;
        }
        if (bufferBuilder == null) {
            ausm$logEmissiveDispatcherFallbackSkip("missing-buffer", state, null, null, pos,
                    MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer(), start, null, framedFallbackCandidate);
            return false;
        }
        if (BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null) {
            ausm$logEmissiveDispatcherFallbackSkip("recursive-fallback", state, null, null, pos,
                    MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer(), start, bufferBuilder, framedFallbackCandidate);
            return false;
        }

        IBlockState inheritedState = pipeline.inheritedBloomRenderState(state, blockAccess, pos);
        ausm$logBlockcrafteryBloomFallbackProbe("candidate", state, inheritedState, null, pos, blockAccess,
                MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer(), start, -1, framedEmission,
                "buffer=" + ausm$bufferDetails(bufferBuilder));
        boolean forcedFramedBloom = targetedFramedBloom;
        IBlockState fallbackSourceState = ausm$isEmissiveBloomFallbackSource(inheritedState)
                ? inheritedState
                : forcedFramedBloom ? state : framedFallbackCandidate ? null : state;
        if (!forcedFramedBloom && !ausm$isEmissiveBloomFallbackSource(fallbackSourceState)) {
            return false;
        }
        IBlockState fallbackState = pipeline.inheritedBloomGeometryRenderState(state, fallbackSourceState);

        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (layer == null || bloomLayer == null) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-missing-layer", state, inheritedState, fallbackSourceState,
                    pos, blockAccess, layer, bloomLayer, start, MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) - start,
                    framedEmission, "missing=" + (layer == null ? "current" : "bloom"));
            ausm$logEmissiveDispatcherFallbackSkip(layer == null ? "missing-current-layer" : "missing-bloom-layer",
                    state, inheritedState, fallbackSourceState, pos, layer, bloomLayer, start, bufferBuilder,
                    framedFallbackCandidate);
            return false;
        }
        if (framedFallback && layer != bloomLayer) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-non-bloom-layer", state, inheritedState, fallbackSourceState,
                    pos, blockAccess, layer, bloomLayer, start, MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) - start,
                    framedEmission, "framed=true");
            ausm$logEmissiveDispatcherFallbackSkip("framed-non-bloom-layer", state, inheritedState, fallbackSourceState,
                    pos, layer, bloomLayer, start, bufferBuilder, true);
            return false;
        }
        if (!framedFallback && layer == bloomLayer) {
            ausm$logBlockcrafteryBloomFallbackProbe("skip-already-bloom-layer", state, inheritedState,
                    fallbackSourceState, pos, blockAccess, layer, bloomLayer, start,
                    MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) - start, framedEmission, "framed=false");
            ausm$logEmissiveDispatcherFallbackSkip("nonframed-already-bloom-layer", state, inheritedState,
                    fallbackSourceState, pos, layer, bloomLayer, start, bufferBuilder, false);
            return false;
        }

        boolean textureBloomSource = pipeline.stateUsesTextureBloomSource(fallbackSourceState);
        boolean solidBloomMaskFallback = framedFallback && !textureBloomSource;
        IBlockState fallbackGeometryState = solidBloomMaskFallback ? state : fallbackState;
        int normalDelta = MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) - start;
        if (normalDelta > 0 && solidBloomMaskFallback) {
            ((IBufferBuilderExtension) bufferBuilder).ausm$truncateVertexCount(start);
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
        int fallbackStart = MinecraftReflectionCompat.bufferVertexCount(bufferBuilder);
        boolean rendered = false;
        try {
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.set(Boolean.TRUE);
            if (solidBloomMaskFallback) {
                BlockRenderContext.setBloomMaskFallback(true);
            }
            MinecraftReflectionCompat.setCurrentRenderLayer(fallbackRenderLayer);
            BlockRendererDispatcher dispatcher = MinecraftReflectionCompat.blockRendererDispatcher(MinecraftReflectionCompat.minecraft());
            rendered = dispatcher != null && MinecraftReflectionCompat.renderBlock(dispatcher, fallbackGeometryState, pos, blockAccess, bufferBuilder);
        } finally {
            MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
            BlockRenderContext.clearBloomMaskFallback();
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.remove();
        }

        int fallbackDelta = MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) - fallbackStart;
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
                bufferBuilder != null ? MinecraftReflectionCompat.bufferVertexCount(bufferBuilder) : -1,
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
    private static void ausm$logRenderProbe(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, Boolean result) {
        // Probe disabled.
    }

}
