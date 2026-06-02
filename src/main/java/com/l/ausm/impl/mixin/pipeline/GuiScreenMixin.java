package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public class GuiScreenMixin {

    @Inject(method = "drawScreen(IIF)V", at = @At("HEAD"))
    private void ausm$beforeDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().beginGuiRendering();
    }

    @Inject(method = "drawScreen(IIF)V", at = @At("RETURN"))
    private void ausm$afterDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().finishGuiRendering();
    }
}
