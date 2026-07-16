package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.l.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
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
import java.util.IdentityHashMap;
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
    private static final int AUSM_SHADERLESS_COMPILE_LIGHT_PROBE_LIMIT = 0;

    @Unique
    private static final int AUSM_NOTHIRIUM_LAYER_COMPILE_LOG_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_NOTHIRIUM_LAYER_COMPILE_LOGS = new AtomicInteger();

    @Unique
    private static final AtomicInteger AUSM_SHADERLESS_COMPILE_LIGHT_PROBES = new AtomicInteger();

    @Unique
    private static final int AUSM_THREAD_CACHE_LIMIT = 4096;

    @Unique
    private static final ThreadLocal<IdentityHashMap<IBlockState, Block>> AUSM_STATE_BLOCKS =
            ThreadLocal.withInitial(IdentityHashMap::new);

    @Unique
    private static final ThreadLocal<IdentityHashMap<IBlockState, ResourceLocation>> AUSM_STATE_REGISTRY_NAMES =
            ThreadLocal.withInitial(IdentityHashMap::new);

    @Shadow(remap = false)
    private IBlockAccess chunkCache;

    @Unique
    private static volatile Field ausm$abstractRenderChunkTaskRenderChunkField;

    @Unique
    private int ausm$fireCutoutFallbackStart = -1;

    @Unique
    private int ausm$emissiveFallbackStart = -1;

    @Unique
    private int ausm$framedDiagnosticStart = -1;

    @Unique
    private BlockRenderLayer ausm$framedDiagnosticLayer = null;

    @Inject(
            method = "compileSection(Lnet/minecraft/client/renderer/RegionRenderCacheBuilder;)Lmeldexun/nothirium/api/renderer/chunk/RenderChunkTaskResult;",
            at = @At("HEAD"),
            remap = false
    )
    private void ausm$resetShaderlessBloomLayerSummaries(RegionRenderCacheBuilder regionBuffers, CallbackInfoReturnable<?> cir) {
        ausm$clearThreadCaches();
        PipelineContext.getInstance().beginFramedMaterialCompileCache();
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
    private void ausm$logNothiriumLayerCompileBuffers(RegionRenderCacheBuilder regionBuffers,
                                                      CallbackInfoReturnable<?> cir) {
        int index = AUSM_NOTHIRIUM_LAYER_COMPILE_LOGS.incrementAndGet();
        if (index > AUSM_NOTHIRIUM_LAYER_COMPILE_LOG_LIMIT) {
            return;
        }
        Object renderChunk = ausm$renderChunk();
        String chunk = "n/a";
        if (renderChunk instanceof meldexun.nothirium.mc.renderer.chunk.RenderChunk nothiriumChunk) {
            chunk = nothiriumChunk.getX() + "," + nothiriumChunk.getY() + "," + nothiriumChunk.getZ();
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumLayerCompile] call={} chunk={} solid={} cutoutMipped={} cutout={} translucent={} currentLayer={} pipelineActive={} formatMode={}",
                index,
                chunk,
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.SOLID),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.CUTOUT_MIPPED),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.CUTOUT),
                ausm$layerCompileBufferDetails(regionBuffers, BlockRenderLayer.TRANSLUCENT),
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(),
                PipelineContext.getInstance().isPipelineActive(),
                NothiriumPipelineCompat.shouldUsePipelineBlockFormat()
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
        ausm$emissiveFallbackStart = -1;
        PipelineContext pipeline = PipelineContext.getInstance();
        if (pipeline.shouldForceVanillaTerrainRenderer()) {
            return;
        }
        IBlockState effectiveState = pipeline.effectiveBlockRenderState(state, chunkCache, pos);
        IBlockState inheritedBloomState = pipeline.inheritedBloomRenderState(state, chunkCache, pos);
        boolean originalFire = ausm$isFireFallbackTarget(state);
        boolean effectiveFire = ausm$isFireFallbackTarget(effectiveState);
        BufferBuilder buffer = regionBuffers != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, BlockRenderLayer.CUTOUT) : null;
        boolean framedState = pipeline.isFramedBlockDiagnosticTarget(state);
        boolean forcedFramedBloom = framedState
                && pipeline.framedBloomFallbackEmission(state, chunkCache, pos) > 0;
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
        if (pipeline.shouldForceVanillaTerrainRenderer()) {
            BlockRenderContext.setBlockEntityId(0);
            BlockRenderContext.setRenderType((short) com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderTypeOrdinal(state));
            BlockRenderContext.setMetadata(0);
            BlockRenderContext.setLocalBlockPos(
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos)
            );
            BlockRenderContext.setWorldBlockContext(chunkCache, pos);
            BlockRenderContext.setAgricraftCrop(false);
            BlockRenderContext.setPackedLightmap(0);
            BlockRenderContext.setBlockEmission(0);
            BlockRenderContext.setBloomOnlyEmission(false);
            BlockRenderContext.setBlockAlpha(-1);
            BlockRenderContext.setCustomLiquidTint(-1);
            BlockRenderContext.setCrystalOnlyEmission(false);
            BlockRenderContext.setSeparateAoEligible(false);
            ausm$framedDiagnosticStart = -1;
            ausm$framedDiagnosticLayer = null;
            return;
        }
        IBlockState contextState = pipeline.effectiveBlockRenderState(state, chunkCache, pos);
        if (contextState == null) {
            contextState = state;
        }

        int blockEntityId = pipeline.blockEntityId(state, chunkCache, pos);
        BlockRenderContext.setBlockEntityId(blockEntityId);
        BlockRenderContext.setRenderType((short) com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderTypeOrdinal(contextState));
        BlockRenderContext.setMetadata(pipeline.blockMetadata(state, chunkCache, pos));
        BlockRenderContext.setLocalBlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos));
        BlockRenderContext.setWorldBlockContext(chunkCache, pos);
        BlockRenderContext.setAgricraftCrop(ausm$isAgricraftCropState(contextState));
        int packedLightmap = ausm$packedLightmap(contextState, chunkCache, pos);
        BlockRenderContext.setPackedLightmap(packedLightmap);
        int blockEmission = pipeline.shouldUseShaderlessBloomEmission()
                ? pipeline.blockShaderlessBloomEmission(state, chunkCache, pos)
                : (pipeline.shouldInheritFramedEmissionInBasePass(state)
                || BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null)
                        ? pipeline.blockRenderEmissionWithFramedInheritance(state, chunkCache, pos)
                        : pipeline.blockRenderEmission(state, chunkCache, pos);
        if (BlockRendererDispatcherHooks.BLOOM_FALLBACK_RENDER.get() != null) {
            blockEmission = Math.max(blockEmission, pipeline.framedBloomFallbackEmission(state, chunkCache, pos));
        }
        int framedShaderlessExtractionEmission = pipeline.shaderlessFramedBloomExtractionEmission(state, chunkCache, pos);
        blockEmission = Math.max(blockEmission, framedShaderlessExtractionEmission);
        BlockRenderContext.setBlockEmission(blockEmission);
        BlockRenderContext.setBloomOnlyEmission(framedShaderlessExtractionEmission > 0);
        BlockRenderContext.setBlockAlpha(pipeline.blockRenderAlpha(state, chunkCache, pos));
        BlockRenderContext.setCustomLiquidTint(pipeline.customLiquidTintColor(state, chunkCache, pos));
        BlockRenderContext.setCrystalOnlyEmission(pipeline.shouldUseCrystalOnlyEmission(state, chunkCache, pos));
        BlockRenderContext.setSeparateAoEligible(pipeline.shouldSeparateBlockAo(contextState, chunkCache, pos));
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.setBlockRenderDebugContext(state, chunkCache, pos);
        }
        pipeline.recordSyntheticLightCandidate(contextState, chunkCache, pos);

        ausm$framedDiagnosticStart = -1;
        ausm$framedDiagnosticLayer = null;
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
        BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.regionBufferForLayer(regionBuffers, fallbackLayer);
        if (buffer == null) {
            return;
        }

        if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferBegin(buffer, 7, NothiriumPipelineCompat.pipelineBlockFormat(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFormat()));
            int originX = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos), 16) * 16;
            int originY = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos), 16) * 16;
            int originZ = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos), 16) * 16;
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferSetTranslation(buffer, -originX, -originY, -originZ);
        }

        BlockRenderLayer previousLayer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
        int start = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer);
        boolean rendered = false;
        try {
            // Keep the model in its native BLOOM render layer while storing the
            // resulting geometry in a vanilla Nothirium pass.
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(bloomLayer);
            BlockRendererDispatcher dispatcher = com.l.ausm.impl.util.MinecraftReflectionCompat.blockRendererDispatcher(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
            rendered = dispatcher != null && com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlock(dispatcher, fallbackTarget, pos, chunkCache, buffer);
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
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

            boolean textureBloomSource = PipelineContext.getInstance().stateUsesTextureBloomSource(fallbackSourceState);
            boolean solidBloomMaskFallback = framedState && !textureBloomSource;
            BlockRenderLayer renderLayer = framedState
                    ? ausm$framedGeometryLayer(fallbackRenderState, fallbackSourceState)
                    : bloomLayer;
            BlockRenderLayer bufferLayer = framedState ? renderLayer : ausm$bloomFallbackLayer(fallbackSourceState);
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
                ausm$logEmissiveFallback("normal-or-dispatcher-present", renderState, fallbackRenderState, pos,
                        fallbackSourceState, bufferLayer, renderLayer, normalDelta, false, 0, buffer, regionBuffers);
                return true;
            }

            if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.bufferBegin(buffer, 7, NothiriumPipelineCompat.pipelineBlockFormat(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFormat()));
                int originX = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos), 16) * 16;
                int originY = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos), 16) * 16;
                int originZ = Math.floorDiv(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos), 16) * 16;
                com.l.ausm.impl.util.MinecraftReflectionCompat.bufferSetTranslation(buffer, -originX, -originY, -originZ);
            }

            int fallbackStart = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer);
            boolean rendered = ausm$renderEmissiveFallbackWithLayer(fallbackRenderState, fallbackSourceState, pos, buffer, renderLayer, solidBloomMaskFallback);
            int fallbackDelta = com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer) - fallbackStart;
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
    private static boolean ausm$isBloomOnlyModelBlock(IBlockState state) {
        Block block = ausm$block(state);
        if (state == null || block == null || com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderType(state) == EnumBlockRenderType.INVISIBLE) {
            return false;
        }

        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (bloomLayer == null || !ausm$canRenderInLayer(block, state, bloomLayer)) {
            return false;
        }

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (layer == null || AusmBloomLayer.isBloomLayer(layer)) {
                continue;
            }
            if (ausm$canRenderInLayer(block, state, layer)) {
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
        if (ausm$isRandomThingsLuminousState(state)) {
            return ausm$isRandomThingsTranslucentLuminousState(state)
                    ? BlockRenderLayer.TRANSLUCENT
                    : BlockRenderLayer.SOLID;
        }
        BlockRenderLayer naturalLayer = ausm$naturalRenderLayer(state);
        if (naturalLayer != null && !AusmBloomLayer.isBloomLayer(naturalLayer)) {
            return naturalLayer;
        }
        if (path.contains("translucent") || !com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((state), new String[] {"func_185913_b", "isOpaqueCube"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) || !com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((state), new String[] {"func_185917_h", "isFullCube"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false)) {
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
                && "randomthings".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name))
                && ausm$isRandomThingsLuminousPath(com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name));
    }

    @Unique
    private static boolean ausm$isRandomThingsLuminousPath(String path) {
        return "luminousblock".equalsIgnoreCase(path)
                || "translucentluminousblock".equalsIgnoreCase(path)
                || "luminousstainedbrick".equalsIgnoreCase(path);
    }

    @Unique
    private static boolean ausm$isRandomThingsTranslucentLuminousState(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        return name != null
                && "randomthings".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name))
                && "translucentluminousblock".equalsIgnoreCase(com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name));
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
            return block != null && layer != null && com.l.ausm.impl.util.MinecraftReflectionCompat.blockCanRenderInLayer(block, state, layer);
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
                || ausm$isRandomThingsLuminousState(state)
                || path.contains("lumenized")
                || path.contains("luminous")
                || path.contains("emissive")
                || path.contains("bloom");
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
        IdentityHashMap<IBlockState, ResourceLocation> cache = AUSM_STATE_REGISTRY_NAMES.get();
        ResourceLocation cached = cache.get(state);
        if (cached != null) {
            return cached;
        }
        Block block = ausm$block(state);
        ResourceLocation name = block != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(block) : null;
        if (name != null) {
            if (cache.size() > AUSM_THREAD_CACHE_LIMIT) {
                cache.clear();
            }
            cache.put(state, name);
            return name;
        }
        return null;
    }

    @Unique
    private static Block ausm$block(IBlockState state) {
        if (state == null) {
            return null;
        }
        IdentityHashMap<IBlockState, Block> cache = AUSM_STATE_BLOCKS.get();
        Block cached = cache.get(state);
        if (cached != null) {
            return cached;
        }
        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        if (block != null) {
            if (cache.size() > AUSM_THREAD_CACHE_LIMIT) {
                cache.clear();
            }
            cache.put(state, block);
            return block;
        }
        return null;
    }

    @Unique
    private static void ausm$clearThreadCaches() {
        AUSM_STATE_BLOCKS.get().clear();
        AUSM_STATE_REGISTRY_NAMES.get().clear();
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
