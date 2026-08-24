package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Inject(
            method = "renderSuffocationOverlay(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ausm$skipPipelineSuffocationOverlay(TextureAtlasSprite sprite, CallbackInfo ci) {
        // Vanilla's inside-block overlay is an opaque 10%-brightness fullscreen
        // quad. In a managed world pass it turns a near-camera collision into a
        // full-scene exposure change, so leave the actual occluding geometry as
        // the only visual feedback instead.
        if (PipelineContext.getInstance().shouldSuppressSuffocationOverlay()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderFireInFirstPerson()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void ausm$restorePixelatedFireTexture(CallbackInfo ci) {
        // The fire HUD sprite is magnified from the block atlas. A leaked
        // GL_LINEAR magnification filter makes it visibly blurry; the atlas is
        // supposed to use nearest-neighbour magnification.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    @Inject(method = "renderWaterOverlayTexture(F)V", at = @At("HEAD"), cancellable = true)
    private void ausm$skipUnderwaterOverlay(float partialTicks, CallbackInfo ci) {
        if (!PipelineContext.getInstance().shouldRenderUnderwaterOverlay()) {
            ci.cancel();
        }
    }
}
