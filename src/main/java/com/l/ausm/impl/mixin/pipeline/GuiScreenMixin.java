package com.l.ausm.impl.mixin.pipeline;

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

        // Preserve the rendered world behind all screens. Recovery blits can be
        // unavailable for custom shader screens and otherwise produce a black base.
        return false;
    }

    private boolean ausm$shouldUseVanillaWorldBackground() {
        return "tinker_io.gui.GuiSmartOutput".equals(getClass().getName());
    }
}
