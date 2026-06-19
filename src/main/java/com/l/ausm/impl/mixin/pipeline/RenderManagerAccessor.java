package com.l.ausm.impl.mixin.pipeline;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RenderManager.class)
public interface RenderManagerAccessor {
    @Accessor("entityRenderMap")
    Map<Class<? extends Entity>, Render<? extends Entity>> ausm$entityRenderMap();

    @Accessor("renderPosX")
    double ausm$renderPosX();

    @Accessor("renderPosY")
    double ausm$renderPosY();

    @Accessor("renderPosZ")
    double ausm$renderPosZ();
}
