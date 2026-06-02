package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiContainer.class)
public class GuiContainerMixin {

    @Inject(method = "drawScreen(IIF)V", at = @At("HEAD"))
    private void ausm$beforeDrawContainer(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().beginGuiRendering();
    }

    @Inject(method = "drawScreen(IIF)V", at = @At("RETURN"))
    private void ausm$afterDrawContainer(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().finishGuiRendering();
    }
}
