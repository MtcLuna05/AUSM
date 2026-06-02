package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.client.renderer.entity.RenderLightningBolt;
import net.minecraft.entity.effect.EntityLightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderLightningBolt.class)
public class RenderLightningBoltMixin {

    @Inject(method = "doRender(Lnet/minecraft/entity/effect/EntityLightningBolt;DDDFF)V", at = @At("HEAD"))
    private void ausm$beginLightning(EntityLightningBolt entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.LIGHTNING);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/effect/EntityLightningBolt;DDDFF)V", at = @At("RETURN"))
    private void ausm$endLightning(EntityLightningBolt entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }
}
