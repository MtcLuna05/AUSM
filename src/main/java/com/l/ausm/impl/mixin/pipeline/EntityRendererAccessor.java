package com.l.ausm.impl.mixin.pipeline;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Accessor("lightmapTexture")
    DynamicTexture ausm$getLightmapTexture();
}
