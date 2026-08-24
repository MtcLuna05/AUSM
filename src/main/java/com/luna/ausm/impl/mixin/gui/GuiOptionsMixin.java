package com.luna.ausm.impl.mixin.gui;

import com.luna.ausm.impl.client.gui.GuiShaders;
import com.luna.ausm.impl.client.gui.GuiOptionsButtonPlacement;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the shader pack entry to the vanilla Options screen, above skin customization. */
@Mixin(GuiOptions.class)
public class GuiOptionsMixin extends GuiScreen {
    private static final int AUSM_SHADER_OPTIONS_ID = 301;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void ausm$addShaderOptionsButton(CallbackInfo ci) {
        List<GuiButton> buttons = MinecraftReflectionCompat.guiScreenButtons(this);
        GuiButton skinButton = null;
        List<GuiOptionsButtonPlacement.Placement> occupiedButtons = new ArrayList<>(buttons.size());
        for (GuiButton button : buttons) {
            if (MinecraftReflectionCompat.guiButtonId(button) == 110) {
                skinButton = button;
            }
            occupiedButtons.add(new GuiOptionsButtonPlacement.Placement(
                    MinecraftReflectionCompat.guiButtonX(button),
                    MinecraftReflectionCompat.guiButtonY(button),
                    MinecraftReflectionCompat.guiButtonWidth(button),
                    MinecraftReflectionCompat.guiButtonHeight(button)
            ));
        }
        if (skinButton == null) {
            return;
        }

        GuiOptionsButtonPlacement.Placement shaderButton = GuiOptionsButtonPlacement.findAboveSkin(
                new GuiOptionsButtonPlacement.Placement(
                        MinecraftReflectionCompat.guiButtonX(skinButton),
                        MinecraftReflectionCompat.guiButtonY(skinButton),
                        MinecraftReflectionCompat.guiButtonWidth(skinButton),
                        MinecraftReflectionCompat.guiButtonHeight(skinButton)
                ),
                this.width,
                occupiedButtons
        );
        if (shaderButton != null) {
            buttons.add(new GuiButton(
                    AUSM_SHADER_OPTIONS_ID,
                    shaderButton.x(),
                    shaderButton.y(),
                    shaderButton.width(),
                    shaderButton.height(),
                    "Shader Options..."
            ));
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void ausm$openShaderOptions(GuiButton button, CallbackInfo ci) {
        if (MinecraftReflectionCompat.guiButtonId(button) != AUSM_SHADER_OPTIONS_ID) {
            return;
        }
        MinecraftReflectionCompat.displayGuiScreen(
                MinecraftReflectionCompat.guiScreenMinecraft(this), new GuiShaders(this));
        ci.cancel();
    }
}
