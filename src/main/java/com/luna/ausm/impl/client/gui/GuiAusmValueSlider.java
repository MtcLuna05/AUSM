package com.luna.ausm.impl.client.gui;

import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

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
        // Hotswap preserves existing button instances, so normalize their old size too.
        if (MinecraftReflectionCompat.guiButtonHeight(this) != 20) {
            MinecraftReflectionCompat.setField(this, 20, "field_146121_g", "height");
        }
        if (dragging) {
            updateFromMouse(mouseX);
        }

        int x = MinecraftReflectionCompat.guiButtonX(this);
        int y = MinecraftReflectionCompat.guiButtonY(this);
        int width = MinecraftReflectionCompat.guiButtonWidth(this);
        int height = MinecraftReflectionCompat.guiButtonHeight(this);
        boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        boolean enabled = MinecraftReflectionCompat.guiButtonEnabled(this);
        ResourceLocation widgets = MinecraftReflectionCompat.field(
                GuiButton.class, ResourceLocation.class, null, "field_146122_a", "BUTTON_TEXTURES");
        if (widgets == null) {
            widgets = new ResourceLocation("textures/gui/widgets.png");
        }
        MinecraftReflectionCompat.bindTexture(MinecraftReflectionCompat.textureManager(mc), widgets);
        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(770, 771, 1, 0);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);

        int leftWidth = width / 2;
        int textureY = 46;
        MinecraftReflectionCompat.invoke(this,
                new String[]{"func_73729_b", "drawTexturedModalRect"},
                new Class<?>[]{int.class, int.class, int.class, int.class, int.class, int.class},
                x, y, 0, textureY, leftWidth, height);
        MinecraftReflectionCompat.invoke(this,
                new String[]{"func_73729_b", "drawTexturedModalRect"},
                new Class<?>[]{int.class, int.class, int.class, int.class, int.class, int.class},
                x + leftWidth, y, 200 - leftWidth, textureY, width - leftWidth, height);

        int thumbLeft = x + Math.round(valueFraction() * Math.max(0, width - 8));
        MinecraftReflectionCompat.invoke(this,
                new String[]{"func_73729_b", "drawTexturedModalRect"},
                new Class<?>[]{int.class, int.class, int.class, int.class, int.class, int.class},
                thumbLeft, y, 0, 66, 4, height);
        MinecraftReflectionCompat.invoke(this,
                new String[]{"func_73729_b", "drawTexturedModalRect"},
                new Class<?>[]{int.class, int.class, int.class, int.class, int.class, int.class},
                thumbLeft + 4, y, 196, 66, 4, height);

        FontRenderer font = MinecraftReflectionCompat.fontRenderer(mc);
        int textColor = !enabled ? 10526880 : hovered ? 16777120 : 14737632;
        int textY = y + (height - 8) / 2;
        MinecraftReflectionCompat.fontDrawString(font, label, x + 7, textY, textColor);
        String valueText = valueLabel();
        int valueX = Math.max(x + 7, x + width - 7 - MinecraftReflectionCompat.fontStringWidth(font, valueText));
        MinecraftReflectionCompat.fontDrawString(font, valueText, valueX, textY, textColor);
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
        if (dragging) {
            updateFromMouse(mouseX);
        }
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

    private void drawNotches(int railLeft, int railY, int railWidth, boolean enabled) {
        // Vanilla's widget atlas has no tick-mark overlay; quantization remains in updateFromMouse.
    }
}
