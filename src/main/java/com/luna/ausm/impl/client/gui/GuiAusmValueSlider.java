package com.luna.ausm.impl.client.gui;

import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * Vanilla-skinned continuous slider shared by AUSM's own settings screens.
 * Values are shown in the ordinary button label until interaction begins; the
 * live value is intentionally left to the owning screen's item-style tooltip.
 */
public final class GuiAusmValueSlider extends GuiButton {
    private final String label;
    private final double min;
    private final double max;
    private final int notchCount;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final DoubleFunction<String> formatter;
    private boolean dragging;

    public GuiAusmValueSlider(int buttonId, int x, int y, int width, String label,
                              double min, double max, int notchCount,
                              DoubleSupplier getter, DoubleConsumer setter,
                              DoubleFunction<String> formatter) {
        super(buttonId, x, y, width, 20, "");
        this.label = Objects.requireNonNull(label, "label");
        this.min = min;
        this.max = max;
        this.notchCount = Math.max(0, notchCount);
        this.getter = Objects.requireNonNull(getter, "getter");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    public boolean dragging() {
        return dragging;
    }

    public double value() {
        return Math.clamp(getter.getAsDouble(), min, max);
    }

    public String valueLabel() {
        return formatter.apply(value());
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!MinecraftReflectionCompat.guiButtonVisible(this)) {
            return;
        }
        if (dragging) {
            updateFromMouse(mouseX);
        }

        int x = MinecraftReflectionCompat.guiButtonX(this);
        int y = MinecraftReflectionCompat.guiButtonY(this);
        int width = MinecraftReflectionCompat.guiButtonWidth(this);
        int height = MinecraftReflectionCompat.guiButtonHeight(this);
        boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        MinecraftReflectionCompat.setGuiButtonText(this, hovered || dragging ? "" : label + ": " + valueLabel());
        super.drawButton(mc, -1, -1, partialTicks);
        if (!hovered && !dragging) {
            return;
        }

        int railLeft = x + 7;
        int railWidth = Math.max(1, width - 14);
        int railY = y + height / 2;
        MinecraftReflectionCompat.guiDrawRect(railLeft, railY, railLeft + railWidth, railY + 1, 0xFFB7C7D8);
        drawNotches(railLeft, railY, railWidth);
        int thumbWidth = 12;
        int thumbHeight = 12;
        int thumbX = railLeft + Math.round(valueFraction() * (railWidth - thumbWidth));
        int thumbY = y + (height - thumbHeight) / 2;
        new GuiButton(-1, thumbX, thumbY, thumbWidth, thumbHeight, "").drawButton(mc, -1, -1, partialTicks);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!MinecraftReflectionCompat.guiButtonMousePressed(this, mc, mouseX, mouseY)) {
            return false;
        }
        updateFromMouse(mouseX);
        dragging = true;
        return true;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        // MappingSafeGuiScreen feeds the actual pointer through drawButton while held.
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        dragging = false;
    }

    private void updateFromMouse(int mouseX) {
        int x = MinecraftReflectionCompat.guiButtonX(this);
        int width = Math.max(1, MinecraftReflectionCompat.guiButtonWidth(this));
        double fraction = Math.clamp((mouseX - (x + 7)) / (double) Math.max(1, width - 14), 0.0D, 1.0D);
        if (notchCount > 1) {
            fraction = Math.round(fraction * (notchCount - 1)) / (double) (notchCount - 1);
        }
        setter.accept(min + fraction * (max - min));
    }

    private float valueFraction() {
        if (Double.compare(max, min) == 0) {
            return 0.0F;
        }
        return (float) Math.clamp((value() - min) / (max - min), 0.0D, 1.0D);
    }

    private void drawNotches(int railLeft, int railY, int railWidth) {
        if (notchCount < 2) {
            return;
        }
        for (int index = 0; index < notchCount; index++) {
            int x = railLeft + Math.round(index * railWidth / (float) (notchCount - 1));
            MinecraftReflectionCompat.guiDrawRect(x, railY - 2, x + 1, railY + 3, 0xFFB7C7D8);
        }
    }
}
