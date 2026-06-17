package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {
    @Redirect(
            method = {"renderQuadsSmooth", "renderQuadsFlat"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;putColorMultiplier(FFFI)V")
    )
    private void ausm$separateAoAfterColorMultiplier(BufferBuilder bufferBuilder, float redMultiplier, float greenMultiplier, float blueMultiplier, int vertexIndex) {
        int emission = BlockRenderContext.blockEmission();
        if (emission > 0) {
            float emissiveWeight = Math.min(1.0f, emission / 15.0f);
            redMultiplier = brightenAoMultiplier(redMultiplier, emissiveWeight);
            greenMultiplier = brightenAoMultiplier(greenMultiplier, emissiveWeight);
            blueMultiplier = brightenAoMultiplier(blueMultiplier, emissiveWeight);
        }
        bufferBuilder.putColorMultiplier(redMultiplier, greenMultiplier, blueMultiplier, vertexIndex);
    }

    @Redirect(
            method = {"renderQuadsSmooth", "renderQuadsFlat"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/model/BakedQuad;getVertexData()[I")
    )
    private int[] ausm$setQuadEmissionFromSprite(BakedQuad quad) {
        TextureAtlasSprite sprite = quad != null ? quad.getSprite() : null;
        BlockRenderContext.setQuadSprite(sprite != null ? sprite.getIconName() : null);
        return quad.getVertexData();
    }

    @Redirect(
            method = {"renderQuadsSmooth", "renderQuadsFlat"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;addVertexData([I)V")
    )
    private void ausm$addVertexDataWithQuadEmissionClear(BufferBuilder bufferBuilder, int[] vertexData) {
        try {
            bufferBuilder.addVertexData(vertexData);
        } finally {
            BlockRenderContext.clearQuadEmissionOverride();
        }
    }

    @Redirect(
            method = {"renderQuadsSmooth", "renderQuadsFlat"},
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/model/pipeline/LightUtil;diffuseLight(Lnet/minecraft/util/EnumFacing;)F")
    )
    private float ausm$disableDirectionalShading(EnumFacing side) {
        if (BlockRenderContext.blockEmission() > 0) {
            return 1.0f;
        }
        if (PipelineContext.getInstance().shouldDisableDirectionalShading()) {
            return 1.0f;
        }
        return LightUtil.diffuseLight(side);
    }

    private static float brightenAoMultiplier(float multiplier, float weight) {
        return multiplier + (1.0f - multiplier) * weight;
    }
}
