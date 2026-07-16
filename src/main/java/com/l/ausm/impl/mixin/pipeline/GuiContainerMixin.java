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
        PipelineContext context = PipelineContext.getInstance();
        if (!context.isActive()) {
            return;
        }
        ausm$prepareCompatContainerGui();
        context.beginGuiScreenRendering();
    }

    @Inject(method = "drawScreen(IIF)V", at = @At("RETURN"))
    private void ausm$finishContainerGui(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isActive()) {
            context.finishGuiScreenRendering();
        }
    }

    private void ausm$prepareCompatContainerGui() {
        if (!"tinker_io.gui.GuiSmartOutput".equals(getClass().getName())) {
            return;
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179106_n", "disableFog"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);;
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
