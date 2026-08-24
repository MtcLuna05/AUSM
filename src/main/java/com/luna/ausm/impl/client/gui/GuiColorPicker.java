package com.luna.ausm.impl.client.gui;

import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.awt.Color;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** In-game RGB/HSV colour picker with a normal pasteable hexadecimal field. */
public final class GuiColorPicker extends MappingSafeGuiScreen {
    public interface ColorReceiver {
        void accept(int rgb);
    }

    private static final int ID_DONE = 200;
    private static final int PICKER_MIN_SIZE = 96;
    private static final int PICKER_MAX_SIZE = 156;
    private final GuiScreen parent;
    private final ColorReceiver receiver;
    private final GuiTextField[] rgbFields = new GuiTextField[3];
    private final GuiTextField[] hsvFields = new GuiTextField[3];
    private GuiTextField hexField;
    private int red;
    private int green;
    private int blue;
    private int hue;
    private int saturation;
    private int brightness;
    private boolean draggingColorSquare;
    private boolean draggingBrightness;

    public GuiColorPicker(GuiScreen parent, int initialColor, ColorReceiver receiver) {
        this.parent = parent;
        this.receiver = receiver;
        setRgb(initialColor);
    }

    @Override
    protected boolean ausm$doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void ausm$initGui() {
        buttonList.clear();
        int fieldLeft = panelLeft();
        int fieldTop = pickerTop() + pickerSize() + 18;
        int fieldWidth = pickerSize() + brightnessBarWidth() + 8;
        hexField = field(30, fieldLeft, fieldTop, fieldWidth, 7);
        MinecraftReflectionCompat.setGuiTextFieldMaxLength(hexField, 7);
        for (int index = 0; index < 3; index++) {
            int x = fieldLeft + index * (smallFieldWidth() + 4);
            rgbFields[index] = field(31 + index, x, fieldTop + 30, smallFieldWidth(), 3);
            hsvFields[index] = field(34 + index, x, fieldTop + 60, smallFieldWidth(), index == 0 ? 3 : 3);
        }
        syncFieldsFromColor();
        buttonList.add(new GuiFlatButton(ID_DONE, width / 2 - 50, height - 28, 100, 20,
                MinecraftReflectionCompat.i18nFormat("gui.done")));
    }

    @Override
    protected void ausm$drawScreen(int mouseX, int mouseY, float partialTicks) {
        ausm$drawDefaultBackground();
        drawCenteredString(fontRenderer, "Color Picker", width / 2, 18, 0xFFFFFF);
        drawCenteredString(fontRenderer, "Choose a colour directly or enter an exact value", width / 2, 32, 0xA7B2BF);
        drawPicker();
        int fieldLeft = panelLeft();
        int fieldTop = pickerTop() + pickerSize() + 18;
        drawString(fontRenderer, "HEX", fieldLeft, fieldTop - 10, 0xDCE5F0);
        drawFieldLabels(new String[]{"R", "G", "B"}, fieldLeft, fieldTop + 20);
        drawFieldLabels(new String[]{"H", "S", "V"}, fieldLeft, fieldTop + 50);
        super.ausm$drawScreen(mouseX, mouseY, partialTicks);
        MinecraftReflectionCompat.drawGuiTextField(hexField);
        for (GuiTextField field : rgbFields) {
            MinecraftReflectionCompat.drawGuiTextField(field);
        }
        for (GuiTextField field : hsvFields) {
            MinecraftReflectionCompat.drawGuiTextField(field);
        }
        if (Mouse.isButtonDown(0)) {
            if (draggingColorSquare) {
                updateColorSquare(mouseX, mouseY);
            } else if (draggingBrightness) {
                updateBrightness(mouseY);
            }
        }
    }

    @Override
    protected void ausm$mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        applyFocusedField();
        if (mouseButton == 0 && containsColorSquare(mouseX, mouseY)) {
            draggingColorSquare = true;
            updateColorSquare(mouseX, mouseY);
            return;
        }
        if (mouseButton == 0 && containsBrightnessBar(mouseX, mouseY)) {
            draggingBrightness = true;
            updateBrightness(mouseY);
            return;
        }
        MinecraftReflectionCompat.guiTextFieldMouseClicked(hexField, mouseX, mouseY, mouseButton);
        for (GuiTextField field : rgbFields) {
            MinecraftReflectionCompat.guiTextFieldMouseClicked(field, mouseX, mouseY, mouseButton);
        }
        for (GuiTextField field : hsvFields) {
            MinecraftReflectionCompat.guiTextFieldMouseClicked(field, mouseX, mouseY, mouseButton);
        }
        super.ausm$mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void ausm$mouseReleased(int mouseX, int mouseY, int mouseButton) {
        draggingColorSquare = false;
        draggingBrightness = false;
        super.ausm$mouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void ausm$keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            MinecraftReflectionCompat.displayGuiScreen(mc, parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            applyFocusedField();
            return;
        }
        if (keyTypedInField(hexField, typedChar, keyCode)
                || keyTypedInFields(rgbFields, typedChar, keyCode)
                || keyTypedInFields(hsvFields, typedChar, keyCode)) {
            return;
        }
        super.ausm$keyTyped(typedChar, keyCode);
    }

    @Override
    protected void ausm$actionPerformed(GuiButton button) {
        int id = MinecraftReflectionCompat.guiButtonId(button);
        if (id == ID_DONE) {
            applyFocusedField();
            receiver.accept(rgb());
            MinecraftReflectionCompat.displayGuiScreen(mc, parent);
        }
    }

    private void drawPicker() {
        int left = panelLeft();
        int top = pickerTop();
        int size = pickerSize();
        int brightnessLeft = left + size + 8;
        drawRect(left - 2, top - 2, brightnessLeft + brightnessBarWidth() + 2, top + size + 2, 0xFF050505);
        drawHueSaturationSquare(left, top, size);
        drawBrightnessBar(brightnessLeft, top, size);
        int squareX = left + Math.round(hue / 360.0F * (size - 1));
        int squareY = top + Math.round((100 - saturation) / 100.0F * (size - 1));
        drawPickerCursor(squareX, squareY);
        int brightnessY = top + Math.round((100 - brightness) / 100.0F * (size - 1));
        drawRect(brightnessLeft - 2, brightnessY - 1, brightnessLeft + brightnessBarWidth() + 2, brightnessY + 1, 0xFFFFFFFF);
        drawRect(brightnessLeft - 1, brightnessY, brightnessLeft + brightnessBarWidth() + 1, brightnessY + 1, 0xFF000000);
    }

    private void drawHueSaturationSquare(int left, int top, int size) {
        for (int column = 0; column < size; column++) {
            float hueValue = column / (float) Math.max(1, size - 1);
            int hueRgb = Color.HSBtoRGB(hueValue, 1.0F, 1.0F);
            drawGradientRect(left + column, top, left + column + 1, top + size,
                    0xFF000000 | hueRgb, 0xFFFFFFFF);
        }
    }

    private void drawBrightnessBar(int left, int top, int height) {
        int hueRgb = Color.HSBtoRGB(hue / 360.0F, saturation / 100.0F, 1.0F);
        drawGradientRect(left, top, left + brightnessBarWidth(), top + height,
                0xFF000000 | hueRgb, 0xFF000000);
    }

    private void drawPickerCursor(int x, int y) {
        drawRect(x - 3, y - 3, x + 4, y - 2, 0xFF000000);
        drawRect(x - 3, y + 2, x + 4, y + 3, 0xFF000000);
        drawRect(x - 3, y - 2, x - 2, y + 3, 0xFF000000);
        drawRect(x + 2, y - 2, x + 3, y + 3, 0xFF000000);
        drawRect(x - 2, y - 2, x + 3, y - 1, 0xFFFFFFFF);
        drawRect(x - 2, y + 1, x + 3, y + 2, 0xFFFFFFFF);
        drawRect(x - 2, y - 1, x - 1, y + 2, 0xFFFFFFFF);
        drawRect(x + 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
    }

    private void drawFieldLabels(String[] labels, int left, int y) {
        for (int index = 0; index < labels.length; index++) {
            drawString(fontRenderer, labels[index], left + index * (smallFieldWidth() + 4), y, 0xDCE5F0);
        }
    }

    private GuiTextField field(int id, int x, int y, int fieldWidth, int maxLength) {
        GuiTextField field = new GuiTextField(id, fontRenderer, x, y, fieldWidth, 20);
        MinecraftReflectionCompat.setGuiTextFieldMaxLength(field, maxLength);
        MinecraftReflectionCompat.setGuiTextFieldBackground(field, true);
        return field;
    }

    private int pickerSize() {
        return Math.clamp(Math.min(width - 90, height - 190), PICKER_MIN_SIZE, PICKER_MAX_SIZE);
    }

    private int panelLeft() {
        return (width - pickerSize() - brightnessBarWidth() - 8) / 2;
    }

    private int pickerTop() {
        return Math.max(44, (height - (pickerSize() + 158)) / 2);
    }

    private int brightnessBarWidth() {
        return 16;
    }

    private int smallFieldWidth() {
        return Math.max(32, (pickerSize() + brightnessBarWidth()) / 3);
    }

    private boolean containsColorSquare(int mouseX, int mouseY) {
        return mouseX >= panelLeft() && mouseX < panelLeft() + pickerSize()
                && mouseY >= pickerTop() && mouseY < pickerTop() + pickerSize();
    }

    private boolean containsBrightnessBar(int mouseX, int mouseY) {
        int left = panelLeft() + pickerSize() + 8;
        return mouseX >= left && mouseX < left + brightnessBarWidth()
                && mouseY >= pickerTop() && mouseY < pickerTop() + pickerSize();
    }

    private void updateColorSquare(int mouseX, int mouseY) {
        hue = Math.clamp(Math.round((mouseX - panelLeft()) * 360.0F / Math.max(1, pickerSize() - 1)), 0, 360) % 360;
        saturation = Math.clamp(Math.round((pickerTop() + pickerSize() - 1 - mouseY) * 100.0F / Math.max(1, pickerSize() - 1)), 0, 100);
        if (brightness == 0) {
            brightness = 100;
        }
        syncRgbFromHsv();
        syncFieldsFromColor();
    }

    private void updateBrightness(int mouseY) {
        brightness = Math.clamp(Math.round((pickerTop() + pickerSize() - 1 - mouseY) * 100.0F / Math.max(1, pickerSize() - 1)), 0, 100);
        syncRgbFromHsv();
        syncFieldsFromColor();
    }

    private void setRgb(int rgb) {
        red = rgb >> 16 & 0xFF;
        green = rgb >> 8 & 0xFF;
        blue = rgb & 0xFF;
        syncHsvFromRgb();
    }

    private void syncHsvFromRgb() {
        float[] hsv = Color.RGBtoHSB(red, green, blue, null);
        hue = Math.round(hsv[0] * 360.0F) % 360;
        saturation = Math.round(hsv[1] * 100.0F);
        brightness = Math.round(hsv[2] * 100.0F);
        syncFieldsFromColor();
    }

    private void syncRgbFromHsv() {
        int rgb = Color.HSBtoRGB(hue / 360.0F, saturation / 100.0F, brightness / 100.0F);
        red = rgb >> 16 & 0xFF;
        green = rgb >> 8 & 0xFF;
        blue = rgb & 0xFF;
        syncFieldsFromColor();
    }

    private void syncFieldsFromColor() {
        setFieldTextUnlessFocused(hexField, hex());
        setFieldTextUnlessFocused(rgbFields[0], Integer.toString(red));
        setFieldTextUnlessFocused(rgbFields[1], Integer.toString(green));
        setFieldTextUnlessFocused(rgbFields[2], Integer.toString(blue));
        setFieldTextUnlessFocused(hsvFields[0], Integer.toString(hue));
        setFieldTextUnlessFocused(hsvFields[1], Integer.toString(saturation));
        setFieldTextUnlessFocused(hsvFields[2], Integer.toString(brightness));
    }

    private void setFieldTextUnlessFocused(GuiTextField field, String text) {
        if (field != null && !MinecraftReflectionCompat.guiTextFieldFocused(field)) {
            MinecraftReflectionCompat.setGuiTextFieldText(field, text);
        }
    }

    private boolean keyTypedInFields(GuiTextField[] fields, char typedChar, int keyCode) {
        for (GuiTextField field : fields) {
            if (keyTypedInField(field, typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }

    private boolean keyTypedInField(GuiTextField field, char typedChar, int keyCode) {
        if (!MinecraftReflectionCompat.guiTextFieldFocused(field)
                || !MinecraftReflectionCompat.guiTextFieldKeyTyped(field, typedChar, keyCode)) {
            return false;
        }
        applyFocusedField();
        return true;
    }

    private void applyFocusedField() {
        if (hexField != null && MinecraftReflectionCompat.guiTextFieldFocused(hexField)) {
            Integer parsed = parseHex(MinecraftReflectionCompat.guiTextFieldText(hexField));
            if (parsed != null) {
                setRgb(parsed);
            }
            return;
        }
        if (fieldFocused(rgbFields)) {
            Integer redValue = parseInteger(rgbFields[0], 0, 255);
            Integer greenValue = parseInteger(rgbFields[1], 0, 255);
            Integer blueValue = parseInteger(rgbFields[2], 0, 255);
            if (redValue != null && greenValue != null && blueValue != null) {
                red = redValue;
                green = greenValue;
                blue = blueValue;
                syncHsvFromRgb();
            }
            return;
        }
        if (fieldFocused(hsvFields)) {
            Integer hueValue = parseInteger(hsvFields[0], 0, 360);
            Integer saturationValue = parseInteger(hsvFields[1], 0, 100);
            Integer brightnessValue = parseInteger(hsvFields[2], 0, 100);
            if (hueValue != null && saturationValue != null && brightnessValue != null) {
                hue = hueValue % 360;
                saturation = saturationValue;
                brightness = brightnessValue;
                syncRgbFromHsv();
            }
        }
    }

    private static boolean fieldFocused(GuiTextField[] fields) {
        for (GuiTextField field : fields) {
            if (field != null && MinecraftReflectionCompat.guiTextFieldFocused(field)) {
                return true;
            }
        }
        return false;
    }

    private static Integer parseInteger(GuiTextField field, int minimum, int maximum) {
        if (field == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(MinecraftReflectionCompat.guiTextFieldText(field).trim());
            return value >= minimum && value <= maximum ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int rgb() {
        return red << 16 | green << 8 | blue;
    }

    private String hex() {
        return String.format(Locale.ROOT, "#%06X", rgb());
    }

    private static Integer parseHex(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (!value.matches("[0-9A-Fa-f]{6}")) {
            return null;
        }
        return Integer.parseInt(value, 16);
    }
}
