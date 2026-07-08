package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Render.class)
public class RenderMixin {
    @Shadow(remap = false)
    private void func_76975_c(Entity entity, double x, double y, double z, float shadowAlpha, float partialTicks) {
    }

    @Redirect(
            method = "func_76979_b",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;func_76975_c(Lnet/minecraft/entity/Entity;DDDFF)V")
    )
    private void ausm$suppressVanillaEntityShadow(Render<?> renderer, Entity entity, double x, double y, double z, float shadowAlpha, float partialTicks) {
        if (!PipelineContext.getInstance().shouldDisableVanillaEntityShadows()) {
            func_76975_c(entity, x, y, z, shadowAlpha, partialTicks);
        }
    }
}
