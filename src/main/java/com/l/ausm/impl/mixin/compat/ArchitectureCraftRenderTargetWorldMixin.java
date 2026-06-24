package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.elytradev.architecture.client.render.target.RenderTargetWorld", remap = false)
public abstract class ArchitectureCraftRenderTargetWorldMixin {
    private static final int AUSM_ARCHITECTURE_TARGET_LOG_LIMIT = 0;
    private static int ausm$architectureTargetLogs;

    @Shadow(remap = false)
    protected IBlockAccess world;

    @Shadow(remap = false)
    protected BlockPos blockPos;

    @Shadow(remap = false)
    protected IBlockState blockState;

    @Shadow(remap = false)
    protected int vlm1;

    @Shadow(remap = false)
    protected int vlm2;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void ausm$logRenderTargetInit(IBlockAccess world, BlockPos blockPos, BufferBuilder buffer,
                                          TextureAtlasSprite overrideTexture, CallbackInfo ci) {
        ausm$setArchitectureTargetContext();
        ausm$log("architecture-target-init",
                "buffer=" + (buffer != null ? Integer.toHexString(System.identityHashCode(buffer)) : "null")
                        + ", overrideTexture=" + (overrideTexture != null ? overrideTexture.getIconName() : "null"));
        ausm$logTargetDirect("architecture-target-init", null);
    }

    @Inject(
            method = "rawAddVertex(Lcom/elytradev/architecture/common/helpers/Vector3;DD)V",
            at = @At("HEAD"),
            remap = false
    )
    private void ausm$refreshContextForVertex(@Coerce Object vertex, double u, double v, CallbackInfo ci) {
        ausm$setArchitectureTargetContext();
    }

    @Inject(method = "setLight", at = @At("HEAD"), remap = false)
    private void ausm$logSetLight(float shade, int packedLight, CallbackInfo ci) {
        ausm$log("architecture-target-setLight",
                "shade=" + shade
                        + ", packedLight=" + packedLight
                        + ", contextEmission=" + BlockRenderContext.blockEmission()
                        + ", contextAlpha=" + BlockRenderContext.blockAlpha()
                        + ", vlm=" + vlm1 + "/" + vlm2);
        ausm$logTargetDirect("architecture-target-setLight", null);
    }

    @Inject(method = "setLight", at = @At("RETURN"), remap = false)
    private void ausm$forceInheritedEmissionLight(float shade, int packedLight, CallbackInfo ci) {
        int contextEmission = BlockRenderContext.blockEmission();
        if (contextEmission <= 0 || AusmBloomLayer.isBloomLayer(MinecraftForgeClient.getRenderLayer())) {
            return;
        }
        vlm1 = Math.max(vlm1, 240);
        vlm2 = Math.max(vlm2, 240);
        PipelineContext pipeline = PipelineContext.getInstance();
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.logCurrentRenderContextProbe("architecture-target-force-light",
                    "shade=" + shade
                            + ", packedLight=" + packedLight
                            + ", contextEmission=" + contextEmission
                            + ", vlm=" + vlm1 + "/" + vlm2);
        }
    }

    @Inject(method = "end", at = @At("RETURN"), remap = false)
    private void ausm$logEnd(CallbackInfoReturnable<Boolean> cir) {
        ausm$log("architecture-target-end",
                "result=" + cir.getReturnValue()
                        + ", contextEmission=" + BlockRenderContext.blockEmission()
                        + ", contextAlpha=" + BlockRenderContext.blockAlpha()
                        + ", vlm=" + vlm1 + "/" + vlm2);
        ausm$logTargetDirect("architecture-target-end", cir.getReturnValue());
        BlockRenderContext.clear();
    }

    private void ausm$setArchitectureTargetContext() {
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState contextState = pipeline.effectiveBlockRenderState(blockState, world, blockPos);
        if (contextState == null) {
            contextState = blockState;
        }
        BlockRenderContext.setBlockEntityId(pipeline.blockEntityId(blockState, world, blockPos));
        BlockRenderContext.setRenderType((short) (contextState != null ? contextState.getRenderType().ordinal() : -1));
        BlockRenderContext.setMetadata(pipeline.blockMetadata(blockState, world, blockPos));
        if (blockPos != null) {
            BlockRenderContext.setLocalBlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        BlockRenderContext.setBlockEmission(pipeline.blockRenderEmissionWithFramedInheritance(blockState, world, blockPos));
        BlockRenderContext.setBlockAlpha(pipeline.blockRenderAlpha(blockState, world, blockPos));
        BlockRenderContext.setCrystalOnlyEmission(pipeline.shouldUseCrystalOnlyEmission(blockState, world, blockPos));
        BlockRenderContext.setSeparateAoEligible(contextState != null && pipeline.shouldSeparateBlockAo(contextState, world, blockPos));
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.setBlockRenderDebugContext(blockState, world, blockPos);
        }
        pipeline.recordSyntheticLightCandidate(contextState, world, blockPos);
        if (pipeline.currentProblemProbesEnabled()) {
            pipeline.logCurrentProblemProbe("architecture-target-context", blockState, world, blockPos,
                    "context=" + pipeline.diagnosticStateName(contextState)
                            + ", contextEmission=" + BlockRenderContext.blockEmission()
                            + ", contextAlpha=" + BlockRenderContext.blockAlpha()
                            + ", vlm=" + vlm1 + "/" + vlm2);
        }
    }

    private void ausm$logTargetDirect(String source, Boolean result) {
        if (AUSM_ARCHITECTURE_TARGET_LOG_LIMIT <= 0) {
            return;
        }
        if (++ausm$architectureTargetLogs > AUSM_ARCHITECTURE_TARGET_LOG_LIMIT) {
            return;
        }
        PipelineContext pipeline = PipelineContext.getInstance();
        IBlockState effectiveState = pipeline.effectiveBlockRenderState(blockState, world, blockPos);
        IBlockState inheritedState = pipeline.inheritedBloomRenderState(blockState, world, blockPos);
        MainMod.LOGGER.info(
                "[AUSMArchitectureTargetDiag] call={} source={} pos={} layer={} result={} state={} effective={} inherited={} emission={} inheritedEmission={} blockId={} inheritedBlockId={} contextEmission={} contextAlpha={} vlm={}/{} access={}",
                ausm$architectureTargetLogs,
                source,
                blockPos,
                MinecraftForgeClient.getRenderLayer(),
                result,
                ausm$stateName(blockState),
                ausm$stateName(effectiveState),
                ausm$stateName(inheritedState),
                pipeline.blockRenderEmission(blockState, world, blockPos),
                pipeline.blockRenderEmissionWithFramedInheritance(blockState, world, blockPos),
                pipeline.blockEntityId(blockState, world, blockPos),
                pipeline.blockEntityId(inheritedState, world, blockPos),
                BlockRenderContext.blockEmission(),
                BlockRenderContext.blockAlpha(),
                vlm1,
                vlm2,
                world != null ? world.getClass().getName() : "null"
        );
    }

    private static String ausm$stateName(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return String.valueOf(state);
        }
        ResourceLocation name = state.getBlock().getRegistryName();
        return name != null ? name.toString() : state.toString();
    }

    private void ausm$log(String source, String extra) {
        PipelineContext pipeline = PipelineContext.getInstance();
        if (!pipeline.framedBlockDiagnosticsEnabled()) {
            return;
        }
        pipeline.logFramedBlockDiagnostic(
                source,
                blockState,
                world,
                blockPos,
                MinecraftForgeClient.getRenderLayer(),
                -1,
                -1,
                null,
                extra
        );
    }
}
