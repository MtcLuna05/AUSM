package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public class GuiScreenMixin {
    private static final int WORLD_GUI_BACKGROUND = 0x44000000;

    @Shadow
    protected Minecraft mc;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true)
    private void ausm$flattenShaderlessWorldBackground(int tint, CallbackInfo ci) {
        if (ausm$drawFlatWorldBackground()) {
            ci.cancel();
        }
    }

    private boolean ausm$drawFlatWorldBackground() {
        if (this.mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(this.mc) == null) {
            return false;
        }
        if (ausm$shouldUseVanillaWorldBackground()) {
            return false;
        }

        PipelineContext context = PipelineContext.getInstance();
        boolean shaderlessWorldBackground = !context.isActive();
        if (shaderlessWorldBackground) {
            if (context.isRenderingBetterPortalsRenderPass()) {
                return false;
            }
            context.freshSkyProbe("gui-bg-before-refresh", "screen=" + getClass().getName());
            context.prepareShaderlessGuiScreenRendering();
            float partialTicks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(this.mc);
            ((EntityRendererAccessor) com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(this.mc)).ausm$setupCameraTransform(partialTicks, 2);
            com.l.ausm.impl.util.MinecraftReflectionCompat.renderSky(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(this.mc), partialTicks, 2);
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(this.mc)), new String[] {"func_78478_c", "setupOverlayRendering"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);;
            context.freshSkyProbe("gui-bg-after-refresh", "screen=" + getClass().getName());
            context.prepareShaderlessGuiScreenRendering();
            context.freshSkyProbe("gui-bg-suppress-vanilla", "screen=" + getClass().getName());
            return true;
        } else {
            context.freshSkyProbe("gui-bg-active-before-framebuffer", "screen=" + getClass().getName());
            context.prepareGuiFramebuffer();
            context.freshSkyProbe("gui-bg-active-after-framebuffer", "screen=" + getClass().getName());
        }
        context.prepareFlatGuiBackgroundRenderState();
        context.freshSkyProbe("gui-bg-before-flat-rect", "screen=" + getClass().getName());
        Gui.drawRect(0, 0, this.width, this.height, WORLD_GUI_BACKGROUND);
        context.freshSkyProbe("gui-bg-after-flat-rect", "screen=" + getClass().getName());
        return true;
    }

    private boolean ausm$shouldUseVanillaWorldBackground() {
        return "tinker_io.gui.GuiSmartOutput".equals(getClass().getName());
    }
}
