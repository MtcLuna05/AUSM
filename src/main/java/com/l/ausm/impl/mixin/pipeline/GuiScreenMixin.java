package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
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
    private static final int SIMPLE_VOID_WORLD_DIMENSION_ID = 43;
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
                || this.mc.world == null) {
            return false;
        }
        if (ausm$shouldUseVanillaWorldBackground()) {
            return false;
        }

        PipelineContext context = PipelineContext.getInstance();
        if (!context.isActive() && !ausm$isSimpleVoidWorld()) {
            return false;
        }
        boolean shaderlessVoidBackground = !context.isActive();
        if (shaderlessVoidBackground) {
            context.prepareShaderlessGuiScreenRendering();
        } else {
            context.prepareGuiFramebuffer();
        }
        context.prepareFlatGuiBackgroundRenderState();
        Gui.drawRect(0, 0, this.width, this.height, WORLD_GUI_BACKGROUND);
        if (shaderlessVoidBackground) {
            context.prepareShaderlessGuiScreenRendering();
        }
        return true;
    }

    private boolean ausm$isSimpleVoidWorld() {
        return this.mc.world.provider != null
                && this.mc.world.provider.getDimension() == SIMPLE_VOID_WORLD_DIMENSION_ID;
    }

    private boolean ausm$shouldUseVanillaWorldBackground() {
        return "tinker_io.gui.GuiSmartOutput".equals(getClass().getName());
    }
}
