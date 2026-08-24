package com.luna.ausm.impl.mixin.pipeline;

import java.util.Map;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderManager.class)
public interface RenderManagerAccessor {
    @Accessor(value = "field_78729_o", remap = false)
    Map<Class<? extends Entity>, Render<? extends Entity>> ausm$entityRenderMap();

    @Accessor(value = "field_78725_b", remap = false)
    double ausm$renderPosX();

    @Accessor(value = "field_78726_c", remap = false)
    double ausm$renderPosY();

    @Accessor(value = "field_78723_d", remap = false)
    double ausm$renderPosZ();
}
