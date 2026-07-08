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
        if (contextEmission <= 0 || AusmBloomLayer.isBloomLayer(com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer())) {
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
        BlockRenderContext.setRenderType((short) (contextState != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderTypeOrdinal(contextState) : -1));
        BlockRenderContext.setMetadata(pipeline.blockMetadata(blockState, world, blockPos));
        if (blockPos != null) {
            BlockRenderContext.setLocalBlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(blockPos), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(blockPos), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(blockPos));
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
        // Diagnostic disabled.
    }

    private static String ausm$stateName(IBlockState state) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null) {
            return String.valueOf(state);
        }
        ResourceLocation name = com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state));
        return name != null ? name.toString() : state.toString();
    }

    private void ausm$log(String source, String extra) {
        // Diagnostic disabled.
    }
}
