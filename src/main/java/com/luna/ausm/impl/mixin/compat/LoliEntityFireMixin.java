package com.luna.ausm.impl.mixin.compat;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zone.rong.loliasm.client.sprite.ondemand.IAnimatedSpriteActivator;

@Mixin(Render.class)
public class LoliEntityFireMixin {
    @Redirect(
            method = "renderEntityOnFire(Lnet/minecraft/entity/Entity;DDDF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureMap;getAtlasSprite(Ljava/lang/String;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;")
    )
    private TextureAtlasSprite ausm$activateEntityFireSprite(TextureMap atlas, String name) {
        TextureAtlasSprite sprite = atlas.getAtlasSprite(name);
        // Entity flames need their own visibility marker; no terrain chunk may use them.
        if (sprite instanceof IAnimatedSpriteActivator activator) {
            activator.setActive(true);
        }
        return sprite;
    }
}
