package com.l.ausm.impl.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.util.List;

final class GuiControlHints {
    private static final String LABEL_KEY = "gui.ausm.controls";
    private static final String ESC_BACK_KEY = "tooltip.ausm.controls.esc";
    private static final String SHIFT_ESC_CLOSE_KEY = "tooltip.ausm.controls.shift_esc";
    private static final int HINT_MARGIN = 10;
    private static final int HINT_TOP = 10;
    private static final int HINT_HEIGHT = 16;
    private static final int HINT_PADDING_X = 6;
    private static final int FOCUS_COLOR = 0xFFFFD27D;

    private GuiControlHints() {
    }

    static List<String> escapeTooltip() {
        return List.of(I18n.format(ESC_BACK_KEY), I18n.format(SHIFT_ESC_CLOSE_KEY));
    }

    static void drawEscapeHintLabel(FontRenderer fontRenderer, int screenWidth) {
        String label = I18n.format(LABEL_KEY);
        int left = escapeHintLeft(fontRenderer, screenWidth, label);
        int right = screenWidth - HINT_MARGIN;
        Gui.drawRect(left, HINT_TOP, right, HINT_TOP + HINT_HEIGHT, 0x66101418);
        Gui.drawRect(left, HINT_TOP, right, HINT_TOP + 1, 0xFF42566D);
        fontRenderer.drawString(label, left + HINT_PADDING_X, HINT_TOP + 4, 0xFFC8D3E0);
    }

    static boolean isMouseOverEscapeHint(FontRenderer fontRenderer, int screenWidth, int mouseX, int mouseY) {
        String label = I18n.format(LABEL_KEY);
        int left = escapeHintLeft(fontRenderer, screenWidth, label);
        int right = screenWidth - HINT_MARGIN;
        return mouseX >= left && mouseX < right
                && mouseY >= HINT_TOP && mouseY < HINT_TOP + HINT_HEIGHT;
    }

    static void drawFocusedButtonOutline(GuiButton button) {
        if (button == null) {
            return;
        }

        Gui.drawRect(button.x - 2, button.y - 2, button.x + button.width + 2, button.y - 1, FOCUS_COLOR);
        Gui.drawRect(button.x - 2, button.y + button.height + 1, button.x + button.width + 2, button.y + button.height + 2, FOCUS_COLOR);
        Gui.drawRect(button.x - 2, button.y - 2, button.x - 1, button.y + button.height + 2, FOCUS_COLOR);
        Gui.drawRect(button.x + button.width + 1, button.y - 2, button.x + button.width + 2, button.y + button.height + 2, FOCUS_COLOR);
    }

    static boolean isFocusable(GuiButton button) {
        return button.visible && button.enabled;
    }

    static boolean isMouseOverButton(GuiButton button, int mouseX, int mouseY) {
        return button != null && button.visible
                && mouseX >= button.x && mouseY >= button.y
                && mouseX < button.x + button.width && mouseY < button.y + button.height;
    }

    static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static int escapeHintLeft(FontRenderer fontRenderer, int screenWidth, String label) {
        int width = fontRenderer.getStringWidth(label) + HINT_PADDING_X * 2;
        return Math.max(4, screenWidth - HINT_MARGIN - width);
    }
}
