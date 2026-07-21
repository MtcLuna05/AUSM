package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public class GuiScreenMixin {
    private static final int WORLD_GUI_BACKGROUND = 0x44000000;

    @Shadow(remap = false)
    protected Minecraft field_146297_k;

    @Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true)
    private void ausm$flattenShaderlessWorldBackground(int tint, CallbackInfo ci) {
        if (ausm$drawFlatWorldBackground()) {
            ci.cancel();
        }
    }

    private boolean ausm$drawFlatWorldBackground() {
        return false;
    }

    private boolean ausm$shouldUseVanillaWorldBackground() {
        String className = getClass().getName();
        return className.startsWith("com.l.ausm.impl.client.gui.")
                || "tinker_io.gui.GuiSmartOutput".equals(className);
    }
}
