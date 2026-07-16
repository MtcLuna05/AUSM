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
    @Inject(method = "func_175180_a(F)V", at = @At("HEAD"), require = 1)
    private void ausm$beforeForgeGameOverlay(float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (ausm$isHudHidden()) {
            return;
        }
        context.renderShaderlessBloomBeforeGui();
        context.beginGuiRendering();
    }

    @Inject(method = "func_175180_a(F)V", at = @At("RETURN"), require = 1)
    private void ausm$afterForgeGameOverlay(float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (ausm$isHudHidden()) {
            return;
        }
        context.finishGuiRendering();
        ShaderCompileNotifications.renderOverlay(new ScaledResolution(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()));
    }

    private boolean ausm$isHudHidden() {
        Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return minecraft != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(minecraft) == null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(minecraft) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(minecraft));
    }
}
