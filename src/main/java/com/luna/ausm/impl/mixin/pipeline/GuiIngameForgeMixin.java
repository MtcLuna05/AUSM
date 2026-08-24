package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.client.AusmGuiRenderController;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiIngameForge.class, remap = false)
public class GuiIngameForgeMixin {
    @Inject(method = "func_175180_a(F)V", at = @At("HEAD"), require = 1)
    private void ausm$beforeForgeGameOverlay(float partialTicks, CallbackInfo ci) {
        AusmGuiRenderController.beginHud();
    }

    @Inject(method = "func_175180_a(F)V", at = @At("RETURN"), require = 1)
    private void ausm$afterForgeGameOverlay(float partialTicks, CallbackInfo ci) {
        AusmGuiRenderController.endHud();
    }
}
