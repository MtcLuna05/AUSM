package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiContainer.class)
public class GuiContainerMixin {
    @Inject(method = "drawScreen(IIF)V", at = @At("HEAD"))
    private void ausm$beginContainerGui(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ausm$prepareCompatContainerGui();
        PipelineContext.getInstance().beginGuiRendering();
    }

    @Inject(method = "drawScreen(IIF)V", at = @At("RETURN"))
    private void ausm$finishContainerGui(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().finishGuiRendering();
    }

    private void ausm$prepareCompatContainerGui() {
        if (!"tinker_io.gui.GuiSmartOutput".equals(getClass().getName())) {
            return;
        }

        OpenGlHelper.glUseProgram(0);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
