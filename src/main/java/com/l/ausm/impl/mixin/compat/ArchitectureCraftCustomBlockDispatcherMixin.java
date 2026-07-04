package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "com.elytradev.architecture.client.render.CustomBlockDispatcher", remap = false)
public abstract class ArchitectureCraftCustomBlockDispatcherMixin {
    @Unique
    private static final int AUSM_ARCHITECTURE_DISPATCHER_LOG_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_ARCHITECTURE_DISPATCHER_LOGS = new AtomicInteger();

    @Inject(method = "func_175018_a", at = @At("HEAD"), remap = false)
    private void ausm$logRenderBlockHead(IBlockState state, BlockPos pos, IBlockAccess blockAccess,
                                         BufferBuilder buffer, CallbackInfoReturnable<Boolean> cir) {
        ausm$setArchitectureBlockContext(state, blockAccess, pos);
        ausm$logArchitectureDispatcher("architecture-dispatcher-head", state, blockAccess, pos, buffer, null, null, null);
    }

    @Inject(method = "func_175018_a", at = @At("RETURN"), remap = false)
    private void ausm$logRenderBlockReturn(IBlockState state, BlockPos pos, IBlockAccess blockAccess,
                                           BufferBuilder buffer, CallbackInfoReturnable<Boolean> cir) {
        // Diagnostic disabled.
    }

    @Inject(
            method = "customRenderBlockToWorld(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/elytradev/architecture/client/render/ICustomRenderer;)Z",
            at = @At("HEAD"),
            remap = false
    )
    private void ausm$logCustomRenderHead(IBlockAccess blockAccess, BlockPos pos, IBlockState state, BufferBuilder buffer,
                                          TextureAtlasSprite overrideTexture, @Coerce Object customRenderer,
                                          CallbackInfoReturnable<Boolean> cir) {
        ausm$setArchitectureBlockContext(state, blockAccess, pos);
        ausm$logArchitectureDispatcher("architecture-custom-head", state, blockAccess, pos, buffer, overrideTexture,
                customRenderer, null);
    }

    @Inject(
            method = "customRenderBlockToWorld(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/elytradev/architecture/client/render/ICustomRenderer;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private void ausm$logCustomRenderReturn(IBlockAccess blockAccess, BlockPos pos, IBlockState state, BufferBuilder buffer,
                                            TextureAtlasSprite overrideTexture, @Coerce Object customRenderer,
                                            CallbackInfoReturnable<Boolean> cir) {
        ausm$logArchitectureDispatcher("architecture-custom-return", state, blockAccess, pos, buffer, overrideTexture,
                customRenderer, cir.getReturnValue());
    }

    @Unique
    private static void ausm$setArchitectureBlockContext(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState contextState = pipeline.effectiveBlockRenderState(state, blockAccess, pos);
        if (contextState == null) {
            contextState = state;
        }
        BlockRenderContext.setBlockEntityId(pipeline.blockEntityId(state, blockAccess, pos));
        BlockRenderContext.setRenderType((short) (contextState != null ? contextState.getRenderType().ordinal() : -1));
        BlockRenderContext.setMetadata(pipeline.blockMetadata(state, blockAccess, pos));
        if (pos != null) {
            BlockRenderContext.setLocalBlockPos(pos.getX(), pos.getY(), pos.getZ());
        }
        BlockRenderContext.setBlockEmission(pipeline.blockRenderEmissionWithFramedInheritance(state, blockAccess, pos));
        BlockRenderContext.setBlockAlpha(pipeline.blockRenderAlpha(state, blockAccess, pos));
        BlockRenderContext.setCrystalOnlyEmission(pipeline.shouldUseCrystalOnlyEmission(state, blockAccess, pos));
        BlockRenderContext.setSeparateAoEligible(contextState != null && pipeline.shouldSeparateBlockAo(contextState, blockAccess, pos));
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.setBlockRenderDebugContext(state, blockAccess, pos);
        }
        pipeline.recordSyntheticLightCandidate(contextState, blockAccess, pos);
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.logCurrentProblemProbe("architecture-dispatcher-context", state, blockAccess, pos,
                    "context=" + pipeline.diagnosticStateName(contextState)
                            + ", contextEmission=" + BlockRenderContext.blockEmission()
                            + ", contextAlpha=" + BlockRenderContext.blockAlpha());
        }
    }

    @Unique
    private static void ausm$logArchitectureDispatcher(String source, IBlockState state, IBlockAccess blockAccess,
                                                       BlockPos pos, BufferBuilder buffer,
                                                       TextureAtlasSprite overrideTexture, Object customRenderer,
                                                       Boolean result) {
        // Diagnostic disabled.
    }

    @Unique
    private static String ausm$stateName(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return String.valueOf(state);
        }
        ResourceLocation name = state.getBlock().getRegistryName();
        return name != null ? name.toString() : state.toString();
    }
}
