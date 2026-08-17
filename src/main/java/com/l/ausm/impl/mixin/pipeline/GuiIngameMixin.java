package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.client.ShaderCompileNotifications;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
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
        context.beginGuiItemRenderScope();
        context.prepareBypassedGuiScreenDrawState();
    }

    @Inject(method = "renderGameOverlay(F)V", at = @At("RETURN"))
    private void ausm$afterGameOverlay(float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endGuiItemRenderScope();
        if (ausm$isHudHidden()) {
            return;
        }
        ShaderCompileNotifications.renderOverlay(new ScaledResolution(MinecraftReflectionCompat.minecraft()));
    }

    @Inject(method = "renderVignette(FLnet/minecraft/client/gui/ScaledResolution;)V", at = @At("HEAD"), cancellable = true)
    private void ausm$skipVanillaVignette(float lightLevel, ScaledResolution scaledResolution, CallbackInfo ci) {
        if (!PipelineContext.getInstance().shouldRenderVignette()) {
            ci.cancel();
        }
    }

    private boolean ausm$isHudHidden() {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        return minecraft != null
                && MinecraftReflectionCompat.currentScreen(minecraft) == null
                && MinecraftReflectionCompat.gameSettings(minecraft) != null
                && MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(minecraft));
    }
}
