package com.l.ausm.impl.mixin.gui;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.client.gui.GuiShaders;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add the "Shaders..." button to the Vanilla Video Settings screen.
 */
@Mixin(GuiVideoSettings.class)
public class GuiVideoSettingsMixin extends GuiScreen {

    @Inject(method = "initGui", at = @At("RETURN"))
    private void onInitGui(CallbackInfo ci) {
        int buttonId = 300; 

        // Vanilla GuiVideoSettings in 1.12.2 uses a GuiOptionsRowList for the options grid, 
        // NOT individual buttons in this.buttonList (unlike standard Option screens).
        // The ONLY button in this.buttonList in GuiVideoSettings is the "Done" button (id 200).
        // Because of the scrolling list, placing a button static on the screen must be carefully positioned 
        // above the "Done" button so it doesn't overlap the scroll list.

        GuiButton doneButton = null;
        for (GuiButton btn : this.buttonList) {
            if (btn.id == 200) {
                doneButton = btn;
                break;
            }
        }

        if (doneButton != null) {
            // "Done" button is width / 2 - 100, height - 27
            // Let's place the Shaders button directly to the right of the Done button,
            // or next to it, shrinking the Done button slightly to fit both on the bottom row.
            
            // Re-arrange the bottom row to fit both [Shaders...] and [Done]
            // Standard button width is 200, half is 150.
            
            // Move Done button to the right half
            doneButton.width = 150;
            doneButton.x = this.width / 2 + 5;
            
            // Add Shaders button to the left half
            int x = this.width / 2 - 155;
            int y = doneButton.y; 
            
            GuiButton shadersButton = new GuiButton(buttonId, x, y, 150, 20, "Shaders...");
            this.buttonList.add(shadersButton);
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == 300) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.displayGuiScreen(this.mc, new GuiShaders(this));
            ci.cancel();
        }
    }
}