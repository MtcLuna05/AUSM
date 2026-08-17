package com.l.ausm.impl.client.gui;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;

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
        return List.of(MinecraftReflectionCompat.i18nFormat(ESC_BACK_KEY),
                MinecraftReflectionCompat.i18nFormat(SHIFT_ESC_CLOSE_KEY));
    }

    static void drawEscapeHintLabel(FontRenderer fontRenderer, int screenWidth) {
        String label = MinecraftReflectionCompat.i18nFormat(LABEL_KEY);
        int left = escapeHintLeft(fontRenderer, screenWidth, label);
        int right = screenWidth - HINT_MARGIN;
        MinecraftReflectionCompat.guiDrawRect(left, HINT_TOP, right, HINT_TOP + HINT_HEIGHT, 0x66101418);
        MinecraftReflectionCompat.guiDrawRect(left, HINT_TOP, right, HINT_TOP + 1, 0xFF42566D);
        MinecraftReflectionCompat.fontDrawString(fontRenderer, label, left + HINT_PADDING_X, HINT_TOP + 4, 0xFFC8D3E0);
    }

    static boolean isMouseOverEscapeHint(FontRenderer fontRenderer, int screenWidth, int mouseX, int mouseY) {
        String label = MinecraftReflectionCompat.i18nFormat(LABEL_KEY);
        int left = escapeHintLeft(fontRenderer, screenWidth, label);
        int right = screenWidth - HINT_MARGIN;
        return mouseX >= left && mouseX < right
                && mouseY >= HINT_TOP && mouseY < HINT_TOP + HINT_HEIGHT;
    }

    static void drawFocusedButtonOutline(GuiButton button) {
        if (button == null) {
            return;
        }

        int x = MinecraftReflectionCompat.guiButtonX(button);
        int y = MinecraftReflectionCompat.guiButtonY(button);
        int width = MinecraftReflectionCompat.guiButtonWidth(button);
        int height = MinecraftReflectionCompat.guiButtonHeight(button);
        MinecraftReflectionCompat.guiDrawRect(x - 2, y - 2, x + width + 2, y - 1, FOCUS_COLOR);
        MinecraftReflectionCompat.guiDrawRect(x - 2, y + height + 1, x + width + 2, y + height + 2, FOCUS_COLOR);
        MinecraftReflectionCompat.guiDrawRect(x - 2, y - 2, x - 1, y + height + 2, FOCUS_COLOR);
        MinecraftReflectionCompat.guiDrawRect(x + width + 1, y - 2, x + width + 2, y + height + 2, FOCUS_COLOR);
    }

    static boolean isFocusable(GuiButton button) {
        return MinecraftReflectionCompat.guiButtonVisible(button)
                && MinecraftReflectionCompat.guiButtonEnabled(button);
    }

    static boolean isMouseOverButton(GuiButton button, int mouseX, int mouseY) {
        if (button == null || !MinecraftReflectionCompat.guiButtonVisible(button)) {
            return false;
        }
        int x = MinecraftReflectionCompat.guiButtonX(button);
        int y = MinecraftReflectionCompat.guiButtonY(button);
        return mouseX >= x && mouseY >= y
                && mouseX < x + MinecraftReflectionCompat.guiButtonWidth(button)
                && mouseY < y + MinecraftReflectionCompat.guiButtonHeight(button);
    }

    static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static int escapeHintLeft(FontRenderer fontRenderer, int screenWidth, String label) {
        int width = MinecraftReflectionCompat.fontStringWidth(fontRenderer, label) + HINT_PADDING_X * 2;
        return Math.max(4, screenWidth - HINT_MARGIN - width);
    }
}
