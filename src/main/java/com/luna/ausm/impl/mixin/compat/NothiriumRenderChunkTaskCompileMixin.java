package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.compat.nothirium.NothiriumRenderChunkCompileAccess;
import com.luna.ausm.impl.compat.nothirium.NothiriumRenderChunkCompileState;
import com.luna.ausm.impl.compat.nothirium.NothiriumRenderChunkTaskCompileHooks;
import com.luna.ausm.impl.pipeline.PipelineContext;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.RenderChunkTaskCompile", remap = false)
public abstract class NothiriumRenderChunkTaskCompileMixin implements NothiriumRenderChunkCompileAccess {
    @Shadow(remap = false)
    private IBlockAccess chunkCache;

    @Override
    @Unique
    public IBlockAccess ausm$chunkCache() {
        return chunkCache;
    }

    @Unique
    private static volatile Field ausm$abstractRenderChunkTaskRenderChunkField;

    @Unique
    private final NothiriumRenderChunkCompileState ausm$compileState =
            new NothiriumRenderChunkCompileState();

    @Override
    @Unique
    public NothiriumRenderChunkCompileState ausm$state() {
        return ausm$compileState;
    }

    @Inject(
            method = "compileSection(Lnet/minecraft/client/renderer/RegionRenderCacheBuilder;)Lmeldexun/nothirium/api/renderer/chunk/RenderChunkTaskResult;",
            at = @At("HEAD"),
            remap = false
    )
    void ausm$resetShaderlessBloomLayerSummaries(RegionRenderCacheBuilder regionBuffers, CallbackInfoReturnable<?> cir) {
        NothiriumRenderChunkTaskCompileHooks.ausm$resetShaderlessBloomLayerSummaries(this, regionBuffers, cir);
    }

    @Inject(
            method = "compileSection(Lnet/minecraft/client/renderer/RegionRenderCacheBuilder;)Lmeldexun/nothirium/api/renderer/chunk/RenderChunkTaskResult;",
            at = @At("RETURN"),
            remap = false
    )
    void ausm$recordShaderlessBloomLayerSummaries(RegionRenderCacheBuilder regionBuffers, CallbackInfoReturnable<?> cir) {
        NothiriumRenderChunkTaskCompileHooks.ausm$recordShaderlessBloomLayerSummaries(this, regionBuffers, cir);
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
    void ausm$probeNothiriumCompileBuffers(RegionRenderCacheBuilder regionBuffers,
                                           CallbackInfoReturnable<?> cir) {
        NothiriumRenderChunkTaskCompileHooks.ausm$probeNothiriumCompileBuffers(this, regionBuffers, cir);
    }

    @Unique
    public Object ausm$renderChunk() {
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
        NothiriumRenderChunkTaskCompileHooks.ausm$resetShaderlessBloomMetadata(regionBuffers);
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
    VertexFormat ausm$usePipelineBlockFormat(VertexFormat original) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$usePipelineBlockFormat(this, original);
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
    VertexFormat ausm$usePipelineBlockFormatForSectionBuffers(VertexFormat original) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$usePipelineBlockFormatForSectionBuffers(this, original);
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
    boolean ausm$forceEmissiveFallbackLayer(Block block,
                                            IBlockState state,
                                            BlockRenderLayer layer,
                                            IBlockState renderState,
                                            BlockPos pos,
                                            VisibilityGraph visibilityGraph,
                                            RegionRenderCacheBuilder regionBuffers) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$forceEmissiveFallbackLayer(this, block, state, layer, renderState, pos, visibilityGraph, regionBuffers);
    }

    @Inject(method = "renderBlockState", at = @At("HEAD"), remap = false)
    void ausm$captureFireCutoutStart(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                     RegionRenderCacheBuilder regionBuffers, CallbackInfo ci) {
        NothiriumRenderChunkTaskCompileHooks.ausm$captureFireCutoutStart(this, state, pos, visibilityGraph, regionBuffers, ci);
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
    void ausm$setPipelineBlockContext(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                      RegionRenderCacheBuilder bufferBuilder, CallbackInfo ci) {
        NothiriumRenderChunkTaskCompileHooks.ausm$setPipelineBlockContext(this, state, pos, visibilityGraph, bufferBuilder, ci);
    }

    @Unique
    private static boolean ausm$isAgricraftCropState(IBlockState state) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$isAgricraftCropState(state);
    }

    @Unique
    private static int ausm$packedLightmap(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$packedLightmap(state, blockAccess, pos);
    }

    @Unique
    private static int ausm$safeCombinedLight(IBlockAccess blockAccess, BlockPos pos, int lightValue) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$safeCombinedLight(blockAccess, pos, lightValue);
    }

    @Unique
    private static int ausm$safeStateLightValue(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$safeStateLightValue(state, blockAccess, pos);
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
    void ausm$clearPipelineBlockContext(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                        RegionRenderCacheBuilder bufferBuilder, CallbackInfo ci) {
        NothiriumRenderChunkTaskCompileHooks.ausm$clearPipelineBlockContext(this, state, pos, visibilityGraph, bufferBuilder, ci);
    }

    @Unique
    public void ausm$logTerrainCompileBlockProbe(IBlockState state, BlockPos pos, RegionRenderCacheBuilder regionBuffers,
                                          boolean cancelled) {
        NothiriumRenderChunkTaskCompileHooks.ausm$logTerrainCompileBlockProbe(this, state, pos, regionBuffers, cancelled);
    }

    @Inject(method = "renderBlockState", at = @At("RETURN"), remap = false)
    void ausm$renderBloomOnlyFallback(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                      RegionRenderCacheBuilder regionBuffers, CallbackInfo ci) {
        NothiriumRenderChunkTaskCompileHooks.ausm$renderBloomOnlyFallback(this, state, pos, visibilityGraph, regionBuffers, ci);
    }

    @Unique
    public void ausm$beginFramedBloomRouteProbe(IBlockState state, BlockPos pos,
                                         RegionRenderCacheBuilder regionBuffers, PipelineContext pipeline) {
        NothiriumRenderChunkTaskCompileHooks.ausm$beginFramedBloomRouteProbe(this, state, pos, regionBuffers, pipeline);
    }

    @Unique
    public void ausm$captureFramedBloomRouteLayer(RegionRenderCacheBuilder regionBuffers) {
        NothiriumRenderChunkTaskCompileHooks.ausm$captureFramedBloomRouteLayer(this, regionBuffers);
    }

    @Unique
    public void ausm$logFramedBloomRouteProbe(IBlockState state, BlockPos pos, RegionRenderCacheBuilder regionBuffers,
                                       PipelineContext pipeline, String route) {
        NothiriumRenderChunkTaskCompileHooks.ausm$logFramedBloomRouteProbe(this, state, pos, regionBuffers, pipeline, route);
    }

    @Unique
    private static void ausm$logFramedBloomFinalCompileProbe(BlockPos pos, RegionRenderCacheBuilder regionBuffers,
                                                     BlockRenderLayer bloomLayer, int start, int end) {
        NothiriumRenderChunkTaskCompileHooks.ausm$logFramedBloomFinalCompileProbe(pos, regionBuffers, bloomLayer, start, end);
    }

    @Unique
    public void ausm$resetFramedBloomRouteProbe() {
        NothiriumRenderChunkTaskCompileHooks.ausm$resetFramedBloomRouteProbe(this);
    }

    @Unique
    private static String ausm$describePipelineQuad(ByteBuffer raw, int byteStart, int stride) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$describePipelineQuad(raw, byteStart, stride);
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
        return NothiriumRenderChunkTaskCompileHooks.ausm$describePipelineVertex(x, y, z, color, u, v, light, normal, entity, midU, midV, midBlock);
    }

    @Unique
    public void ausm$beginBloomBaseRouteProbe(
            IBlockState state,
            IBlockState effectiveState,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers,
            PipelineContext pipeline
    ) {
        NothiriumRenderChunkTaskCompileHooks.ausm$beginBloomBaseRouteProbe(this, state, effectiveState, pos, regionBuffers, pipeline);
    }

    @Unique
    public void ausm$logBloomBaseRouteProbe(
            String route,
            IBlockState state,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers,
            PipelineContext pipeline
    ) {
        NothiriumRenderChunkTaskCompileHooks.ausm$logBloomBaseRouteProbe(this, route, state, pos, regionBuffers, pipeline);
    }

    @Unique
    private static int ausm$layerVertexCount(RegionRenderCacheBuilder regionBuffers, BlockRenderLayer layer) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$layerVertexCount(regionBuffers, layer);
    }

    @Unique
    private static int ausm$delta(int start, int end) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$delta(start, end);
    }

    @Unique
    public void ausm$resetBloomBaseRouteProbe() {
        NothiriumRenderChunkTaskCompileHooks.ausm$resetBloomBaseRouteProbe(this);
    }

    @Unique
    public boolean ausm$renderMissingBloomOnlyBaseFallback(
            IBlockState originalState,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers
    ) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$renderMissingBloomOnlyBaseFallback(this, originalState, pos, regionBuffers);
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
        NothiriumRenderChunkTaskCompileHooks.ausm$logBloomOnlyBaseFallback(mode, originalState, fallbackState, pos, baseLayer, normalDelta, rendered, fallbackDelta);
    }

    @Unique
    public boolean ausm$renderStackedEmissiveBloomLayer(
            IBlockState renderState,
            IBlockState fallbackTarget,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers
    ) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$renderStackedEmissiveBloomLayer(this, renderState, fallbackTarget, pos, regionBuffers);
    }

    @Unique
    private static void ausm$markShaderlessBloomMetadata(BufferBuilder buffer, BlockRenderLayer layer, BlockPos pos) {
        NothiriumRenderChunkTaskCompileHooks.ausm$markShaderlessBloomMetadata(buffer, layer, pos);
    }

    @Unique
    private static BlockRenderLayer ausm$framedGeometryLayer(IBlockState framedState, IBlockState inheritedState) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$framedGeometryLayer(framedState, inheritedState);
    }

    @Unique
    public boolean ausm$renderEmissiveFallbackWithLayer(IBlockState state, IBlockState maskColorState, BlockPos pos, BufferBuilder buffer,
                                                 BlockRenderLayer layer, boolean bloomMaskFallback) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$renderEmissiveFallbackWithLayer(this, state, maskColorState, pos, buffer, layer, bloomMaskFallback);
    }

    @Unique
    public void ausm$logEmissiveFallback(String mode, IBlockState originalState, IBlockState renderState,
                                  BlockPos pos, IBlockState sourceState,
                                  BlockRenderLayer fallbackLayer, BlockRenderLayer renderLayer,
                                  int normalDelta, boolean rendered, int fallbackDelta,
                                  BufferBuilder buffer, RegionRenderCacheBuilder regionBuffers) {
        NothiriumRenderChunkTaskCompileHooks.ausm$logEmissiveFallback(this, mode, originalState, renderState, pos, sourceState, fallbackLayer, renderLayer, normalDelta, rendered, fallbackDelta, buffer, regionBuffers);
    }

    @Unique
    private static String ausm$bufferDetails(BufferBuilder buffer) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$bufferDetails(buffer);
    }

    @Unique
    private static String ausm$layerCompileBufferDetails(RegionRenderCacheBuilder regionBuffers, BlockRenderLayer layer) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$layerCompileBufferDetails(regionBuffers, layer);
    }

    @Unique
    public boolean ausm$renderMissingFireCutoutFallback(
            IBlockState renderState,
            IBlockState fallbackTarget,
            BlockPos pos,
            RegionRenderCacheBuilder regionBuffers
    ) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$renderMissingFireCutoutFallback(this, renderState, fallbackTarget, pos, regionBuffers);
    }

    @Unique
    public boolean ausm$renderFireFallbackWithLayer(IBlockState state, BlockPos pos, BufferBuilder buffer,
                                             BlockRenderLayer layer) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$renderFireFallbackWithLayer(this, state, pos, buffer, layer);
    }

    @Unique
    private static void ausm$logFireFallback(String mode, IBlockState originalState, IBlockState renderState,
                                     BlockPos pos, int normalDelta, boolean rendered, int fallbackDelta) {
        NothiriumRenderChunkTaskCompileHooks.ausm$logFireFallback(mode, originalState, renderState, pos, normalDelta, rendered, fallbackDelta);
    }

    @Unique
    private static boolean ausm$isNativeBloomOnlyBlock(IBlockState state) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$isNativeBloomOnlyBlock(state);
    }

    @Unique
    private static BlockRenderLayer ausm$bloomFallbackLayer(IBlockState state) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$bloomFallbackLayer(state);
    }

    @Unique
    private static BlockRenderLayer ausm$naturalRenderLayer(IBlockState state) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$naturalRenderLayer(state);
    }

    @Unique
    private static boolean ausm$canRenderInLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$canRenderInLayer(block, state, layer);
    }

    @Unique
    private static boolean ausm$canRenderStateInLayer(IBlockState state, BlockRenderLayer layer) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$canRenderStateInLayer(state, layer);
    }

    @Unique
    private static boolean ausm$isEmissiveBloomFallbackTarget(IBlockState state) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$isEmissiveBloomFallbackTarget(state);
    }

    @Unique
    private static boolean ausm$isEmissiveBloomFallbackSource(IBlockState state) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$isEmissiveBloomFallbackSource(state);
    }

    @Unique
    private static boolean ausm$isFireFallbackTarget(IBlockState state) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$isFireFallbackTarget(state);
    }

    @Unique
    private static ResourceLocation ausm$registryName(IBlockState state) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$registryName(state);
    }

    @Unique
    private static Block ausm$block(IBlockState state) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$block(state);
    }

    @Unique
    private static void ausm$clearThreadCaches() {
        NothiriumRenderChunkTaskCompileHooks.ausm$clearThreadCaches();
    }

    @Unique
    private static String ausm$stateName(IBlockState state) {
        return NothiriumRenderChunkTaskCompileHooks.ausm$stateName(state);
    }

    @Unique
    private static String ausm$externalCaller() {
        return NothiriumRenderChunkTaskCompileHooks.ausm$externalCaller();
    }
}
