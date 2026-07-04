package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.client.ShaderCompileNotifications;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiIngameForge.class, remap = false)
public class GuiIngameForgeMixin {
    @Inject(method = "renderGameOverlay(F)V", at = @At("HEAD"), cancellable = true)
    private void ausm$beforeForgeGameOverlay(float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().probeShaderlessSkyGuiState("forge-overlay-head");
        if (ausm$isHudHidden()) {
            PipelineContext.getInstance().probeShaderlessSkyGuiState("forge-overlay-hidden");
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldDeferIngameHud()) {
            context.probeShaderlessSkyGuiState("forge-overlay-deferred");
            ci.cancel();
            return;
        }

        context.renderShaderlessBloomBeforeGui();
        context.beginGuiRendering();
    }

    @Inject(method = "renderGameOverlay(F)V", at = @At("RETURN"))
    private void ausm$afterForgeGameOverlay(float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().probeShaderlessSkyGuiState("forge-overlay-return");
        if (ausm$isHudHidden()) {
            return;
        }
        PipelineContext.getInstance().finishGuiRendering();
        ShaderCompileNotifications.renderOverlay(new ScaledResolution(Minecraft.getMinecraft()));
    }

    private boolean ausm$isHudHidden() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null
                && minecraft.currentScreen == null
                && minecraft.gameSettings != null
                && minecraft.gameSettings.hideGUI;
    }
}
