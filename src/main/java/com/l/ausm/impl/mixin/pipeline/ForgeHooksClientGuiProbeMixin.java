package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraftforge.client.ForgeHooksClient", remap = false)
public abstract class ForgeHooksClientGuiProbeMixin {
    @Inject(method = "drawScreen", at = @At("HEAD"), remap = false)
    private static void ausm$probeDrawScreenHead(GuiScreen screen, int mouseX, int mouseY, float partialTicks,
                                                 CallbackInfo ci) {
        PipelineContext.getInstance().logGuiBypassProbe("forge-draw-head");
    }

    @Inject(
            method = "drawScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiScreen;func_73863_a(IIF)V",
                    shift = At.Shift.BEFORE,
                    remap = false
            ),
            remap = false
    )
    private static void ausm$probeDrawScreenUncancelled(GuiScreen screen, int mouseX, int mouseY, float partialTicks,
                                                        CallbackInfo ci) {
        PipelineContext.getInstance().logGuiBypassProbe("forge-draw-uncancelled");
    }
}
