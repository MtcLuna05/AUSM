package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.BitSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.BlockModelRenderer$AmbientOcclusionFace")
public class AmbientOcclusionFaceMixin {
    @Inject(method = "updateVertexBrightness", at = @At("RETURN"))
    private void ausm$captureSeparateAo(IBlockAccess blockAccess, IBlockState state, BlockPos centerPos, EnumFacing direction, float[] faceShape, BitSet shapeState, CallbackInfo ci) {
        BlockRenderContext.setQuadAoIfEligible(
                MinecraftReflectionCompat.ambientOcclusionFaceVertexColorMultiplier(this));
    }
}
