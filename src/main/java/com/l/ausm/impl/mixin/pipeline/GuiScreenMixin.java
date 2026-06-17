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
    private static final int WORLD_GUI_BACKGROUND = 0x88000000;

    @Shadow
    protected Minecraft mc;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Inject(method = "drawDefaultBackground", at = @At("HEAD"), cancellable = true)
    private void ausm$flattenShaderlessDefaultBackground(CallbackInfo ci) {
        if (ausm$drawFlatWorldBackground()) {
            ci.cancel();
        }
    }

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

        PipelineContext.getInstance().prepareGuiFramebuffer();
        Gui.drawRect(0, 0, this.width, this.height, WORLD_GUI_BACKGROUND);
        return true;
    }

    private boolean ausm$shouldUseVanillaWorldBackground() {
        return "tinker_io.gui.GuiSmartOutput".equals(getClass().getName());
    }
}
