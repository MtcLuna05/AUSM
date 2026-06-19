package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "com.elytradev.architecture.client.render.RenderingManager", remap = false)
public abstract class ArchitectureCraftRenderingManagerMixin {
    @Unique
    private static final int AUSM_ARCHITECTURE_MANAGER_LOG_LIMIT = 0;

    @Unique
    private static final AtomicInteger AUSM_ARCHITECTURE_MANAGER_LOGS = new AtomicInteger();

    @Inject(
            method = "getCustomRenderer(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Lcom/elytradev/architecture/client/render/ICustomRenderer;",
            at = @At("HEAD"),
            remap = false
    )
    private void ausm$setArchitectureContext(IBlockAccess blockAccess, BlockPos pos, IBlockState state,
                                             CallbackInfoReturnable<Object> cir) {
        if (!PipelineContext.getInstance().isFramedBlockDiagnosticTarget(state)) {
            return;
        }
        ausm$setBlockContext(state, blockAccess, pos);
        ausm$logArchitectureManager("manager-head", state, blockAccess, pos, null);
    }

    @Inject(
            method = "getCustomRenderer(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Lcom/elytradev/architecture/client/render/ICustomRenderer;",
            at = @At("RETURN"),
            remap = false
    )
    private void ausm$logArchitectureRenderer(IBlockAccess blockAccess, BlockPos pos, IBlockState state,
                                              CallbackInfoReturnable<Object> cir) {
        if (!PipelineContext.getInstance().isFramedBlockDiagnosticTarget(state)) {
            return;
        }
        ausm$setBlockContext(state, blockAccess, pos);
        Object renderer = cir.getReturnValue();
        ausm$logArchitectureManager("manager-return", state, blockAccess, pos, renderer);
    }

    @Unique
    private static void ausm$setBlockContext(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
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
        pipeline.setBlockRenderDebugContext(state, blockAccess, pos);
        pipeline.recordSyntheticLightCandidate(contextState, blockAccess, pos);
        pipeline.logCurrentProblemProbe("architecture-manager-context", state, blockAccess, pos,
                "context=" + pipeline.diagnosticStateName(contextState)
                        + ", contextEmission=" + BlockRenderContext.blockEmission()
                        + ", contextAlpha=" + BlockRenderContext.blockAlpha());
    }

    @Unique
    private static void ausm$logArchitectureManager(String source, IBlockState state, IBlockAccess blockAccess,
                                                   BlockPos pos, Object renderer) {
        int count = AUSM_ARCHITECTURE_MANAGER_LOGS.incrementAndGet();
        if (count > AUSM_ARCHITECTURE_MANAGER_LOG_LIMIT) {
            return;
        }

        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState effectiveState = pipeline.effectiveBlockRenderState(state, blockAccess, pos);
        IBlockState inheritedState = pipeline.inheritedBloomRenderState(state, blockAccess, pos);
        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
        MainMod.LOGGER.info(
                "[AUSMArchitectureManagerDiag] call={} source={} pos={} layer={} state={} effective={} inherited={} emission={} inheritedEmission={} contextEmission={} renderer={} access={}",
                count,
                source,
                pos,
                layer,
                ausm$stateName(state),
                ausm$stateName(effectiveState),
                ausm$stateName(inheritedState),
                pipeline.blockRenderEmission(state, blockAccess, pos),
                pipeline.blockRenderEmissionWithFramedInheritance(state, blockAccess, pos),
                BlockRenderContext.blockEmission(),
                renderer != null ? renderer.getClass().getName() : "null",
                blockAccess != null ? blockAccess.getClass().getName() : "null"
        );
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
