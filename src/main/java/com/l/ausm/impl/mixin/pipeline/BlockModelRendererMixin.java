package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.AppliedEnergisticsFacadeQuadMetadata;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {
    @Unique
    private static final ThreadLocal<AppliedEnergisticsFacadeQuadMetadata.Metadata> ausm$currentQuadMetadata = new ThreadLocal<>();

    @Redirect(
            method = {"renderQuadsSmooth", "renderQuadsFlat"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;putColorMultiplier(FFFI)V")
    )
    private void ausm$separateAoAfterColorMultiplier(BufferBuilder bufferBuilder, float redMultiplier, float greenMultiplier, float blueMultiplier, int vertexIndex) {
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((bufferBuilder), new String[] {"func_178978_a", "putColorMultiplier"},
                new Class<?>[] {float.class, float.class, float.class, int.class}, (redMultiplier), (greenMultiplier), (blueMultiplier), (vertexIndex));;
        if (vertexIndex == 1) {
            ausm$currentQuadMetadata.remove();
            BlockRenderContext.clearQuadOverrides();
        }
    }

    @Redirect(
            method = {"renderQuadsSmooth", "renderQuadsFlat"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/model/BakedQuad;getVertexData()[I")
    )
    private int[] ausm$setQuadEmissionFromSprite(BakedQuad quad) {
        AppliedEnergisticsFacadeQuadMetadata.Metadata metadata = AppliedEnergisticsFacadeQuadMetadata.get(quad);
        ausm$currentQuadMetadata.set(metadata);
        if (metadata != null) {
            if (metadata.hasBlockMetadata()) {
                BlockRenderContext.setQuadBlockMetadata(
                        metadata.blockEntityId(),
                        metadata.renderType(),
                        metadata.metadata(),
                        metadata.emission()
                );
            }
            return com.l.ausm.impl.util.MinecraftReflectionCompat.bakedQuadVertexData(quad);
        }
        BlockRenderContext.clearQuadOverrides();
        TextureAtlasSprite sprite = quad != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bakedQuadSprite(quad) : null;
        BlockRenderContext.setQuadSprite(sprite != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.spriteIconName(sprite) : null);
        return com.l.ausm.impl.util.MinecraftReflectionCompat.bakedQuadVertexData(quad);
    }

    @Redirect(
            method = {"renderQuadsSmooth", "renderQuadsFlat"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;addVertexData([I)V")
    )
    private void ausm$addVertexDataWithQuadEmissionClear(BufferBuilder bufferBuilder, int[] vertexData) {
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((bufferBuilder), new String[] {"func_178981_a", "addVertexData"}, new Class<?>[] {int[].class}, (vertexData));;
    }

    @Redirect(
            method = {"renderQuadsSmooth", "renderQuadsFlat"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/color/BlockColors;colorMultiplier(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;I)I")
    )
    private int ausm$useAe2CableBusTintFallback(BlockColors blockColors, IBlockState state, IBlockAccess blockAccess,
                                                BlockPos pos, int tintIndex) {
        int color = com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((blockColors), new String[] {"func_186724_a", "colorMultiplier"}, new Class<?>[] {net.minecraft.block.state.IBlockState.class, net.minecraft.world.IBlockAccess.class, net.minecraft.util.math.BlockPos.class, int.class}, 0xFFFFFF, (state), (blockAccess), (pos), (tintIndex));
        AppliedEnergisticsFacadeQuadMetadata.Metadata metadata = ausm$currentQuadMetadata.get();
        int ae2Color = metadata != null ? metadata.tintColor(tintIndex) : -1;
        if (ae2Color >= 0) {
            return ae2Color & 0xFFFFFF;
        }
        return color;
    }

    @Redirect(
            method = {"renderQuadsSmooth", "renderQuadsFlat"},
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/model/pipeline/LightUtil;diffuseLight(Lnet/minecraft/util/EnumFacing;)F")
    )
    private float ausm$disableDirectionalShading(EnumFacing side) {
        if (PipelineContext.getInstance().shouldDisableDirectionalShading()) {
            return 1.0f;
        }
        return LightUtil.diffuseLight(side);
    }
}
