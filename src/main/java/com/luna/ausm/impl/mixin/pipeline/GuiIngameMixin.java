package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.client.AusmGuiRenderController;
import com.luna.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public class GuiIngameMixin {

    @Inject(method = "renderGameOverlay(F)V", at = @At("HEAD"))
    private void ausm$beforeGameOverlay(float partialTicks, CallbackInfo ci) {
        AusmGuiRenderController.beginHud();
    }

    @Inject(method = "renderGameOverlay(F)V", at = @At("RETURN"))
    private void ausm$afterGameOverlay(float partialTicks, CallbackInfo ci) {
        AusmGuiRenderController.endHud();
    }

    @Inject(method = "renderVignette(FLnet/minecraft/client/gui/ScaledResolution;)V", at = @At("HEAD"), cancellable = true)
    private void ausm$skipVanillaVignette(float lightLevel, ScaledResolution scaledResolution, CallbackInfo ci) {
        if (!PipelineContext.getInstance().shouldRenderVignette()) {
            ci.cancel();
        }
    }
}
