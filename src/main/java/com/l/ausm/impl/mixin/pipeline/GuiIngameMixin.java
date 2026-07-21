package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.client.ShaderCompileNotifications;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.Minecraft;
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
        if (ausm$isHudHidden()) {
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        context.logGuiBypassProbe("hud-head-before-bypass");
        context.prepareBypassedGuiScreenDrawState();
        context.logGuiBypassProbe("hud-head-after-bypass");
    }

    @Inject(method = "renderGameOverlay(F)V", at = @At("RETURN"))
    private void ausm$afterGameOverlay(float partialTicks, CallbackInfo ci) {
        if (ausm$isHudHidden()) {
            return;
        }
        PipelineContext.getInstance().logGuiBypassProbe("hud-return");
        ShaderCompileNotifications.renderOverlay(new ScaledResolution(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()));
    }

    @Inject(
            method = "renderGameOverlay(F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiIngame;renderHotbar(Lnet/minecraft/client/gui/ScaledResolution;F)V"
            )
    )
    private void ausm$beforeHotbar(float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().logGuiBypassProbe("hud-before-hotbar");
    }

    @Inject(
            method = "renderGameOverlay(F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiSubtitleOverlay;renderSubtitles(Lnet/minecraft/client/gui/ScaledResolution;)V"
            )
    )
    private void ausm$beforeSubtitles(float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().logGuiBypassProbe("hud-before-subtitles");
    }

    @Inject(method = "renderVignette(FLnet/minecraft/client/gui/ScaledResolution;)V", at = @At("HEAD"), cancellable = true)
    private void ausm$skipVanillaVignette(float lightLevel, ScaledResolution scaledResolution, CallbackInfo ci) {
        if (!PipelineContext.getInstance().shouldRenderVignette()) {
            ci.cancel();
        }
    }

    private boolean ausm$isHudHidden() {
        Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return minecraft != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(minecraft) == null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(minecraft) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(minecraft));
    }
}
