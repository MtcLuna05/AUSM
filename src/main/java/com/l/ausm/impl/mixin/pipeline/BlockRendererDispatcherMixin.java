package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRendererDispatcher.class)
public class BlockRendererDispatcherMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"))
    private void ausm$beforeRenderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, CallbackInfoReturnable<Boolean> cir) {
        PipelineContext pipeline = PipelineContext.getInstance();
        int blockEntityId = pipeline.blockEntityId(state);
        int blockEmission = pipeline.blockRenderEmission(state, blockAccess, pos);
        BlockRenderContext.setBlockEntityId(blockEntityId);
        BlockRenderContext.setRenderType((short) state.getRenderType().ordinal());
        BlockRenderContext.setMetadata(state.getBlock().getMetaFromState(state));
        BlockRenderContext.setLocalBlockPos(pos.getX(), pos.getY(), pos.getZ());
        BlockRenderContext.setBlockEmission(blockEmission);
        BlockRenderContext.setSeparateAoEligible(pipeline.shouldSeparateBlockAo(state));
        pipeline.recordSyntheticLightCandidate(state, blockAccess, pos);
    }

    @Inject(method = "renderBlock", at = @At("RETURN"))
    private void ausm$afterRenderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, CallbackInfoReturnable<Boolean> cir) {
        BlockRenderContext.clear();
    }
}
