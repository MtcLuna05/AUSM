package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderManager.class)
public class RenderManagerMixin {

    @Inject(
            method = "renderEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V", shift = At.Shift.BEFORE)
    )
    private void ausm$beforeRenderEntity(Entity entity, double x, double y, double z, float entityYaw, float partialTicks, boolean debugBoundingBox, CallbackInfo ci) {
        PipelineContext.getInstance().setCurrentEntity(entity);
    }

    @Inject(
            method = "renderEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V", shift = At.Shift.AFTER)
    )
    private void ausm$afterRenderEntity(Entity entity, double x, double y, double z, float entityYaw, float partialTicks, boolean debugBoundingBox, CallbackInfo ci) {
        PipelineContext.getInstance().clearCurrentEntity();
    }

    @Inject(
            method = "renderMultipass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;renderMultipass(Lnet/minecraft/entity/Entity;DDDFF)V", shift = At.Shift.BEFORE)
    )
    private void ausm$beforeRenderMultipass(Entity entity, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().setCurrentEntity(entity);
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.ENTITIES_TRANSLUCENT);
    }

    @Inject(
            method = "renderMultipass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;renderMultipass(Lnet/minecraft/entity/Entity;DDDFF)V", shift = At.Shift.AFTER)
    )
    private void ausm$afterRenderMultipass(Entity entity, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
        PipelineContext.getInstance().clearCurrentEntity();
    }
}
