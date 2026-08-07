package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.client.model.pipeline.ForgeBlockModelRenderer;
import net.minecraftforge.client.model.pipeline.IVertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(value = ForgeBlockModelRenderer.class, remap = false)
public abstract class ForgeBlockModelRendererMixin {
    @Redirect(
            method = "render(Lnet/minecraftforge/client/model/pipeline/VertexLighterFlat;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/BufferBuilder;ZJ)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/model/BakedQuad;pipe(Lnet/minecraftforge/client/model/pipeline/IVertexConsumer;)V",
                    remap = false
            ),
            require = 2,
            remap = false
    )
    private static void ausm$pipeWithFramedMaterial(BakedQuad quad, IVertexConsumer consumer) {
        boolean framed = BlockRenderContext.isFramedMaterialOwner();
        TextureAtlasSprite sprite = framed ? MinecraftReflectionCompat.bakedQuadSprite(quad) : null;
        String spriteName = sprite != null ? MinecraftReflectionCompat.spriteIconName(sprite) : null;
            if (framed) {
                BlockRenderContext.clearQuadOverrides();
                BlockRenderContext.setQuadSprite(spriteName);
                PipelineContext.getInstance().applyFramedQuadMaterial(quad, spriteName);
            }
        try {
            if (!MinecraftReflectionCompat.bakedQuadPipe(quad, consumer)
                    && BlockRendererDispatcherHooks.FRAMED_PIPE_FAILURE_COUNT.incrementAndGet() <= 8) {
                com.l.ausm.impl.MainMod.LOGGER.error(
                        "[AUSMFramedQuadPipeFailure] quad={} consumer={} framed={}",
                        quad != null ? quad.getClass().getName() : "null",
                        consumer != null ? consumer.getClass().getName() : "null",
                        framed
                );
            }
        } finally {
            if (framed) {
                BlockRenderContext.clearQuadOverrides();
            }
        }
    }

}
