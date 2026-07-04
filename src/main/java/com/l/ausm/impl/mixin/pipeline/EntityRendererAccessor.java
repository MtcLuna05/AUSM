package com.l.ausm.impl.mixin.pipeline;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Accessor("lightmapTexture")
    DynamicTexture ausm$getLightmapTexture();

    @Accessor("lightmapUpdateNeeded")
    void ausm$setLightmapUpdateNeeded(boolean needed);

    @Invoker("updateLightmap")
    void ausm$updateLightmap(float partialTicks);

    @Invoker("setupCameraTransform")
    void ausm$setupCameraTransform(float partialTicks, int pass);
}
