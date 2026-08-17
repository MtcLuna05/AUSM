package com.l.ausm.impl.client.gui;

import com.l.ausm.api.pipeline.pack.ShaderOption;
import com.l.ausm.api.pipeline.pack.ShaderScreenEntry;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

abstract class GuiShaderOptionsBase extends MappingSafeGuiScreen {
    protected static final int ID_DONE = 200;

    protected static final int ID_APPLY = 201;

    protected static final int ID_RESET = 202;

    protected static final int ID_PREVIOUS_PAGE = 203;

    protected static final int ID_NEXT_PAGE = 204;

    protected static final int ID_PREVIEW = 205;

    protected static final String DEBUG_VOID_OPTION = "AUSM_CUSTOM_VOID_WORLD";

    protected static final int CATEGORY_BASE_ID = 1000;

    protected static final int OPTION_BASE_ID = 3000;

    protected static final String CUSTOM_PROFILE = "Custom";

    protected static final int OPTION_PANEL_TOP = 42;

    protected static final int OPTION_ROW_HEIGHT = 24;

    protected static final int MIN_SIDEBAR_WIDTH = 128;

    protected static final int MAX_SIDEBAR_WIDTH = 260;

    protected GuiScreen parent;

    protected String packName;

    protected final Map<String, String> savedValues = new LinkedHashMap<>();

    protected final Map<String, String> pendingValues = new LinkedHashMap<>();

    protected final List<String> screenHistory = new ArrayList<>();

    protected final Set<String> expandedSidebarScreens = new HashSet<>();

    protected ShaderProperties properties;

    protected String selectedScreen = "screen";

    protected List<ShaderScreenEntry> visibleEntries = List.of();

    protected List<GuiShaderOptions.SidebarItem> sidebarItems = List.of();

    protected GuiTextField searchField;

    protected GuiButton applyButton;

    protected GuiButton previousPageButton;

    protected GuiButton nextPageButton;

    protected GuiButton profileButton;

    protected GuiButton previewButton;

    protected boolean previewHidden;

    protected GuiShaderOptions.GuiShaderOptionDropdown activeDropdown;

    protected GuiShaderOptions.GuiShaderProfileDropdown activeProfileDropdown;

    protected List<String> hoveredCommentTitle = List.of();

    protected List<String> hoveredCommentBody = List.of();

    protected int page;

    protected int sidebarWidth = MIN_SIDEBAR_WIDTH;

    protected int sidebarScrollOffset;

    protected int lastMouseX;

    protected int lastMouseY;

    protected int focusedControl = -1;

    protected static final class OptionGrid {
        final int columns;
        final int rows;

        OptionGrid(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }

        int columns() {
            return columns;
        }

        int rows() {
            return rows;
        }
    }

    protected static final class SidebarItem {
        final String screen;
        final int depth;

        SidebarItem(String screen, int depth) {
            this.screen = screen;
            this.depth = depth;
        }

        String screen() {
            return screen;
        }

        int depth() {
            return depth;
        }
    }

    protected final class GuiShaderOptionSlider extends GuiButton {
        final ShaderOption option;
        final boolean numeric;
        final boolean continuous;
        final double minValue;
        final double maxValue;
        final int scale;
        float sliderValue;
        boolean dragging;
        String selectedValue;

        GuiShaderOptionSlider(int buttonId, int x, int y, int widthIn, int heightIn, ShaderOption option, String currentValue) {
            super(buttonId, x, y, widthIn, heightIn, "");
            this.option = option;
            this.numeric = allChoicesNumeric(option);
            this.minValue = numeric ? parseDouble(option.choices().get(0), 0.0) : 0.0;
            this.maxValue = numeric ? parseDouble(option.choices().get(option.choices().size() - 1), 1.0) : 1.0;
            this.scale = numeric ? maxScale(option.choices()) : 0;
            this.continuous = numeric && scale > 0;
            this.sliderValue = initialSliderValue(currentValue);
            updateDisplay();
        }

        String selectedValue() {
            return selectedValue;
        }

        String calculateSelectedValue() {
            if (numeric) {
                double value = minValue + sliderValue * (maxValue - minValue);
                return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
            }

            int index = Math.round(sliderValue * (option.choices().size() - 1));
            return option.choices().get(Math.clamp(index, 0, option.choices().size() - 1));
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!MinecraftReflectionCompat.guiButtonVisible(this)) {
                return;
            }
            if (dragging) {
                updateFromMouse(mouseX, false);
            }

            int x = MinecraftReflectionCompat.guiButtonX(this);
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int width = MinecraftReflectionCompat.guiButtonWidth(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            int background = hovered ? 0xFF202C38 : 0xFF151D26;
            MinecraftReflectionCompat.guiDrawRect(x, y, x + width, y + height, background);
            MinecraftReflectionCompat.guiDrawRect(x, y, x + width, y + 1, 0xFF40566C);
            MinecraftReflectionCompat.guiDrawRect(x, y + height - 1, x + width, y + height, 0xFF070B10);

            boolean showSlider = hovered || dragging;
            String leftText = GuiShaderOptionsBase.this.self().optionName(option.name());
            String rightText = showSlider ? "" : GuiShaderOptionsBase.this.self().optionValue(option.name(), selectedValue);
            int textColor = MinecraftReflectionCompat.guiButtonEnabled(this) ? 0xFFFFFF : 0x707070;
            if (!showSlider) {
                MinecraftReflectionCompat.fontDrawString(MinecraftReflectionCompat.fontRenderer(mc),
                        leftText, x + 7, y + 5, textColor);
                MinecraftReflectionCompat.fontDrawString(MinecraftReflectionCompat.fontRenderer(mc),
                        rightText, x + width - 7 - MinecraftReflectionCompat.fontStringWidth(
                                MinecraftReflectionCompat.fontRenderer(mc), rightText), y + 5, textColor);
            }

            if (showSlider) {
                int trackLeft = x + 2;
                int trackTop = y + 2;
                int trackWidth = width - 4;
                int trackHeight = height - 4;
                MinecraftReflectionCompat.guiDrawRect(trackLeft, trackTop,
                        trackLeft + trackWidth, trackTop + trackHeight, 0xFF07101A);
                MinecraftReflectionCompat.guiDrawRect(trackLeft, trackTop,
                        trackLeft + Math.round(sliderValue * trackWidth), trackTop + trackHeight, 0xFF204D7A);
                drawNotches(trackLeft, trackTop, trackWidth, trackHeight);

                int thumbWidth = 6;
                int thumbX = trackLeft + Math.round(sliderValue * (trackWidth - thumbWidth));
                MinecraftReflectionCompat.guiDrawRect(thumbX, trackTop + 2, thumbX + thumbWidth,
                        trackTop + trackHeight - 2, dragging ? 0xFFFFFFFF : 0xFFE7EEF8);
                MinecraftReflectionCompat.guiDrawRect(thumbX + 1, trackTop + 3, thumbX + thumbWidth - 1,
                        trackTop + trackHeight - 3, 0xFF7CB7FF);

                String valueText = GuiShaderOptionsBase.this.self().optionValue(option.name(), selectedValue);
                MinecraftReflectionCompat.fontDrawString(MinecraftReflectionCompat.fontRenderer(mc),
                        valueText,
                        x + width / 2 - MinecraftReflectionCompat.fontStringWidth(
                                MinecraftReflectionCompat.fontRenderer(mc), valueText) / 2,
                        y + 6,
                        textColor
                );
            }
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (MinecraftReflectionCompat.guiButtonMousePressed(this, mc, mouseX, mouseY)) {
                if (GuiControlHints.isShiftDown()) {
                    resetToDefault();
                    return true;
                }
                updateFromMouse(mouseX, true);
                dragging = true;
                return true;
            }
            return false;
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (MinecraftReflectionCompat.guiButtonVisible(this) && dragging) {
                updateFromMouse(mouseX, false);
            }
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            dragging = false;
        }

        void updateFromMouse(int mouseX, boolean snapToNearbyNotch) {
            int x = MinecraftReflectionCompat.guiButtonX(this);
            int width = MinecraftReflectionCompat.guiButtonWidth(this);
            sliderValue = (float) (mouseX - (x + 7)) / (float) (width - 14);
            sliderValue = Math.clamp(sliderValue, 0.0f, 1.0f);
            if (!continuous) {
                sliderValue = quantizedSliderValue(sliderValue);
            } else if (snapToNearbyNotch) {
                sliderValue = nearestNotchValue(mouseX, sliderValue);
            }
            selectedValue = calculateSelectedValue();
            GuiShaderOptionsBase.this.self().setPendingOptionValue(option, selectedValue);
            updateDisplay();
            if (applyButton != null) {
                MinecraftReflectionCompat.setGuiButtonEnabled(applyButton, true);
            }
        }

        void updateDisplay() {
            selectedValue = calculateSelectedValue();
            MinecraftReflectionCompat.setGuiButtonText(this, GuiShaderOptionsBase.this.self().optionName(option.name()));
        }

        void resetToDefault() {
            sliderValue = initialSliderValue(option.defaultValue());
            selectedValue = calculateSelectedValue();
            GuiShaderOptionsBase.this.self().setPendingOptionValue(option, option.defaultValue());
            updateDisplay();
            if (applyButton != null) {
                MinecraftReflectionCompat.setGuiButtonEnabled(applyButton, true);
            }
        }

        void step(int direction) {
            stepFine(direction);
        }

        void stepFine(int direction) {
            if (option.choices().isEmpty()) {
                return;
            }

            if (numeric) {
                double value = parseDouble(selectedValue, minValue) + numericStepSize() * direction;
                value = Math.clamp(value, minValue, maxValue);
                String formatted = BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
                sliderValue = initialSliderValue(formatted);
            } else {
                String value = GuiShaderOptionsBase.this.self().shiftedChoice(option, selectedValue, direction);
                sliderValue = initialSliderValue(value);
            }
            selectedValue = calculateSelectedValue();
            GuiShaderOptionsBase.this.self().setPendingOptionValue(option, selectedValue);
            updateDisplay();
            if (applyButton != null) {
                MinecraftReflectionCompat.setGuiButtonEnabled(applyButton, true);
            }
        }

        void stepNotch(int direction) {
            if (option.choices().isEmpty()) {
                return;
            }

            String value = GuiShaderOptionsBase.this.self().shiftedChoice(option, selectedValue, direction);
            sliderValue = initialSliderValue(value);
            selectedValue = calculateSelectedValue();
            GuiShaderOptionsBase.this.self().setPendingOptionValue(option, selectedValue);
            updateDisplay();
            if (applyButton != null) {
                MinecraftReflectionCompat.setGuiButtonEnabled(applyButton, true);
            }
        }

        float initialSliderValue(String currentValue) {
            if (numeric) {
                double value = parseDouble(currentValue, minValue);
                if (maxValue == minValue) {
                    return 0.0f;
                }
                return (float) Math.clamp((value - minValue) / (maxValue - minValue), 0.0, 1.0);
            }

            int index = Math.max(0, option.choices().indexOf(currentValue));
            return option.choices().size() <= 1 ? 0.0f : (float) index / (float) (option.choices().size() - 1);
        }

        float nearestNotchValue(int mouseX, float fallback) {
            int railLeft = MinecraftReflectionCompat.guiButtonX(this) + 7;
            int railWidth = MinecraftReflectionCompat.guiButtonWidth(this) - 14;
            float nearest = fallback;
            int nearestDistance = 5;
            for (String choice : option.choices()) {
                float position = choicePosition(choice);
                int notchX = railLeft + Math.round(position * railWidth);
                int distance = Math.abs(mouseX - notchX);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = position;
                }
            }
            return nearest;
        }

        float quantizedSliderValue(float value) {
            if (!numeric) {
                int index = Math.round(value * (option.choices().size() - 1));
                return option.choices().size() <= 1 ? 0.0f : (float) index / (float) (option.choices().size() - 1);
            }
            double rounded = Math.rint(minValue + value * (maxValue - minValue));
            if (maxValue == minValue) {
                return 0.0f;
            }
            return (float) Math.clamp((rounded - minValue) / (maxValue - minValue), 0.0, 1.0);
        }

        double numericStepSize() {
            return scale <= 0 ? 1.0 : Math.pow(10.0, -scale);
        }

        void drawNotches(int trackLeft, int trackTop, int trackWidth, int trackHeight) {
            for (String choice : option.choices()) {
                int notchX = trackLeft + Math.round(choicePosition(choice) * trackWidth);
                MinecraftReflectionCompat.guiDrawRect(notchX, trackTop + trackHeight - 4,
                        notchX + 1, trackTop + trackHeight - 1, 0xFFB7C7D8);
            }
        }

        float choicePosition(String choice) {
            if (numeric) {
                double value = parseDouble(choice, minValue);
                if (maxValue == minValue) {
                    return 0.0f;
                }
                return (float) Math.clamp((value - minValue) / (maxValue - minValue), 0.0, 1.0);
            }

            int index = Math.max(0, option.choices().indexOf(choice));
            return option.choices().size() <= 1 ? 0.0f : (float) index / (float) (option.choices().size() - 1);
        }

        boolean allChoicesNumeric(ShaderOption option) {
            return option.choices().stream().allMatch(choice -> {
                try {
                    Double.parseDouble(choice);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            });
        }

        double parseDouble(String value, double fallback) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        int maxScale(List<String> values) {
            int result = 0;
            for (String value : values) {
                int dot = value.indexOf('.');
                if (dot >= 0) {
                    result = Math.max(result, value.length() - dot - 1);
                }
            }
            return result;
        }
    }

    protected final class GuiShaderOptionDropdown extends GuiButton {
        final ShaderOption option;
        int scrollOffset;

        GuiShaderOptionDropdown(int buttonId, int x, int y, int widthIn, int heightIn, ShaderOption option, String currentValue) {
            super(buttonId, x, y, widthIn, heightIn, "");
            this.option = option;
            updateDisplay(currentValue);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!MinecraftReflectionCompat.guiButtonVisible(this)) {
                return;
            }

            int x = MinecraftReflectionCompat.guiButtonX(this);
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int width = MinecraftReflectionCompat.guiButtonWidth(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            boolean open = activeDropdown == this;
            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            int background = open ? 0xFF24354A : hovered ? 0xFF202C38 : 0xFF151D26;
            MinecraftReflectionCompat.guiDrawRect(x, y, x + width, y + height, background);
            MinecraftReflectionCompat.guiDrawRect(x, y, x + width, y + 1, 0xFF40566C);
            MinecraftReflectionCompat.guiDrawRect(x, y + height - 1, x + width, y + height, 0xFF070B10);
            MinecraftReflectionCompat.guiDrawRect(x + width - 18, y + 1,
                    x + width - 17, y + height - 1, 0xFF0A0F15);

            MinecraftReflectionCompat.fontDrawString(MinecraftReflectionCompat.fontRenderer(mc),
                    MinecraftReflectionCompat.guiButtonText(this), x + 7, y + 6,
                    MinecraftReflectionCompat.guiButtonEnabled(this) ? 0xFFFFFF : 0x707070);
            MinecraftReflectionCompat.fontDrawString(MinecraftReflectionCompat.fontRenderer(mc),
                    open ? "^" : "v", x + width - 12, y + 6, 0xC8CED6);
        }

        void updateDisplay(String value) {
            MinecraftReflectionCompat.setGuiButtonText(this,
                    GuiShaderOptionsBase.this.self().optionName(option.name()) + ": " + GuiShaderOptionsBase.this.self().optionValue(option.name(), value));
        }

        boolean isMouseOver(int mouseX, int mouseY) {
            int x = MinecraftReflectionCompat.guiButtonX(this);
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int width = MinecraftReflectionCompat.guiButtonWidth(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            int bottom = activeDropdown == this ? dropdownBottom() : y + height;
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < bottom;
        }

        String valueAt(int mouseX, int mouseY) {
            int x = MinecraftReflectionCompat.guiButtonX(this);
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int width = MinecraftReflectionCompat.guiButtonWidth(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            int top = y + height;
            if (mouseX < x || mouseX >= x + width || mouseY < top || mouseY >= dropdownBottom()) {
                return null;
            }

            int index = scrollOffset + (mouseY - top) / height;
            if (index < 0 || index >= option.choices().size()) {
                return null;
            }
            return option.choices().get(index);
        }

        void opened() {
            scrollOffset = 0;
            int selectedIndex = option.choices().indexOf(GuiShaderOptionsBase.this.self().valueFor(option));
            if (selectedIndex >= visibleRows()) {
                scrollOffset = selectedIndex - visibleRows() + 1;
            }
            clampScroll();
        }

        void scroll(int rows) {
            scrollOffset += rows;
            clampScroll();
        }

        void step(int direction) {
            String value = GuiShaderOptionsBase.this.self().shiftedChoice(option, GuiShaderOptionsBase.this.self().valueFor(option), direction);
            GuiShaderOptionsBase.this.self().setPendingOptionValue(option, value);
            updateDisplay(value);
            if (applyButton != null) {
                MinecraftReflectionCompat.setGuiButtonEnabled(applyButton, true);
            }
        }

        void clampScroll() {
            scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
        }

        int maxScroll() {
            return Math.max(0, option.choices().size() - visibleRows());
        }

        int visibleRows() {
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            int available = Math.max(height, GuiShaderOptionsBase.this.self().height - (y + height) - 36);
            return Math.clamp(available / height, 1, Math.max(1, option.choices().size()));
        }

        int dropdownBottom() {
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            return y + height + visibleRows() * height;
        }

        void drawDropdown(int mouseX, int mouseY) {
            int x = MinecraftReflectionCompat.guiButtonX(this);
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int width = MinecraftReflectionCompat.guiButtonWidth(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            int top = y + height;
            int rows = visibleRows();
            int bottom = top + rows * height;
            MinecraftReflectionCompat.guiDrawRect(x, top, x + width, bottom, 0xEE121922);
            MinecraftReflectionCompat.guiDrawRect(x, top, x + width, top + 1, 0xFF4B5E73);
            MinecraftReflectionCompat.guiDrawRect(x, bottom - 1, x + width, bottom, 0xFF05080C);

            for (int row = 0; row < rows; row++) {
                int i = scrollOffset + row;
                int rowTop = top + row * height;
                boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowTop && mouseY < rowTop + height;
                boolean selected = option.choices().get(i).equals(GuiShaderOptionsBase.this.self().valueFor(option));
                if (hovered) {
                    MinecraftReflectionCompat.guiDrawRect(x + 1, rowTop + 1,
                            x + width - 1, rowTop + height - 1, 0xFF26384A);
                } else if (selected) {
                    MinecraftReflectionCompat.guiDrawRect(x + 1, rowTop + 1,
                            x + width - 1, rowTop + height - 1, 0xFF1D2D3C);
                }
                String value = option.choices().get(i);
                MinecraftReflectionCompat.fontDrawString(fontRenderer, GuiShaderOptionsBase.this.self().optionValue(option.name(), value), x + 6, rowTop + 6, selected ? 0xFFFFFF : 0xC8CED6);
            }
            GuiShaderOptionsBase.this.self().drawScrollbar(x, width, top, bottom, option.choices().size(), rows, scrollOffset);
        }
    }

    protected final class GuiShaderProfileDropdown extends GuiButton {
        int scrollOffset;

        GuiShaderProfileDropdown(int buttonId, int x, int y, int widthIn, int heightIn) {
            super(buttonId, x, y, widthIn, heightIn, GuiShaderOptionsBase.this.self().profileLabel());
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!MinecraftReflectionCompat.guiButtonVisible(this)) {
                return;
            }

            int x = MinecraftReflectionCompat.guiButtonX(this);
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int width = MinecraftReflectionCompat.guiButtonWidth(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            boolean open = activeProfileDropdown == this;
            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            int background = open ? 0xFF24354A : hovered ? 0xFF202C38 : 0xFF151D26;
            MinecraftReflectionCompat.guiDrawRect(x, y, x + width, y + height, background);
            MinecraftReflectionCompat.guiDrawRect(x, y, x + width, y + 1, 0xFF40566C);
            MinecraftReflectionCompat.guiDrawRect(x, y + height - 1, x + width, y + height, 0xFF070B10);
            MinecraftReflectionCompat.guiDrawRect(x + width - 18, y + 1,
                    x + width - 17, y + height - 1, 0xFF0A0F15);

            MinecraftReflectionCompat.fontDrawString(MinecraftReflectionCompat.fontRenderer(mc),
                    GuiShaderOptionsBase.this.self().profileLabel(), x + 7, y + 6,
                    MinecraftReflectionCompat.guiButtonEnabled(this) ? 0xFFFFFF : 0x707070);
            MinecraftReflectionCompat.fontDrawString(MinecraftReflectionCompat.fontRenderer(mc),
                    open ? "^" : "v", x + width - 12, y + 6, 0xC8CED6);
        }

        boolean isMouseOver(int mouseX, int mouseY) {
            int x = MinecraftReflectionCompat.guiButtonX(this);
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int width = MinecraftReflectionCompat.guiButtonWidth(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            int bottom = activeProfileDropdown == this ? dropdownBottom() : y + height;
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < bottom;
        }

        String valueAt(int mouseX, int mouseY) {
            int x = MinecraftReflectionCompat.guiButtonX(this);
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int width = MinecraftReflectionCompat.guiButtonWidth(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            int top = y + height;
            if (mouseX < x || mouseX >= x + width || mouseY < top || mouseY >= dropdownBottom()) {
                return null;
            }

            List<String> profiles = new ArrayList<>(GuiShaderOptionsBase.this.self().properties().profiles().keySet());
            int index = scrollOffset + (mouseY - top) / height;
            if (index < 0 || index >= profiles.size()) {
                return null;
            }
            return profiles.get(index);
        }

        void opened() {
            List<String> profiles = new ArrayList<>(GuiShaderOptionsBase.this.self().properties().profiles().keySet());
            scrollOffset = 0;
            int selectedIndex = profiles.indexOf(pendingValues.get("<profile>"));
            if (selectedIndex >= visibleRows()) {
                scrollOffset = selectedIndex - visibleRows() + 1;
            }
            clampScroll();
        }

        void scroll(int rows) {
            scrollOffset += rows;
            clampScroll();
        }

        void clampScroll() {
            scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
        }

        int maxScroll() {
            return Math.max(0, GuiShaderOptionsBase.this.self().properties().profiles().size() - visibleRows());
        }

        int visibleRows() {
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            int available = Math.max(height, GuiShaderOptionsBase.this.self().height - (y + height) - 36);
            return Math.clamp(
                    available / height,
                    1,
                    Math.max(1, GuiShaderOptionsBase.this.self().properties().profiles().size()));
        }

        int dropdownBottom() {
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            return y + height + visibleRows() * height;
        }

        void drawDropdown(int mouseX, int mouseY) {
            List<String> profiles = new ArrayList<>(GuiShaderOptionsBase.this.self().properties().profiles().keySet());
            int x = MinecraftReflectionCompat.guiButtonX(this);
            int y = MinecraftReflectionCompat.guiButtonY(this);
            int width = MinecraftReflectionCompat.guiButtonWidth(this);
            int height = MinecraftReflectionCompat.guiButtonHeight(this);
            int top = y + height;
            int rows = visibleRows();
            int bottom = top + rows * height;
            MinecraftReflectionCompat.guiDrawRect(x, top, x + width, bottom, 0xEE121922);
            MinecraftReflectionCompat.guiDrawRect(x, top, x + width, top + 1, 0xFF4B5E73);
            MinecraftReflectionCompat.guiDrawRect(x, bottom - 1, x + width, bottom, 0xFF05080C);

            String selectedProfile = pendingValues.get("<profile>");
            for (int row = 0; row < rows; row++) {
                int i = scrollOffset + row;
                String profile = profiles.get(i);
                int rowTop = top + row * height;
                boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowTop && mouseY < rowTop + height;
                boolean selected = profile.equals(selectedProfile);
                if (hovered) {
                    MinecraftReflectionCompat.guiDrawRect(x + 1, rowTop + 1,
                            x + width - 1, rowTop + height - 1, 0xFF26384A);
                } else if (selected) {
                    MinecraftReflectionCompat.guiDrawRect(x + 1, rowTop + 1,
                            x + width - 1, rowTop + height - 1, 0xFF1D2D3C);
                }
                MinecraftReflectionCompat.fontDrawString(fontRenderer, GuiShaderOptionsBase.this.self().profileName(profile), x + 6, rowTop + 6, selected ? 0xFFFFFF : 0xC8CED6);
            }
            GuiShaderOptionsBase.this.self().drawScrollbar(x, width, top, bottom, profiles.size(), rows, scrollOffset);
        }
    }

    protected GuiShaderOptions self() {
        return (GuiShaderOptions) this;
    }
}
