package com.l.ausm.impl.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiFlatButton extends GuiButton {

    public GuiFlatButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
        super(buttonId, x, y, widthIn, heightIn, buttonText);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }

        boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        int background = !enabled ? 0xFF11161D : hovered ? 0xFF263548 : 0xFF18222D;
        int topLine = hovered && enabled ? 0xFF5D7894 : 0xFF384C60;
        int textColor = !enabled ? 0xFF6B7580 : hovered ? 0xFFFFFFFF : 0xFFE2E8F0;

        drawRect(x, y, x + width, y + height, background);
        drawRect(x, y, x + width, y + 1, topLine);
        drawRect(x, y + height - 1, x + width, y + height, 0xFF070B10);
        drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, textColor);
    }
}
