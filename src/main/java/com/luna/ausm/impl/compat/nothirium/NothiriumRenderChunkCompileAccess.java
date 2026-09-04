package com.luna.ausm.impl.compat.nothirium;

import com.luna.ausm.impl.pipeline.PipelineContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Runtime-safe view of state added to Nothirium's compile task by the mixin.
 * Helper classes must depend on this interface, never on the mixin class itself.
 */
public interface NothiriumRenderChunkCompileAccess {
    NothiriumRenderChunkCompileState ausm$state();

    IBlockAccess ausm$chunkCache();

    Object ausm$renderChunk();

    void ausm$logTerrainCompileBlockProbe(IBlockState state, BlockPos pos,
                                          RegionRenderCacheBuilder regionBuffers, boolean cancelled);

    void ausm$beginFramedBloomRouteProbe(IBlockState state, BlockPos pos,
                                         RegionRenderCacheBuilder regionBuffers, PipelineContext pipeline);

    void ausm$captureFramedBloomRouteLayer(RegionRenderCacheBuilder regionBuffers);

    void ausm$logFramedBloomRouteProbe(IBlockState state, BlockPos pos,
                                       RegionRenderCacheBuilder regionBuffers, PipelineContext pipeline, String route);

    void ausm$resetFramedBloomRouteProbe();

    void ausm$beginBloomBaseRouteProbe(IBlockState state, IBlockState effectiveState, BlockPos pos,
                                       RegionRenderCacheBuilder regionBuffers, PipelineContext pipeline);

    void ausm$logBloomBaseRouteProbe(String route, IBlockState state, BlockPos pos,
                                     RegionRenderCacheBuilder regionBuffers, PipelineContext pipeline);

    void ausm$resetBloomBaseRouteProbe();

    boolean ausm$renderMissingBloomOnlyBaseFallback(IBlockState originalState, BlockPos pos,
                                                    RegionRenderCacheBuilder regionBuffers);

    boolean ausm$renderStackedEmissiveBloomLayer(IBlockState renderState, IBlockState fallbackTarget,
                                                 BlockPos pos, RegionRenderCacheBuilder regionBuffers);

    boolean ausm$renderEmissiveFallbackWithLayer(IBlockState state, IBlockState maskColorState,
                                                 BlockPos pos, BufferBuilder buffer,
                                                 BlockRenderLayer layer, boolean bloomMaskFallback);

    void ausm$logEmissiveFallback(String mode, IBlockState originalState, IBlockState renderState,
                                  BlockPos pos, IBlockState sourceState, BlockRenderLayer fallbackLayer,
                                  BlockRenderLayer renderLayer, int normalDelta, boolean rendered,
                                  int fallbackDelta, BufferBuilder buffer,
                                  RegionRenderCacheBuilder regionBuffers);

    boolean ausm$renderMissingFireCutoutFallback(IBlockState renderState, IBlockState fallbackTarget,
                                                 BlockPos pos, RegionRenderCacheBuilder regionBuffers);

    boolean ausm$renderFireFallbackWithLayer(IBlockState state, BlockPos pos, BufferBuilder buffer,
                                             BlockRenderLayer layer);
}
