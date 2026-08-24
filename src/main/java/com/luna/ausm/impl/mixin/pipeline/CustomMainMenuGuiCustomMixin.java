package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "lumien.custommainmenu.gui.GuiCustom", remap = false)
public class CustomMainMenuGuiCustomMixin {
    @Inject(
            method = "func_73863_a",
            at = @At(
                    value = "INVOKE",
                    target = "Llumien/custommainmenu/gui/GuiCustom;drawBackground(Llumien/custommainmenu/lib/MODE;)V",
                    shift = At.Shift.BEFORE,
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private void ausm$forceBackgroundTextureColor(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, ausm$currentAlpha());
    }

    @Redirect(
            method = "func_73863_a",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor3f(FFF)V", remap = false),
            require = 0,
            remap = false
    )
    private void ausm$syncRawColorWithGlStateManager(float red, float green, float blue) {
        MinecraftReflectionCompat.glStateColor(red, green, blue, 1.0F);
        GL11.glColor4f(red, green, blue, 1.0F);
    }

    private float ausm$currentAlpha() {
        try {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, buffer);
            float alpha = buffer.get(3);
            if (Float.isNaN(alpha) || alpha < 0.0F || alpha > 1.0F) {
                return 1.0F;
            }
            return alpha;
        } catch (RuntimeException | LinkageError ignored) {
            return 1.0F;
        }
    }
}
