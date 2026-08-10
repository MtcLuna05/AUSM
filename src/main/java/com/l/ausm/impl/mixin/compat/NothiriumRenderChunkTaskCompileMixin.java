package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.l.ausm.impl.pipeline.compat.BlockcrafteryContainedStateCompat;
import com.l.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import com.l.ausm.impl.pipeline.compat.TerrainCompileCoordinator;
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
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    private static final int AUSM_EMISSIVE_FALLBACK_LOG_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_EMISSIVE_FALLBACK_LOGS = new AtomicInteger();

    @Unique
    private static final int AUSM_BLOOM_ONLY_BASE_FALLBACK_LOG_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_BLOOM_ONLY_BASE_FALLBACK_LOGS = new AtomicInteger();

    @Unique
    private static final int AUSM_BLOOM_BASE_ROUTE_PROBE_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_BLOOM_BASE_ROUTE_PROBES = new AtomicInteger();

    @Unique
    private static final int AUSM_BLOOM_VERTEX_PROBE_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_NATIVE_BLOOM_VERTEX_PROBES = new AtomicInteger();

    @Unique
    private static final int AUSM_SHADERLESS_COMPILE_LIGHT_PROBE_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_SHADERLESS_COMPILE_LIGHT_PROBES = new AtomicInteger();

    @Unique
    private static final int AUSM_ENDERIO_GLASS_LAYER_PROBE_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_ENDERIO_GLASS_LAYER_PROBES = new AtomicInteger();

    @Unique
    private static final int AUSM_FRAMED_BLOOM_ROUTE_PROBE_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_FRAMED_BLOOM_ROUTE_PROBES = new AtomicInteger();

    @Unique
    private static final int AUSM_FRAMED_BLOOM_FINAL_PROBE_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_FRAMED_BLOOM_FINAL_PROBES = new AtomicInteger();


    @Shadow(remap = false)
    private IBlockAccess chunkCache;

    @Unique
    private static volatile Field ausm$abstractRenderChunkTaskRenderChunkField;

    @Unique
    private int ausm$fireCutoutFallbackStart = -1;

    @Unique
    private int ausm$bloomOnlyBaseFallbackStart = -1;

    @Unique
    private BlockRenderLayer ausm$bloomOnlyBaseFallbackLayer = null;

    @Unique
    private IBlockState ausm$bloomOnlyBaseFallbackState = null;

    @Unique
    private boolean ausm$bloomBaseRouteProbeTarget = false;

    @Unique
    private String ausm$bloomBaseRouteProbeKind = "";

    @Unique
    private IBlockState ausm$bloomBaseRouteProbeEffectiveState = null;

    @Unique
    private BlockRenderLayer ausm$bloomBaseRouteProbeCurrentLayer = null;

    @Unique
    private BlockRenderLayer ausm$bloomBaseRouteProbeBaseLayer = null;

    @Unique
    private int ausm$bloomBaseRouteProbeCurrentStart = -1;

    @Unique
    private int ausm$bloomBaseRouteProbeBaseStart = -1;

    @Unique
    private int ausm$bloomBaseRouteProbeBloomStart = -1;

    @Unique
    private int ausm$emissiveFallbackStart = -1;

    @Unique
    private int ausm$nativeBloomProbeStart = -1;

    @Unique
    private BlockRenderLayer ausm$nativeBloomProbeLayer = null;

    @Unique
    private boolean ausm$framedBloomRouteProbeTarget = false;

    @Unique
    private IBlockState ausm$framedBloomRouteProbeContainedState = null;

    @Unique
    private BlockRenderLayer ausm$framedBloomRouteProbeCurrentLayer = null;

    @Unique
    private int ausm$framedBloomRouteProbeCurrentStart = -1;

    @Unique
    private int ausm$framedBloomRouteProbeBloomStart = -1;

    @Unique
    private int ausm$framedDiagnosticStart = -1;

    @Unique
    private BlockRenderLayer ausm$framedDiagnosticLayer = null;

    @Unique
    private int ausm$terrainCompileProbeStart = -1;

    @Unique
    private BlockRenderLayer ausm$terrainCompileProbeLayer = null;

    @Inject(
            method = "compileSection(Lnet/minecraft/client/renderer/RegionRenderCacheBuilder;)Lmeldexun/nothirium/api/renderer/chunk/RenderChunkTaskResult;",
            at = @At("HEAD"),
            remap = false
    )
    private void ausm$resetShaderlessBloomLayerSummaries(RegionRenderCacheBuilder regionBuffers, CallbackInfoReturnable<?> cir) {
        ausm$clearThreadCaches();
        PipelineContext.getInstance().beginFramedMaterialCompileCache();
        TerrainCompileCoordinator.beginSection();
        ausm$resetShaderlessBloomMetadata(regionBuffers);
    }

    @Inject(
            method = "compileSection(Lnet/minecraft/client/renderer/RegionRenderCacheBuilder;)Lmeldexun/nothirium/api/renderer/chunk/RenderChunkTaskResult;",
            at = @At("RETURN"),
            remap = false
    )
    private void ausm$recordShaderlessBloomLayerSummaries(RegionRenderCacheBuilder regionBuffers, CallbackInfoReturnable<?> cir) {
        try {
            Object result = cir.getReturnValue();
            if (result != null && !"SUCCESSFUL".equals(String.valueOf(result))) {
                return;
            }
            Object renderChunk = ausm$renderChunk();
            if (!(renderChunk instanceof meldexun.nothirium.mc.renderer.chunk.RenderChunk chunk)) {
                return;
            }
            int x = chunk.getX();
            int y = chunk.getY();
            int z = chunk.getZ();
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                BufferBuilder buffer = regionBuffers != null
                        ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, layer)
                        : null;
                boolean hasBloomMetadata = buffer instanceof IBufferBuilderExtension extension
                        && extension.ausm$hasShaderlessBloomMetadata();
                PipelineContext.getInstance().recordShaderlessBloomLayerSummary(x, y, z, layer, hasBloomMetadata);
            }
        } finally {
            ausm$resetShaderlessBloomMetadata(regionBuffers);
            PipelineContext.getInstance().endFramedMaterialCompileCache();
            TerrainCompileCoordinator.endSection();
            ausm$clearThreadCaches();
        }
    }

    @Inject(
            method = "compileSection(Lnet/minecraft/client/renderer/RegionRenderCacheBuilder;)Lmeldexun/nothirium/api/renderer/chunk/RenderChunkTaskResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/nothirium/api/renderer/chunk/IRenderChunkDispatcher;runOnRenderThread(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;",
                    remap = false
            ),
            remap = false
    )
    private void ausm$probeNothiriumCompileBuffers(RegionRenderCacheBuilder regionBuffers,
                                                   CallbackInfoReturnable<?> cir) {
        int call = TerrainRenderProbeState.nextTerrainCompileBufferProbe();
        if (call < 0) {
            return;
        }
        Object renderChunk = ausm$renderChunk();
        String chunk = "n/a";
        if (renderChunk instanceof meldexun.nothirium.mc.renderer.chunk.RenderChunk nothiriumChunk) {
            chunk = nothiriumChunk.getX() + "," + nothiriumChunk.getY() + "," + nothiriumChunk.getZ();
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumCompileBuffers] call={} thread={} chunk={} currentLayer={} pipelineActive={} forceVanilla={} formatMode={} solid={} cutoutMipped={} cutout={} translucent={} bloom={}",
                call,
                Thread.currentThread().getName(),
                chunk,
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(),
                PipelineContext.getInstance().isPipelineActive(),
                PipelineContext.getInstance().shouldForceVanillaTerrainRenderer(),
                NothiriumPipelineCompat.shouldUsePipelineBlockFormat(),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.SOLID),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.CUTOUT_MIPPED),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.CUTOUT),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.TRANSLUCENT),
                ausm$layerCompileBufferDetails(regionBuffers, AusmBloomLayer.layer())
        );
    }

    @Unique
    private Object ausm$renderChunk() {
        try {
            Field field = ausm$abstractRenderChunkTaskRenderChunkField;
            if (field == null) {
                field = Class.forName("meldexun.nothirium.renderer.chunk.AbstractRenderChunkTask")
                        .getDeclaredField("renderChunk");
                field.setAccessible(true);
                ausm$abstractRenderChunkTaskRenderChunkField = field;
            }
            return field.get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static void ausm$resetShaderlessBloomMetadata(RegionRenderCacheBuilder regionBuffers) {
        if (regionBuffers == null) {
            return;
        }
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, layer);
            if (buffer instanceof IBufferBuilderExtension extension) {
                extension.ausm$resetShaderlessBloomMetadata();
            }
        }
    }

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

    @ModifyArg(
            method = "compileSection(Lnet/minecraft/client/renderer/RegionRenderCacheBuilder;)Lmeldexun/nothirium/api/renderer/chunk/RenderChunkTaskResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;func_181668_a(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V",
                    remap = false
            ),
            index = 1,
            remap = false
    )
    private VertexFormat ausm$usePipelineBlockFormatForSectionBuffers(VertexFormat original) {
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
    private boolean ausm$forceEmissiveFallbackLayer(Block block,
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
            int enderIoProbe = AUSM_ENDERIO_GLASS_LAYER_PROBES.incrementAndGet();
            if (enderIoProbe <= AUSM_ENDERIO_GLASS_LAYER_PROBE_LIMIT) {
                MainMod.LOGGER.info("[AUSMEnderIoGlassLayerProbe] call={} pos={} requestedLayer={} extractedLayer={} declaredLayer={} state={}",
                        enderIoProbe, pos, layer, extractedEnderIoLayer,
                        com.l.ausm.impl.util.MinecraftReflectionCompat.blockRenderLayer(block),
                        ausm$stateName(state));
            }
            return layer == extractedEnderIoLayer;
        }
        if (pipeline.isBlockcrafteryEditableState(state)) {
            IBlockState contained = pipeline.inheritedBlockcrafteryRenderState(state, chunkCache, pos);
            if (contained != null) {
                BlockRenderLayer containedEnderIoLayer = BlockcrafteryContainedStateCompat
                        .enderIoGlassRenderLayer(contained);
                if (containedEnderIoLayer != null && layer != null && !AusmBloomLayer.isBloomLayer(layer)) {
                    return layer == containedEnderIoLayer;
                }
                // Filled frames are the contained block for every terrain
                // layer, including native BLOOM.  No GPOM shape or material
                // route remains in this decision.
                return ausm$canRenderStateInLayer(contained, layer);
            }
            return ausm$canRenderInLayer(block, state, layer);
        }
        if (ausm$canRenderInLayer(block, state, layer)) {
            return true;
        }
        if (BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null
                && PipelineContext.getInstance().isFramedBlockDiagnosticTarget(state)
                && layer != null
                && !AusmBloomLayer.isBloomLayer(layer)) {
            return true;
        }
        return ausm$isEmissiveBloomFallbackTarget(state) && layer == ausm$bloomFallbackLayer(state);
    }

    @Inject(method = "renderBlockState", at = @At("HEAD"), remap = false)
    private void ausm$captureFireCutoutStart(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                             RegionRenderCacheBuilder regionBuffers, CallbackInfo ci) {
        ausm$fireCutoutFallbackStart = -1;
        ausm$bloomOnlyBaseFallbackStart = -1;
        ausm$bloomOnlyBaseFallbackLayer = null;
        ausm$bloomOnlyBaseFallbackState = null;
        ausm$emissiveFallbackStart = -1;
        ausm$nativeBloomProbeStart = -1;
        ausm$nativeBloomProbeLayer = null;
        ausm$resetFramedBloomRouteProbe();
        ausm$resetBloomBaseRouteProbe();
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState effectiveState = pipeline.effectiveBlockRenderState(state, chunkCache, pos);
        ausm$beginFramedBloomRouteProbe(state, pos, regionBuffers, pipeline);
        ausm$beginBloomBaseRouteProbe(state, effectiveState, pos, regionBuffers, pipeline);
        if (pipeline.shouldForceVanillaTerrainRenderer()) {
            return;
        }
        IBlockState inheritedBloomState = pipeline.inheritedBloomRenderState(state, chunkCache, pos);
        BlockRenderLayer currentLayer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
        if (ausm$isRandomThingsLuminousState(state) && ausm$isNativeBloomOverlayLayer(currentLayer)
                && regionBuffers != null) {
            // Quantum Things emits a SOLID base and a separate translucent
            // _t overlay. Only the latter is the native bloom source that a
            // framed material must reproduce; recording the base exhausted
            // the bounded diagnostic budget before that comparison existed.
            BufferBuilder nativeBloomBuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(
                    regionBuffers, currentLayer);
            ausm$nativeBloomProbeLayer = currentLayer;
            ausm$nativeBloomProbeStart = nativeBloomBuffer != null
                    ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(nativeBloomBuffer)
                    : -1;
        }
        IBlockState bloomOnlyState = ausm$isNativeBloomOnlyBlock(effectiveState)
                ? effectiveState
                : ausm$isNativeBloomOnlyBlock(state) ? state : null;
        if (bloomOnlyState != null && !pipeline.isBlockcrafteryEditableState(state) && regionBuffers != null) {
            BlockRenderLayer baseLayer = ausm$bloomFallbackLayer(bloomOnlyState);
            BufferBuilder baseBuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(
                    regionBuffers, baseLayer);
            if (baseBuffer != null) {
                ausm$bloomOnlyBaseFallbackState = bloomOnlyState;
                ausm$bloomOnlyBaseFallbackLayer = baseLayer;
                ausm$bloomOnlyBaseFallbackStart = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(baseBuffer);
                ausm$bloomBaseRouteProbeBaseLayer = baseLayer;
                ausm$bloomBaseRouteProbeBaseStart = ausm$bloomOnlyBaseFallbackStart;
            }
        }
        boolean originalFire = ausm$isFireFallbackTarget(state);
        boolean effectiveFire = ausm$isFireFallbackTarget(effectiveState);
        BufferBuilder buffer = regionBuffers != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, BlockRenderLayer.CUTOUT) : null;
        boolean framedState = pipeline.isFramedBlockDiagnosticTarget(state);
        boolean forcedFramedBloom = false;
        IBlockState emissiveState = ausm$isEmissiveBloomFallbackTarget(inheritedBloomState)
                ? inheritedBloomState
                : forcedFramedBloom ? state : framedState ? null : ausm$isEmissiveBloomFallbackTarget(effectiveState) ? effectiveState : state;
        boolean emissiveTarget = forcedFramedBloom || ausm$isEmissiveBloomFallbackTarget(emissiveState);
        if (emissiveTarget && regionBuffers != null) {
            IBlockState fallbackRenderState = pipeline.inheritedBloomGeometryRenderState(state, emissiveState);
            BlockRenderLayer emissiveLayer = framedState
                    ? ausm$framedGeometryLayer(fallbackRenderState, emissiveState)
                    : ausm$bloomFallbackLayer(emissiveState);
            BufferBuilder emissiveBuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, emissiveLayer);
            if (emissiveBuffer != null) {
                ausm$emissiveFallbackStart = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(emissiveBuffer);
            }
        }
        if (!effectiveFire || buffer == null) {
            return;
        }

        ausm$fireCutoutFallbackStart = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer);
        if (ausm$bloomBaseRouteProbeTarget && ausm$bloomBaseRouteProbeBaseStart < 0) {
            ausm$bloomBaseRouteProbeBaseLayer = BlockRenderLayer.CUTOUT;
            ausm$bloomBaseRouteProbeBaseStart = ausm$fireCutoutFallbackStart;
        }
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
        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        BlockRenderLayer naturalLayer = block != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockRenderLayer(block)
                : null;
        if (naturalLayer != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer() == null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(naturalLayer);
        }
        ausm$captureFramedBloomRouteLayer(bufferBuilder);
        // The render-layer thread local is initialized immediately above on
        // Nothirium's first pass.  Capturing only at renderBlockState HEAD
        // therefore missed native luminous blocks on that pass and left the
        // framed/native comparison without its source data.
        if (ausm$nativeBloomProbeStart < 0 && ausm$isRandomThingsLuminousState(state)
                && bufferBuilder != null) {
            BlockRenderLayer nativeLayer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
            BufferBuilder nativeBuffer = ausm$isNativeBloomOverlayLayer(nativeLayer)
                    ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, nativeLayer)
                    : null;
            if (nativeBuffer != null) {
                ausm$nativeBloomProbeLayer = nativeLayer;
                ausm$nativeBloomProbeStart =
                        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(nativeBuffer);
            }
        }
        PipelineContext pipeline = PipelineContext.getInstance();
        // Nothirium bypasses BlockRendererDispatcher's context hook. Set the
        // native BLOOM marker before the diagnostics-only fast return so every
        // ordinary luminous block reaches Entree's coat exclusion path.
        BlockRenderContext.setFramedBloomBoost(pipeline.stateHasBloomLayerGeometry(state));
        ausm$terrainCompileProbeLayer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
        BufferBuilder terrainProbeBuffer = ausm$terrainCompileProbeLayer != null && bufferBuilder != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, ausm$terrainCompileProbeLayer)
                : null;
        ausm$terrainCompileProbeStart = terrainProbeBuffer != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(terrainProbeBuffer)
                : -1;
        ausm$framedDiagnosticStart = -1;
        ausm$framedDiagnosticLayer = null;
        if (!pipeline.currentProblemProbesEnabled()
                && !pipeline.isFramedBlockDiagnosticTarget(state)
                && !pipeline.shouldProbeBlockcrafteryTransparency(state, chunkCache, pos)) {
            return;
        }
        if (pipeline.shouldForceVanillaTerrainRenderer()) {
            BlockRenderContext.configureBlock(
                    0,
                    (short) com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderTypeOrdinal(state),
                    0,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos),
                    chunkCache,
                    pos,
                    pipeline.isBlockcrafteryEditableState(state)
                            && !pipeline.shouldReplaceFilledBlockcrafteryFrame(state, chunkCache, pos),
                    false, 0, 0, false, -1, -1, false, false);
            return;
        }
        IBlockState actualState = pipeline.actualBlockRenderState(state, chunkCache, pos);
        IBlockState contextState = pipeline.effectiveBlockRenderState(state, actualState, chunkCache, pos);
        if (contextState == null) {
            contextState = state;
        }

        int blockEntityId = pipeline.blockEntityIdForActualState(actualState, chunkCache, pos);
        int packedLightmap = ausm$packedLightmap(contextState, chunkCache, pos);
        int blockEmission = pipeline.shouldUseShaderlessBloomEmission()
                ? pipeline.blockShaderlessBloomEmission(state, chunkCache, pos)
                : pipeline.blockRenderEmission(state, chunkCache, pos);
        // Nothirium builds terrain without BlockRendererDispatcher's context
        // hook.  Preserve the native BLOOM marker here as well so the shader
        // can reject coated-texture treatment before material resolution.
        BlockRenderContext.configureBlock(
                blockEntityId,
                (short) com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderTypeOrdinal(contextState),
                pipeline.blockMetadataForActualState(actualState),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos),
                chunkCache,
                pos,
                pipeline.isBlockcrafteryEditableState(state)
                        && !pipeline.shouldReplaceFilledBlockcrafteryFrame(state, chunkCache, pos),
                ausm$isAgricraftCropState(contextState),
                packedLightmap,
                blockEmission,
                pipeline.stateHasBloomLayerGeometry(contextState),
                pipeline.blockRenderAlpha(state, chunkCache, pos),
                pipeline.customLiquidTintColor(state, chunkCache, pos),
                pipeline.shouldUseCrystalOnlyEmission(actualState),
                pipeline.shouldSeparateBlockAo(contextState));
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.setBlockRenderDebugContext(state, chunkCache, pos);
        }
        pipeline.recordSyntheticLightCandidate(contextState, chunkCache, pos);

        if (pipeline.isFramedBlockDiagnosticTarget(state) && bufferBuilder != null) {
            ausm$framedDiagnosticLayer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
            BufferBuilder layerBuffer = ausm$framedDiagnosticLayer != null
                    ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, ausm$framedDiagnosticLayer)
                    : null;
            ausm$framedDiagnosticStart = layerBuffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : -1;
        }
        if (pipeline.shouldProbeBlockcrafteryTransparency(state, chunkCache, pos)) {
            BlockRenderLayer layer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
            BufferBuilder layerBuffer = layer != null && bufferBuilder != null
                    ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, layer)
                    : null;
            pipeline.logBlockcrafteryTransparencyProbe(
                    "nothirium-head",
                    state,
                    chunkCache,
                    pos,
                    layer,
                    layerBuffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : null,
                    layerBuffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : null,
                    null,
                    "context=" + pipeline.diagnosticStateName(contextState)
                            + ", blockAlpha=" + BlockRenderContext.blockAlpha()
                            + ", layerBuffer=" + ausm$bufferDetails(layerBuffer)
            );
        }
        if (pipeline.currentProblemProbesEnabled()
                && (pipeline.isCurrentProblemProbeTarget(state)
                || pipeline.isCurrentProblemProbeTarget(contextState)
                || blockEmission > 0
                || blockEntityId != 0)) {
            BufferBuilder layerBuffer = bufferBuilder != null && com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer() != null
                    ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer())
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
        if (state == null || ausm$block(state) == null) {
            return false;
        }
        ResourceLocation name = ausm$registryName(state);
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
            return com.l.ausm.impl.util.MinecraftReflectionCompat.statePackedLightmapCoords(state, blockAccess, pos);
        } catch (RuntimeException ignored) {
            return 0;
        }
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
                    : com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
            BufferBuilder layerBuffer = layer != null && bufferBuilder != null
                    ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(bufferBuilder, layer)
                    : null;
            if (pipeline.framedBlockDiagnosticsEnabled()) {
                pipeline.logFramedBlockDiagnostic(
                        "nothirium-dispatcher",
                        state,
                        chunkCache,
                        pos,
                        layer,
                        ausm$framedDiagnosticStart,
                        layerBuffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : -1,
                        null,
                        "buffer=" + (layerBuffer != null ? Integer.toHexString(System.identityHashCode(layerBuffer)) : "null")
                );
            }
            if (pipeline.currentProblemProbesEnabled()) {
                pipeline.logCurrentProblemProbe("nothirium-return", state, chunkCache, pos,
                        "layer=" + layer
                                + ", start=" + ausm$framedDiagnosticStart
                                + ", end=" + (layerBuffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : -1)
                                + ", buffer=" + ausm$bufferDetails(layerBuffer));
            }
            if (pipeline.shouldProbeBlockcrafteryTransparency(state, chunkCache, pos)) {
                pipeline.logBlockcrafteryTransparencyProbe(
                        "nothirium-return",
                        state,
                        chunkCache,
                        pos,
                        layer,
                        ausm$framedDiagnosticStart >= 0 ? ausm$framedDiagnosticStart : null,
                        layerBuffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(layerBuffer) : null,
                        null,
                        "buffer=" + ausm$bufferDetails(layerBuffer)
                );
            }
        }
        ausm$logNativeBloomVertexProbe(state, pos, bufferBuilder);
        ausm$logTerrainCompileBlockProbe(state, pos, bufferBuilder, ci.isCancelled());
        ausm$framedDiagnosticStart = -1;
        ausm$framedDiagnosticLayer = null;
        ausm$terrainCompileProbeStart = -1;
        ausm$terrainCompileProbeLayer = null;
        BlockRenderContext.clear();
    }

    @Unique
    private void ausm$logTerrainCompileBlockProbe(IBlockState state, BlockPos pos, RegionRenderCacheBuilder regionBuffers,
                                                  boolean cancelled) {
        int call = TerrainRenderProbeState.nextTerrainCompileBlockProbe();
        if (call < 0) {
            return;
        }
        BlockRenderLayer layer = ausm$terrainCompileProbeLayer;
        BufferBuilder buffer = layer != null && regionBuffers != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, layer)
                : null;
        int end = buffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer) : -1;
        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        MainMod.LOGGER.info(
                "[AUSMNothiriumCompileBlock] call={} thread={} pos={} layer={} state={} block={} start={} end={} delta={} cancelled={} buffer={} drawing={} format={} contextBlockId={} contextEmission={} packedLight=0x{} pipelineActive={} forceVanilla={}",
                call,
                Thread.currentThread().getName(),
                pos,
                layer,
                PipelineContext.getInstance().diagnosticStateName(state),
                block != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(block) : null,
                ausm$terrainCompileProbeStart,
                end,
                ausm$terrainCompileProbeStart >= 0 && end >= 0 ? end - ausm$terrainCompileProbeStart : -1,
                cancelled,
                ausm$bufferDetails(buffer),
                buffer instanceof IBufferBuilderExtension extension && extension.ausm$isDrawing(),
                buffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexFormat(buffer) : null,
                BlockRenderContext.blockEntityId(),
                BlockRenderContext.blockEmission(),
                Integer.toHexString(BlockRenderContext.packedLightmap()),
                PipelineContext.getInstance().isPipelineActive(),
                PipelineContext.getInstance().shouldForceVanillaTerrainRenderer()
        );
    }

    @Inject(method = "renderBlockState", at = @At("RETURN"), remap = false)
    private void ausm$renderBloomOnlyFallback(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                              RegionRenderCacheBuilder regionBuffers, CallbackInfo ci) {
        PipelineContext pipeline = PipelineContext.getInstance();
        String route = "none";
        try {
            if (pipeline.shouldReplaceFilledBlockcrafteryFrame(state, chunkCache, pos)) {
                route = "contained-block-native";
                return;
            }
            IBlockState effectiveState = pipeline.effectiveBlockRenderState(state, chunkCache, pos);
            IBlockState inheritedBloomState = pipeline.inheritedBloomRenderState(state, chunkCache, pos);
            if (ausm$renderMissingBloomOnlyBaseFallback(state, pos, regionBuffers)) {
                route = "bloom-only-base";
                return;
            }
            if (ausm$renderMissingFireCutoutFallback(state, effectiveState, pos, regionBuffers)) {
                route = "fire-cutout";
                return;
            }
            if (!pipeline.isManualBloomExtractionEnabled()) {
                route = "skip-stacked-manual-disabled";
                return;
            }
            IBlockState fallbackTarget = inheritedBloomState != null ? inheritedBloomState : effectiveState;
            if (ausm$renderStackedEmissiveBloomLayer(state, fallbackTarget, pos, regionBuffers)) {
                route = "stacked-emissive";
                return;
            }
        } finally {
            ausm$logFramedBloomRouteProbe(state, pos, regionBuffers, pipeline, route);
            ausm$resetFramedBloomRouteProbe();
            ausm$logBloomBaseRouteProbe(route, state, pos, regionBuffers, pipeline);
            ausm$resetBloomBaseRouteProbe();
        }
    }

    @Unique
    private void ausm$beginFramedBloomRouteProbe(IBlockState state, BlockPos pos,
                                                   RegionRenderCacheBuilder regionBuffers, PipelineContext pipeline) {
        if (regionBuffers == null || !pipeline.isBlockcrafteryEditableState(state)) {
            return;
        }
        IBlockState contained = pipeline.inheritedBlockcrafteryRenderState(state, chunkCache, pos);
        // Shadered Bloom does not use the shaderless extractor, so its source
        // predicate is intentionally false while the actual framed source is
        // active.  Ask the shared framed decision instead; it covers native
        // Bloom-layer geometry and contained emission alike.
        if (contained == null || !pipeline.hasContainedFrameBloom(state, chunkCache, pos)) {
            return;
        }
        ausm$framedBloomRouteProbeTarget = true;
        ausm$framedBloomRouteProbeContainedState = contained;
        ausm$framedBloomRouteProbeBloomStart = ausm$layerVertexCount(regionBuffers, AusmBloomLayer.layer());
    }

    @Unique
    private void ausm$captureFramedBloomRouteLayer(RegionRenderCacheBuilder regionBuffers) {
        if (!ausm$framedBloomRouteProbeTarget || ausm$framedBloomRouteProbeCurrentStart >= 0 || regionBuffers == null) {
            return;
        }
        BlockRenderLayer layer = MinecraftReflectionCompat.currentRenderLayer();
        ausm$framedBloomRouteProbeCurrentLayer = layer;
        ausm$framedBloomRouteProbeCurrentStart = ausm$layerVertexCount(regionBuffers, layer);
    }

    @Unique
    private void ausm$logFramedBloomRouteProbe(IBlockState state, BlockPos pos, RegionRenderCacheBuilder regionBuffers,
                                                PipelineContext pipeline, String route) {
        if (!ausm$framedBloomRouteProbeTarget) {
            return;
        }
        int call = AUSM_FRAMED_BLOOM_ROUTE_PROBES.incrementAndGet();
        if (call > AUSM_FRAMED_BLOOM_ROUTE_PROBE_LIMIT) {
            return;
        }
        IBlockState contained = ausm$framedBloomRouteProbeContainedState;
        BlockRenderLayer current = ausm$framedBloomRouteProbeCurrentLayer;
        BlockRenderLayer bloom = AusmBloomLayer.layer();
        int currentEnd = ausm$layerVertexCount(regionBuffers, current);
        int bloomEnd = ausm$layerVertexCount(regionBuffers, bloom);
        MainMod.LOGGER.info(
                "[AUSMFramedBloomRouteProbe] call={} pos={} route={} currentLayer={} bloomLayer={} currentDelta={} bloomDelta={} containedCurrent={} containedBloom={} replace={} manual={} host={} contained={}",
                call,
                pos,
                route,
                current,
                bloom,
                ausm$delta(ausm$framedBloomRouteProbeCurrentStart, currentEnd),
                ausm$delta(ausm$framedBloomRouteProbeBloomStart, bloomEnd),
                ausm$canRenderStateInLayer(contained, current),
                ausm$canRenderStateInLayer(contained, bloom),
                pipeline.shouldReplaceFilledBlockcrafteryFrame(state, chunkCache, pos),
                pipeline.isManualBloomExtractionEnabled(),
                ausm$stateName(state),
                ausm$stateName(contained));
        ausm$logFramedBloomFinalCompileProbe(pos, regionBuffers, bloom,
                ausm$framedBloomRouteProbeBloomStart, bloomEnd);
    }

    @Unique
    private static void ausm$logFramedBloomFinalCompileProbe(BlockPos pos, RegionRenderCacheBuilder regionBuffers,
                                                               BlockRenderLayer bloomLayer, int start, int end) {
        if (regionBuffers == null || start < 0 || end - start < 4
                || AUSM_FRAMED_BLOOM_FINAL_PROBES.get() >= AUSM_FRAMED_BLOOM_FINAL_PROBE_LIMIT) {
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
        int call = AUSM_FRAMED_BLOOM_FINAL_PROBES.incrementAndGet();
        if (markerVertices == 0) {
            MainMod.LOGGER.warn("[AUSMFramedBloomFinalProbe] call={} pos={} start={} end={} markerVertices=0 expectedMarker={}",
                    call, pos, start, end, BlockRenderContext.FRAMED_BLOOM_OVERLAY_PROBE_MARKER);
            return;
        }
        MainMod.LOGGER.info("[AUSMFramedBloomFinalProbe] call={} pos={} start={} end={} markerVertices={} firstMarker={} quad={}",
                call, pos, start, end, markerVertices, firstMarker,
                ausm$describePipelineQuad(bytes, firstMarker * stride, stride));
    }

    @Unique
    private void ausm$resetFramedBloomRouteProbe() {
        ausm$framedBloomRouteProbeTarget = false;
        ausm$framedBloomRouteProbeContainedState = null;
        ausm$framedBloomRouteProbeCurrentLayer = null;
        ausm$framedBloomRouteProbeCurrentStart = -1;
        ausm$framedBloomRouteProbeBloomStart = -1;
    }

    @Unique
    private static boolean ausm$isRandomThingsLuminousState(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        ResourceLocation registryName = MinecraftReflectionCompat.blockRegistryName(block);
        return registryName != null
                && "randomthings".equals(MinecraftReflectionCompat.resourceNamespace(registryName))
                && "luminousblock".equals(MinecraftReflectionCompat.resourcePath(registryName));
    }

    @Unique
    private static boolean ausm$isNativeBloomOverlayLayer(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.TRANSLUCENT || AusmBloomLayer.isBloomLayer(layer);
    }

    @Unique
    private void ausm$logNativeBloomVertexProbe(
            IBlockState state,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers
    ) {
        if (ausm$nativeBloomProbeStart < 0 || !ausm$isRandomThingsLuminousState(state)
                || !ausm$isNativeBloomOverlayLayer(ausm$nativeBloomProbeLayer)
                || AUSM_NATIVE_BLOOM_VERTEX_PROBES.get() >= AUSM_BLOOM_VERTEX_PROBE_LIMIT
                || regionBuffers == null) {
            return;
        }
        BlockRenderLayer layer = ausm$nativeBloomProbeLayer;
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
        if (end - ausm$nativeBloomProbeStart < 4
                || !ExtendedVertexFormats.isPipelineBlock(format)
                || stride <= 0
                || raw == null) {
            return;
        }
        int call = AUSM_NATIVE_BLOOM_VERTEX_PROBES.incrementAndGet();
        if (call <= AUSM_BLOOM_VERTEX_PROBE_LIMIT) {
            MainMod.LOGGER.info(
                "[AUSMBloomVertexProbe] kind=native call={} pos={} layer={} bloomLayer={} start={} end={} quad={}",
                call,
                pos,
                layer,
                AusmBloomLayer.layer(),
                ausm$nativeBloomProbeStart,
                    end,
                    ausm$describePipelineQuad(raw, ausm$nativeBloomProbeStart * stride, stride));
        }
    }

    @Unique
    private static String ausm$describePipelineQuad(ByteBuffer raw, int byteStart, int stride) {
        ByteBuffer bytes = raw.duplicate().order(raw.order() != null ? raw.order() : ByteOrder.nativeOrder());
        StringBuilder result = new StringBuilder();
        for (int vertex = 0; vertex < 4; vertex++) {
            int base = byteStart + vertex * stride;
            if (vertex > 0) {
                result.append(';');
            }
            result.append(ausm$describePipelineVertex(
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

    @Unique
    private static String ausm$describePipelineVertex(
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

    @Unique
    private void ausm$beginBloomBaseRouteProbe(
            IBlockState state,
            IBlockState effectiveState,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers,
            PipelineContext pipeline
    ) {
        if (AUSM_BLOOM_BASE_ROUTE_PROBES.get() >= AUSM_BLOOM_BASE_ROUTE_PROBE_LIMIT) {
            return;
        }
        boolean fire = ausm$isFireFallbackTarget(state) || ausm$isFireFallbackTarget(effectiveState);
        boolean twilightPortal = pipeline.isCeleritasTwilightPortalState(state)
                || pipeline.isCeleritasTwilightPortalState(effectiveState);
        if (!fire && !twilightPortal) {
            return;
        }

        ausm$bloomBaseRouteProbeTarget = true;
        ausm$bloomBaseRouteProbeKind = fire && twilightPortal
                ? "fire+twilight-portal" : fire ? "fire" : "twilight-portal";
        ausm$bloomBaseRouteProbeEffectiveState = effectiveState;
        ausm$bloomBaseRouteProbeCurrentLayer =
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
        ausm$bloomBaseRouteProbeCurrentStart = ausm$layerVertexCount(
                regionBuffers, ausm$bloomBaseRouteProbeCurrentLayer);
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        ausm$bloomBaseRouteProbeBloomStart = ausm$layerVertexCount(regionBuffers, bloomLayer);

        IBlockState candidate = effectiveState != null ? effectiveState : state;
        ausm$bloomBaseRouteProbeBaseLayer = fire
                ? BlockRenderLayer.CUTOUT : ausm$bloomFallbackLayer(candidate);
        ausm$bloomBaseRouteProbeBaseStart = ausm$layerVertexCount(
                regionBuffers, ausm$bloomBaseRouteProbeBaseLayer);
    }

    @Unique
    private void ausm$logBloomBaseRouteProbe(
            String route,
            IBlockState state,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers,
            PipelineContext pipeline
    ) {
        if (!ausm$bloomBaseRouteProbeTarget) {
            return;
        }
        int probe = AUSM_BLOOM_BASE_ROUTE_PROBES.incrementAndGet();
        if (probe > AUSM_BLOOM_BASE_ROUTE_PROBE_LIMIT) {
            return;
        }

        IBlockState effectiveState = ausm$bloomBaseRouteProbeEffectiveState;
        BlockRenderLayer currentLayer = ausm$bloomBaseRouteProbeCurrentLayer;
        BlockRenderLayer baseLayer = ausm$bloomBaseRouteProbeBaseLayer;
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        int currentEnd = ausm$layerVertexCount(regionBuffers, currentLayer);
        int baseEnd = ausm$layerVertexCount(regionBuffers, baseLayer);
        int bloomEnd = ausm$layerVertexCount(regionBuffers, bloomLayer);
        MainMod.LOGGER.info(
                "[AUSMBloomBaseRouteProbe] call={} thread={} kind={} route={} pos={} currentLayer={} baseLayer={} bloomLayer={} currentStart={} currentEnd={} currentDelta={} baseStart={} baseEnd={} baseDelta={} bloomStart={} bloomEnd={} bloomDelta={} manualExtraction={} pipelineActive={} forceVanilla={} original={} effective={} originalRenderType={} effectiveRenderType={} originalNatural={} effectiveNatural={} originalNativeBloomOnly={} effectiveNativeBloomOnly={} originalLayers={}/{}/{}/{}/{} effectiveLayers={}/{}/{}/{}/{} solidBuffer={} cutoutMippedBuffer={} cutoutBuffer={} translucentBuffer={} bloomBuffer={}",
                probe,
                Thread.currentThread().getName(),
                ausm$bloomBaseRouteProbeKind,
                route,
                pos,
                currentLayer,
                baseLayer,
                bloomLayer,
                ausm$bloomBaseRouteProbeCurrentStart,
                currentEnd,
                ausm$delta(ausm$bloomBaseRouteProbeCurrentStart, currentEnd),
                ausm$bloomBaseRouteProbeBaseStart,
                baseEnd,
                ausm$delta(ausm$bloomBaseRouteProbeBaseStart, baseEnd),
                ausm$bloomBaseRouteProbeBloomStart,
                bloomEnd,
                ausm$delta(ausm$bloomBaseRouteProbeBloomStart, bloomEnd),
                pipeline.isManualBloomExtractionEnabled(),
                pipeline.isPipelineActive(),
                pipeline.shouldForceVanillaTerrainRenderer(),
                ausm$stateName(state),
                ausm$stateName(effectiveState),
                com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderType(state),
                com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderType(effectiveState),
                ausm$naturalRenderLayer(state),
                ausm$naturalRenderLayer(effectiveState),
                ausm$isNativeBloomOnlyBlock(state),
                ausm$isNativeBloomOnlyBlock(effectiveState),
                ausm$canRenderStateInLayer(state, BlockRenderLayer.SOLID),
                ausm$canRenderStateInLayer(state, BlockRenderLayer.CUTOUT_MIPPED),
                ausm$canRenderStateInLayer(state, BlockRenderLayer.CUTOUT),
                ausm$canRenderStateInLayer(state, BlockRenderLayer.TRANSLUCENT),
                ausm$canRenderStateInLayer(state, bloomLayer),
                ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.SOLID),
                ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.CUTOUT_MIPPED),
                ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.CUTOUT),
                ausm$canRenderStateInLayer(effectiveState, BlockRenderLayer.TRANSLUCENT),
                ausm$canRenderStateInLayer(effectiveState, bloomLayer),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.SOLID),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.CUTOUT_MIPPED),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.CUTOUT),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.TRANSLUCENT),
                ausm$layerCompileBufferDetails(regionBuffers, bloomLayer)
        );
    }

    @Unique
    private static int ausm$layerVertexCount(RegionRenderCacheBuilder regionBuffers, BlockRenderLayer layer) {
        if (regionBuffers == null || layer == null) {
            return -1;
        }
        BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(
                regionBuffers, layer);
        return buffer != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer) : -1;
    }

    @Unique
    private static int ausm$delta(int start, int end) {
        return start >= 0 && end >= 0 ? end - start : -1;
    }

    @Unique
    private void ausm$resetBloomBaseRouteProbe() {
        ausm$bloomBaseRouteProbeTarget = false;
        ausm$bloomBaseRouteProbeKind = "";
        ausm$bloomBaseRouteProbeEffectiveState = null;
        ausm$bloomBaseRouteProbeCurrentLayer = null;
        ausm$bloomBaseRouteProbeBaseLayer = null;
        ausm$bloomBaseRouteProbeCurrentStart = -1;
        ausm$bloomBaseRouteProbeBaseStart = -1;
        ausm$bloomBaseRouteProbeBloomStart = -1;
    }

    @Unique
    private boolean ausm$renderMissingBloomOnlyBaseFallback(
            IBlockState originalState,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers
    ) {
        IBlockState fallbackState = ausm$bloomOnlyBaseFallbackState;
        BlockRenderLayer baseLayer = ausm$bloomOnlyBaseFallbackLayer;
        int baseStart = ausm$bloomOnlyBaseFallbackStart;
        try {
            if (fallbackState == null || baseLayer == null || baseStart < 0
                    || pos == null || regionBuffers == null) {
                return false;
            }

            BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(
                    regionBuffers, baseLayer);
            if (buffer == null) {
                return false;
            }
            int normalDelta = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer) - baseStart;
            if (normalDelta > 0) {
                ausm$logBloomOnlyBaseFallback("base-present", originalState, fallbackState, pos,
                        baseLayer, normalDelta, false, 0);
                return true;
            }

            if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.bufferBegin(buffer, 7,
                        NothiriumPipelineCompat.pipelineBlockFormat(
                                com.l.ausm.impl.util.MinecraftReflectionCompat.blockFormat()));
                int originX = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos), 16) * 16;
                int originY = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos), 16) * 16;
                int originZ = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos), 16) * 16;
                com.l.ausm.impl.util.MinecraftReflectionCompat.bufferSetTranslation(
                        buffer, -originX, -originY, -originZ);
            }

            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            if (bloomLayer == null) {
                return false;
            }
            BlockRenderLayer previousLayer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
            int fallbackStart = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer);
            boolean rendered;
            try {
                // CTM's layer=BLOOM removes these quads from the default pass.
                // Keep the bloom mesh and copy the same model into its normal
                // terrain buffer so bloom overlays scene geometry.
                com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(bloomLayer);
                BlockRendererDispatcher dispatcher = com.l.ausm.impl.util.MinecraftReflectionCompat
                        .blockRendererDispatcher(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
                rendered = dispatcher != null && com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlock(
                        dispatcher, fallbackState, pos, chunkCache, buffer);
            } finally {
                com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
            }
            int fallbackDelta = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer) - fallbackStart;
            ausm$logBloomOnlyBaseFallback("stacked", originalState, fallbackState, pos,
                    baseLayer, normalDelta, rendered, fallbackDelta);
            return fallbackDelta > 0;
        } finally {
            ausm$bloomOnlyBaseFallbackStart = -1;
            ausm$bloomOnlyBaseFallbackLayer = null;
            ausm$bloomOnlyBaseFallbackState = null;
        }
    }

    @Unique
    private static void ausm$logBloomOnlyBaseFallback(
            String mode,
            IBlockState originalState,
            IBlockState fallbackState,
            BlockPos pos,
            BlockRenderLayer baseLayer,
            int normalDelta,
            boolean rendered,
            int fallbackDelta
    ) {
        int index = AUSM_BLOOM_ONLY_BASE_FALLBACK_LOGS.incrementAndGet();
        if (index > AUSM_BLOOM_ONLY_BASE_FALLBACK_LOG_LIMIT) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMBloomOnlyBaseFallback] mode={} pos={} original={} fallback={} baseLayer={} normalDelta={} rendered={} fallbackDelta={}",
                mode,
                pos,
                ausm$stateName(originalState),
                ausm$stateName(fallbackState),
                baseLayer,
                normalDelta,
                rendered,
                fallbackDelta
        );
    }

    @Unique
    private boolean ausm$renderStackedEmissiveBloomLayer(
            IBlockState renderState,
            IBlockState fallbackTarget,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers
    ) {
        if (PipelineContext.getInstance().isFramedBlockDiagnosticTarget(renderState)) {
            return false;
        }
        IBlockState fallbackSourceState = ausm$isEmissiveBloomFallbackSource(fallbackTarget)
                ? fallbackTarget
                : renderState;
        boolean emissiveTarget = ausm$isEmissiveBloomFallbackSource(fallbackSourceState);
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

            boolean textureBloomSource = PipelineContext.getInstance().stateUsesTextureBloomSource(fallbackSourceState);
            boolean solidBloomMaskFallback = false;
            IBlockState fallbackGeometryState = fallbackRenderState;
            BlockRenderLayer renderLayer = ausm$bloomFallbackLayer(fallbackSourceState);
            BlockRenderLayer bufferLayer = renderLayer;
            BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, bufferLayer);
            if (buffer == null) {
                ausm$logEmissiveFallback("skip-missing-buffer", renderState, fallbackRenderState,
                        pos, fallbackSourceState, bufferLayer, null, -1, false, 0, null, regionBuffers);
                return false;
            }

            int start = ausm$emissiveFallbackStart;
            int normalDelta = start >= 0 ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer) - start : 0;
            if (normalDelta > 0 && solidBloomMaskFallback) {
                ((IBufferBuilderExtension) buffer).ausm$truncateVertexCount(start);
                ausm$logEmissiveFallback("replace-normal-geometry", renderState, fallbackRenderState, pos,
                        fallbackSourceState, bufferLayer, renderLayer, normalDelta, false, 0, buffer, regionBuffers);
                normalDelta = 0;
            } else if (normalDelta > 0) {
                ausm$markShaderlessBloomMetadata(buffer, bufferLayer, pos);
                ausm$logEmissiveFallback("normal-or-dispatcher-present", renderState, fallbackRenderState, pos,
                        fallbackSourceState, bufferLayer, renderLayer, normalDelta, false, 0, buffer, regionBuffers);
            }

            if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.bufferBegin(buffer, 7, NothiriumPipelineCompat.pipelineBlockFormat(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFormat()));
                int originX = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos), 16) * 16;
                int originY = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos), 16) * 16;
                int originZ = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos), 16) * 16;
                com.l.ausm.impl.util.MinecraftReflectionCompat.bufferSetTranslation(buffer, -originX, -originY, -originZ);
            }

            int fallbackStart = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer);
            boolean rendered = ausm$renderEmissiveFallbackWithLayer(fallbackGeometryState, fallbackSourceState, pos, buffer, renderLayer, solidBloomMaskFallback);
            int fallbackDelta = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer) - fallbackStart;
            if (fallbackDelta > 0) {
                ausm$markShaderlessBloomMetadata(buffer, bufferLayer, pos);
            }
            String mode = normalDelta > 0 ? "stacked-bloom-layer" : "fallback-bloom-layer";
            ausm$logEmissiveFallback(mode, renderState, fallbackGeometryState, pos,
                    fallbackSourceState, bufferLayer, renderLayer, normalDelta, rendered, fallbackDelta, buffer,
                    regionBuffers);
            return true;
        } finally {
            ausm$emissiveFallbackStart = -1;
        }
    }

    @Unique
    private static void ausm$markShaderlessBloomMetadata(BufferBuilder buffer, BlockRenderLayer layer, BlockPos pos) {
        if (buffer instanceof IBufferBuilderExtension extension) {
            extension.ausm$markShaderlessBloomMetadata();
        }
        PipelineContext.getInstance().recordShaderlessBloomMetadata(pos, layer);
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
    private boolean ausm$renderEmissiveFallbackWithLayer(IBlockState state, IBlockState maskColorState, BlockPos pos, BufferBuilder buffer,
                                                        BlockRenderLayer layer, boolean bloomMaskFallback) {
        BlockRenderLayer previousLayer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
        try {
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.set(Boolean.TRUE);
            if (bloomMaskFallback) {
                BlockRenderContext.setBloomMaskFallback(true);
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(layer);
            BlockRendererDispatcher dispatcher = com.l.ausm.impl.util.MinecraftReflectionCompat.blockRendererDispatcher(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
            return dispatcher != null && com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlock(dispatcher, state, pos, chunkCache, buffer);
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
            BlockRenderContext.clearBloomMaskFallback();
            BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.remove();
        }
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
        VertexFormat format = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexFormat(buffer);
        return Integer.toHexString(System.identityHashCode(buffer))
                + "{vertices=" + com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer)
                + ", drawing=" + ((IBufferBuilderExtension) buffer).ausm$isDrawing()
                + ", format=" + format
                + ", pipeline=" + ExtendedVertexFormats.isPipelineBlock(format)
                + ", stride=" + (format != null ? ExtendedVertexFormats.size(format) : -1)
                + "}";
    }

    @Unique
    private static String ausm$layerCompileBufferDetails(RegionRenderCacheBuilder regionBuffers, BlockRenderLayer layer) {
        BufferBuilder buffer = regionBuffers != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, layer)
                : null;
        return ausm$bufferDetails(buffer);
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

            BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, BlockRenderLayer.CUTOUT);
            if (buffer == null) {
                return true;
            }

            int start = ausm$fireCutoutFallbackStart;
            int normalDelta = start >= 0 ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer) - start : -1;
            IBlockState fallbackRenderState = ausm$isFireFallbackTarget(fallbackTarget) ? fallbackTarget : renderState;
            if (normalDelta > 0) {
                ausm$logFireFallback("normal-present", renderState, fallbackRenderState, pos, normalDelta, false, 0);
                return true;
            }

            if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.bufferBegin(buffer, 7, NothiriumPipelineCompat.pipelineBlockFormat(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFormat()));
                int originX = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos), 16) * 16;
                int originY = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos), 16) * 16;
                int originZ = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos), 16) * 16;
                com.l.ausm.impl.util.MinecraftReflectionCompat.bufferSetTranslation(buffer, -originX, -originY, -originZ);
            }

            int cutoutStart = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer);
            boolean cutoutRendered = ausm$renderFireFallbackWithLayer(fallbackRenderState, pos, buffer, BlockRenderLayer.CUTOUT);
            int cutoutDelta = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer) - cutoutStart;
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

            int bloomStart = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer);
            boolean bloomRendered = ausm$renderFireFallbackWithLayer(fallbackRenderState, pos, buffer, bloomLayer);
            ausm$logFireFallback("fallback-bloom-layer", renderState, fallbackRenderState, pos, normalDelta,
                    bloomRendered, com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer) - bloomStart);
            return true;
        } finally {
            ausm$fireCutoutFallbackStart = -1;
        }
    }

    @Unique
    private boolean ausm$renderFireFallbackWithLayer(IBlockState state, BlockPos pos, BufferBuilder buffer,
                                                     BlockRenderLayer layer) {
        BlockRenderLayer previousLayer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(layer);
            BlockRendererDispatcher dispatcher = com.l.ausm.impl.util.MinecraftReflectionCompat.blockRendererDispatcher(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
            return dispatcher != null && com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlock(dispatcher, state, pos, chunkCache, buffer);
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
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
    private static boolean ausm$isNativeBloomOnlyBlock(IBlockState state) {
        Block block = ausm$block(state);
        if (state == null || block == null || com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderType(state) == EnumBlockRenderType.INVISIBLE) {
            return false;
        }

        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (bloomLayer == null
                || !com.l.ausm.impl.util.MinecraftReflectionCompat.blockCanRenderInLayer(
                        block, state, bloomLayer)) {
            return false;
        }

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (layer == null || AusmBloomLayer.isBloomLayer(layer)) {
                continue;
            }
            if (com.l.ausm.impl.util.MinecraftReflectionCompat.blockCanRenderInLayer(
                    block, state, layer)) {
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
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name);
        if (path.contains("fire") || com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterialIsFire(state)) {
            return BlockRenderLayer.CUTOUT;
        }
        BlockRenderLayer naturalLayer = ausm$naturalRenderLayer(state);
        if (naturalLayer != null && !AusmBloomLayer.isBloomLayer(naturalLayer)) {
            return naturalLayer;
        }
        if (path.contains("translucent") || !com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((state), new String[] {"func_185913_b", "isOpaqueCube"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) || !com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((state), new String[] {"func_185917_h", "isFullCube"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false)) {
            return BlockRenderLayer.TRANSLUCENT;
        }
        return BlockRenderLayer.SOLID;
    }

    @Unique
    private static BlockRenderLayer ausm$naturalRenderLayer(IBlockState state) {
        try {
            Block block = ausm$block(state);
            return block != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockRenderLayer(block) : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Unique
    private static boolean ausm$canRenderInLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        try {
            return TerrainCompileCoordinator.canRenderInLayer(block, state, layer, PipelineContext.getInstance());
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Unique
    private static boolean ausm$canRenderStateInLayer(IBlockState state, BlockRenderLayer layer) {
        return state != null && ausm$canRenderInLayer(ausm$block(state), state, layer);
    }

    @Unique
    private static boolean ausm$isEmissiveBloomFallbackTarget(IBlockState state) {
        return ausm$isEmissiveBloomFallbackSource(state);
    }

    @Unique
    private static boolean ausm$isEmissiveBloomFallbackSource(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        if (state == null || ausm$block(state) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderType(state) == EnumBlockRenderType.INVISIBLE) {
            return false;
        }
        if (PipelineContext.getInstance().isBlockcrafteryEditableState(state)) {
            return false;
        }
        if (PipelineContext.getInstance().stateHasShaderlessBloomSource(state)) {
            return true;
        }
        if (name == null) {
            return false;
        }
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name);
        String namespace = com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name);
        return "lumenized".equals(namespace)
                || path.contains("lumenized");
    }

    @Unique
    private static boolean ausm$isFireFallbackTarget(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        if (name != null && "minecraft".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name)) && "fire".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name))) {
            return true;
        }
        return state != null && com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterialIsFire(state);
    }

    @Unique
    private static ResourceLocation ausm$registryName(IBlockState state) {
        if (state == null) {
            return null;
        }
        Block block = ausm$block(state);
        return block != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(block) : null;
    }

    @Unique
    private static Block ausm$block(IBlockState state) {
        if (state == null) {
            return null;
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
    }

    @Unique
    private static void ausm$clearThreadCaches() {
        com.l.ausm.impl.util.MinecraftReflectionCompat.clearHotThreadCaches();
    }

    @Unique
    private static String ausm$stateName(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        return name != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.resourceString(name) : String.valueOf(state);
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
