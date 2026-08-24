package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.client.AusmGuiRenderController;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public class GuiScreenMixin {
    @Inject(method = "func_146270_b(I)V", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private void ausm$drawOwnedWorldBackground(int tint, CallbackInfo ci) {
        if (AusmGuiRenderController.drawOwnedWorldBackground((GuiScreen) (Object) this)) {
            ci.cancel();
        }
    }
}
