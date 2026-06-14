package com.l.ausm.impl.mixin.compat;

import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        targets = {
                "com.shinoow.abyssalcraft.client.render.entity.RenderShadowMonster",
                "com.shinoow.abyssalcraft.client.render.entity.RenderShadowCreature",
                "com.shinoow.abyssalcraft.client.render.entity.RenderShadowBeast"
        },
        remap = false
)
public class AbyssalCraftShadowEntityRendererMixin {
    @Inject(method = "func_77036_a(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V", at = @At("HEAD"), remap = false)
    private void ausm$beginShadowEntityModel(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                                             float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                             CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.beginPhase(WorldRenderingPhase.ENTITIES_TRANSLUCENT);
        context.applyNonZeroAlphaTestForCurrentPass();
    }

    @Inject(method = "func_77036_a(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V", at = @At("RETURN"), remap = false)
    private void ausm$endShadowEntityModel(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                                           float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                           CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }
}
