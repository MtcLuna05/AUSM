package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.JourneyMapDefaultSkinFilter;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "journeymap.client.render.texture.TextureImpl", remap = false)
public abstract class JourneyMapTextureImplMixin {
    @Shadow(remap = false)
    protected ResourceLocation resourceLocation;

    @Inject(method = "bindTexture", at = @At("RETURN"), require = 0, remap = false)
    private void ausm$keepDefaultPlayerSkinsPixelSharp(CallbackInfo ci) {
        if (resourceLocation == null
                || !JourneyMapDefaultSkinFilter.isDefaultPlayerSkin(resourceLocation.toString())) {
            return;
        }

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }
}
