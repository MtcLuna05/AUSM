package com.luna.ausm.impl.mixin.pipeline;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Accessor(value = "field_78513_d", remap = false)
    DynamicTexture ausm$getLightmapTexture();

    @Accessor(value = "field_78536_aa", remap = false)
    void ausm$setLightmapUpdateNeeded(boolean needed);

    @Invoker(value = "func_78472_g", remap = false)
    void ausm$updateLightmap(float partialTicks);

    @Invoker(value = "func_78479_a", remap = false)
    void ausm$setupCameraTransform(float partialTicks, int pass);
}
