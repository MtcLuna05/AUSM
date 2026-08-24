package com.luna.ausm.impl.client.gui;

import com.luna.ausm.api.pipeline.pack.ShaderOption;
import com.luna.ausm.api.pipeline.pack.ShaderOptions;
import com.luna.ausm.api.pipeline.pack.ShaderScreen;
import com.luna.ausm.api.pipeline.pack.ShaderScreenEntry;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.pack.ShaderProperties;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

abstract class GuiShaderOptionsLayout extends GuiShaderOptionsBase {
    @Override
    protected void ausm$initGui() {
        String activeOptionName = activeDropdown != null ? activeDropdown.option.name() : null;
        boolean profileDropdownOpen = activeProfileDropdown != null;
        self().rebuildButtons();
        self().restoreOpenDropdowns(activeOptionName, profileDropdownOpen);
    }

    @Override
    protected boolean ausm$doesGuiPauseGame() {
        return false;
    }

    protected void rebuildButtons() {
        self().buttonList.clear();
        self().profileButton = null;

        ShaderProperties properties = self().properties();
        if (!properties.screens().containsKey(selectedScreen)) {
            selectedScreen = properties.screens().containsKey("screen") ? "screen" : properties.screens().keySet().stream().findFirst().orElse("screen");
        }

        sidebarItems = self().sidebarItems(properties);
        sidebarWidth = self().computeSidebarWidth(properties);
        self().clampSidebarScroll();
        self().initSearchField();
        page = Math.min(page, self().maxPage());
        self().addCategoryButtons(properties);
        self().addOptionButtons(properties);

        int bottom = self().height - 28;
        self().buttonList.add(new GuiFlatButton(ID_DONE, self().width - 92, bottom, 82, 20,
                MinecraftReflectionCompat.i18nFormat("gui.done")));
        self().applyButton = new GuiFlatButton(ID_APPLY, self().width - 180, bottom, 82, 20, "Apply");
        MinecraftReflectionCompat.setGuiButtonEnabled(self().applyButton, self().isDirty());
        self().buttonList.add(self().applyButton);
        self().buttonList.add(new GuiFlatButton(ID_RESET, 12, bottom, 82, 20, "Reset"));
        self().previewButton = new GuiFlatButton(ID_PREVIEW, 100, bottom, 82, 20, "Preview");
        self().buttonList.add(self().previewButton);

        self().previousPageButton = new GuiFlatButton(ID_PREVIOUS_PAGE, self().width / 2 - 56, bottom, 24, 20, "<");
        self().nextPageButton = new GuiFlatButton(ID_NEXT_PAGE, self().width / 2 + 32, bottom, 24, 20, ">");
        int maxPage = self().maxPage();
        MinecraftReflectionCompat.setGuiButtonEnabled(self().previousPageButton, page > 0);
        MinecraftReflectionCompat.setGuiButtonEnabled(self().nextPageButton, page < maxPage);
        self().buttonList.add(previousPageButton);
        self().buttonList.add(nextPageButton);
        self().updatePreviewVisibility();
        self().ensureFocusedControl();
    }

    protected void restoreOpenDropdowns(String activeOptionName, boolean profileDropdownOpen) {
        activeDropdown = null;
        activeProfileDropdown = null;
        if (activeOptionName == null && !profileDropdownOpen) {
            return;
        }

        for (GuiButton button : buttonList) {
            if (activeOptionName != null && button instanceof GuiShaderOptions.GuiShaderOptionDropdown) {
                GuiShaderOptions.GuiShaderOptionDropdown dropdown = (GuiShaderOptions.GuiShaderOptionDropdown) button;
                if (dropdown.option.name().equals(activeOptionName)) {
                    activeDropdown = dropdown;
                    return;
                }
            }
            if (profileDropdownOpen && button instanceof GuiShaderOptions.GuiShaderProfileDropdown) {
                activeProfileDropdown = (GuiShaderOptions.GuiShaderProfileDropdown) button;
            }
        }
    }

    protected int preferredEntryWidth(ShaderOptions options, ShaderScreenEntry entry, int maxWidth) {
        ShaderScreenEntry.Type type = entry.type();
        if (type == ShaderScreenEntry.Type.SCREEN) {
            return Math.clamp(
                    self().contentButtonWidth(self().label(entry.name()) + "...", 22, 88, maxWidth),
                    1,
                    Math.max(1, maxWidth));
        }
        if (type == ShaderScreenEntry.Type.PROFILE) {
            int widestProfile = MinecraftReflectionCompat.fontStringWidth(fontRenderer, self().profileLabel()) + 30;
            for (String profile : self().properties().profiles().keySet()) {
                widestProfile = Math.max(widestProfile, MinecraftReflectionCompat.fontStringWidth(fontRenderer, self().profileName(profile)) + 30);
            }
            int boundedMaxWidth = Math.max(1, maxWidth);
            return Math.clamp(widestProfile, Math.min(116, boundedMaxWidth), boundedMaxWidth);
        }
        if (type == ShaderScreenEntry.Type.OPTION) {
            ShaderOption option = options.get(entry.name());
            int preferred;
            if (option == null) {
                preferred = self().contentButtonWidth(entry.name(), 16, 88, maxWidth);
            } else if (option.slider()) {
                preferred = self().sliderButtonWidth(option, maxWidth);
            } else if (option.choices().size() > 1 && !self().rendersAsToggle(option)) {
                preferred = self().dropdownButtonWidth(option, maxWidth);
            } else {
                preferred = self().contentButtonWidth(self().optionLabel(option), 16, 88, maxWidth);
            }
            return Math.clamp(preferred, 1, Math.max(1, maxWidth));
        }
        return Math.clamp(maxWidth, 1, 88);
    }

    protected GuiButton createEntryButton(ShaderOptions options, ShaderScreenEntry entry, int id, int x, int y, int width) {
        ShaderScreenEntry.Type type = entry.type();
        if (type == ShaderScreenEntry.Type.SCREEN) {
            return new GuiFlatButton(id, x, y, width, 20, self().label(entry.name()) + "...");
        }
        if (type == ShaderScreenEntry.Type.PROFILE) {
            self().profileButton = new GuiShaderOptions.GuiShaderProfileDropdown(id, x, y, width, 20);
            return self().profileButton;
        }
        if (type == ShaderScreenEntry.Type.OPTION) {
            ShaderOption option = options.get(entry.name());
            if (option == null) {
                self().logVoidOption("create-missing", null, "screen=" + selectedScreen + ", entry=" + entry.name() + ", id=" + id);
                GuiButton missing = new GuiFlatButton(id, x, y, width, 20, entry.name());
                MinecraftReflectionCompat.setGuiButtonEnabled(missing, false);
                return missing;
            }
            self().logVoidOption("create", option, "screen=" + selectedScreen
                    + ", entry=" + entry.name()
                    + ", id=" + id
                    + ", valueFor=" + self().valueFor(option)
                    + ", pending=" + pendingValues.get(option.name())
                    + ", saved=" + savedValues.get(option.name())
                    + ", slider=" + option.slider()
                    + ", toggle=" + option.toggle()
                    + ", rendersAsToggle=" + self().rendersAsToggle(option)
                    + ", choices=" + option.choices());
            if (option.slider() && option.choices().size() > 1) {
                return new GuiShaderOptions.GuiShaderOptionSlider(id, x, y, width, 20, option, self().valueFor(option));
            }
            if (option.choices().size() > 1 && !self().rendersAsToggle(option)) {
                return new GuiShaderOptions.GuiShaderOptionDropdown(id, x, y, width, 20, option, self().valueFor(option));
            }
            return new GuiFlatButton(id, x, y, width, 20, self().optionLabel(option));
        }
        return new GuiFlatButton(id, x, y, width, 20, "");
    }

    protected boolean isBooleanChoiceValue(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("0")
                || lower.equals("1")
                || lower.equals("false")
                || lower.equals("true")
                || lower.equals("off")
                || lower.equals("on");
    }

    protected void addCategoryButtons(ShaderProperties properties) {
        int y = self().sidebarListTop();
        int buttonRight = self().sidebarButtonRight(properties);
        int end = Math.min(sidebarItems.size(), sidebarScrollOffset + self().visibleSidebarRows());
        for (int i = sidebarScrollOffset; i < end; i++) {
            GuiShaderOptions.SidebarItem item = sidebarItems.get(i);
            String label = self().sidebarLabel(properties, item);
            int x = 12 + item.depth() * 10;
            int buttonWidth = Math.clamp(
                    buttonRight - x,
                    44,
                    Math.max(44, sidebarWidth - 24 - item.depth() * 10));
            GuiButton button = new GuiFlatButton(CATEGORY_BASE_ID + i, x, y, buttonWidth, 20, label);
            MinecraftReflectionCompat.setGuiButtonEnabled(button, !item.screen().equals(selectedScreen));
            self().buttonList.add(button);
            y += 22;
        }
    }

    protected void addOptionButtons(ShaderProperties properties) {
        if (self().searchActive()) {
            self().visibleEntries = self().searchEntries(properties);
        } else {
            ShaderScreen screen = properties.screens().get(selectedScreen);
            self().visibleEntries = screen == null ? List.of() : screen.entries().stream()
                    .filter(entry -> entry.type() != ShaderScreenEntry.Type.EMPTY)
                    .toList();
        }

        GuiShaderOptions.OptionGrid grid = self().optionGrid();
        int entriesPerPage = self().entriesPerPage(grid);
        int start = Math.min(page * entriesPerPage, visibleEntries.size());
        int end = Math.min(start + entriesPerPage, visibleEntries.size());
        int panelLeft = self().optionPanelLeft();
        int availableWidth = Math.max(100, self().width - panelLeft - 20);
        int rowWidth = Math.min(400, availableWidth);
        int columns = grid.columns();
        int x0 = panelLeft + (availableWidth - rowWidth) / 2;
        int y0 = OPTION_PANEL_TOP;
        int cellWidth = Math.max(100, (rowWidth - (columns - 1) * 8) / columns);
        // Every page shares one stable width.  Measuring only the visible page
        // made the grid jump whenever a long option existed off-screen.
        int visibleButtonWidth = self().visibleEntryButtonWidth(properties.options(), 0, visibleEntries.size(), cellWidth);

        for (int i = start; i < end; i++) {
            ShaderScreenEntry entry = visibleEntries.get(i);
            int local = i - start;
            int cellX = x0 + (local % columns) * (cellWidth + 8);
            int y = y0 + (local / columns) * 24;
            int buttonWidth = visibleButtonWidth;
            int x = cellX + (cellWidth - buttonWidth) / 2;

            GuiButton button = self().createEntryButton(properties.options(), entry, OPTION_BASE_ID + i, x, y, buttonWidth);
            self().buttonList.add(button);
        }
    }

    protected int visibleEntryButtonWidth(ShaderOptions options, int start, int end, int maxWidth) {
        int width = 88;
        for (int i = start; i < end; i++) {
            width = Math.max(width, self().preferredEntryWidth(options, visibleEntries.get(i), maxWidth));
        }
        return Math.clamp(width, 1, Math.max(1, maxWidth));
    }

    protected int sliderButtonWidth(ShaderOption option, int maxWidth) {
        int widestValue = 0;
        for (String choice : option.choices()) {
            widestValue = Math.max(widestValue, MinecraftReflectionCompat.fontStringWidth(fontRenderer, self().optionValue(option.name(), choice)));
        }
        int labelWidth = MinecraftReflectionCompat.fontStringWidth(fontRenderer, self().optionName(option.name()));
        return self().clamp(labelWidth + widestValue + 28, 130, maxWidth);
    }

    protected int dropdownButtonWidth(ShaderOption option, int maxWidth) {
        int widest = MinecraftReflectionCompat.fontStringWidth(fontRenderer, self().optionName(option.name()) + ": " + self().optionValue(option.name(), self().valueFor(option)));
        for (String choice : option.choices()) {
            widest = Math.max(widest, MinecraftReflectionCompat.fontStringWidth(fontRenderer, self().optionValue(option.name(), choice)) + 24);
        }
        return self().clamp(widest + 46, 120, maxWidth);
    }

    protected int contentButtonWidth(String text, int padding, int minWidth, int maxWidth) {
        return self().clamp(MinecraftReflectionCompat.fontStringWidth(fontRenderer, text) + padding, minWidth, maxWidth);
    }

    protected int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    @Override
    protected void ausm$handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            super.ausm$handleMouseInput();
            return;
        }
        if (previewHidden) {
            super.ausm$handleMouseInput();
            return;
        }

        int mouseX = Mouse.getEventX() * self().width / MinecraftReflectionCompat.displayWidth(self().mc);
        int mouseY = self().height - Mouse.getEventY() * self().height / MinecraftReflectionCompat.displayHeight(self().mc) - 1;
        int direction = wheel > 0 ? -1 : 1;
        if (activeDropdown != null) {
            if (activeDropdown.isMouseOver(mouseX, mouseY)) {
                activeDropdown.scroll(direction);
            }
            return;
        }
        if (activeProfileDropdown != null) {
            if (activeProfileDropdown.isMouseOver(mouseX, mouseY)) {
                activeProfileDropdown.scroll(direction);
            }
            return;
        }
        if (self().isMouseOverSidebar(mouseX, mouseY) && self().adjustSidebarScroll(direction)) {
            return;
        }
        if (!self().adjustHoveredOption(mouseX, mouseY, wheel > 0 ? 1 : -1)) {
            int nextPage = Math.clamp(page + direction, 0, self().maxPage());
            if (nextPage != page) {
                page = nextPage;
                self().rebuildButtons();
            }
        }
    }

    @Override
    protected void ausm$keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (GuiControlHints.isShiftDown()) {
                MinecraftReflectionCompat.displayGuiScreen(self().mc, null);
                return;
            }
            if (previewHidden) {
                self().setPreviewHidden(false);
                return;
            }
            if (self().searchActive()) {
                self().clearSearch();
                page = 0;
                self().rebuildButtons();
                return;
            }
            if (activeDropdown != null || activeProfileDropdown != null) {
                activeDropdown = null;
                activeProfileDropdown = null;
                return;
            }
            if (self().navigateBack()) {
                return;
            }
            MinecraftReflectionCompat.displayGuiScreen(self().mc, parent);
            return;
        }

        if (searchField != null && MinecraftReflectionCompat.guiTextFieldFocused(searchField)) {
            String before = MinecraftReflectionCompat.guiTextFieldText(searchField);
            if (MinecraftReflectionCompat.guiTextFieldKeyTyped(searchField, typedChar, keyCode)) {
                if (!Objects.equals(before, MinecraftReflectionCompat.guiTextFieldText(searchField))) {
                    page = 0;
                    activeDropdown = null;
                    activeProfileDropdown = null;
                    self().rebuildButtons();
                    if (searchField != null) {
                        MinecraftReflectionCompat.setGuiTextFieldFocused(searchField, true);
                    }
                }
                return;
            }
        }

        if (self().handleOpenDropdownKey(keyCode) || self().handleKeyboardNavigation(keyCode)) {
            return;
        }

        super.ausm$keyTyped(typedChar, keyCode);
    }

    @Override
    protected void ausm$actionPerformed(GuiButton button) throws IOException {
        if (!MinecraftReflectionCompat.guiButtonEnabled(button)) {
            return;
        }

        int buttonId = MinecraftReflectionCompat.guiButtonId(button);
        if (buttonId == ID_DONE) {
            MinecraftReflectionCompat.displayGuiScreen(self().mc, parent);
            return;
        }
        if (buttonId == ID_APPLY) {
            self().logVoidOption("apply", self().properties().options().get(DEBUG_VOID_OPTION), "pending=" + pendingValues.get(DEBUG_VOID_OPTION)
                    + ", saved=" + savedValues.get(DEBUG_VOID_OPTION)
                    + ", dirty=" + self().isDirty());
            MainMod.getShaderPackManager().setShaderOptions(packName, pendingValues);
            savedValues.clear();
            savedValues.putAll(pendingValues);
            if (applyButton != null) {
                MinecraftReflectionCompat.setGuiButtonEnabled(applyButton, false);
            }
            self().rebuildButtons();
            return;
        }
        if (buttonId == ID_RESET) {
            String resetProfile = self().resetProfile(self().properties());
            pendingValues.clear();
            if (resetProfile != null) {
                self().applyProfileValues(self().properties(), resetProfile);
            } else {
                self().syncProfileWithCurrentValues(properties);
            }
            if (applyButton != null) {
                MinecraftReflectionCompat.setGuiButtonEnabled(applyButton, self().isDirty());
            }
            self().rebuildButtons();
            return;
        }
        if (buttonId == ID_PREVIEW) {
            self().setPreviewHidden(!previewHidden);
            return;
        }
        if (buttonId == ID_PREVIOUS_PAGE) {
            page = Math.max(0, page - 1);
            self().rebuildButtons();
            return;
        }
        if (buttonId == ID_NEXT_PAGE) {
            page = Math.min(self().maxPage(), page + 1);
            self().rebuildButtons();
            return;
        }
        if (buttonId >= CATEGORY_BASE_ID && buttonId < OPTION_BASE_ID) {
            int index = buttonId - CATEGORY_BASE_ID;
            if (index >= 0 && index < sidebarItems.size()) {
                self().navigateToScreen(sidebarItems.get(index).screen());
            }
            return;
        }
        if (buttonId >= OPTION_BASE_ID) {
            self().handleEntryClick(button);
        }
    }

    protected void setPreviewHidden(boolean previewHidden) {
        self().previewHidden = previewHidden;
        if (previewHidden) {
            activeDropdown = null;
            activeProfileDropdown = null;
        }
        self().updatePreviewVisibility();
    }

    protected void updatePreviewVisibility() {
        for (GuiButton button : self().buttonList) {
            MinecraftReflectionCompat.setGuiButtonVisible(button,
                    !previewHidden || MinecraftReflectionCompat.guiButtonId(button) == ID_PREVIEW);
        }
        if (previewButton != null) {
            MinecraftReflectionCompat.setGuiButtonText(previewButton, previewHidden ? "Show GUI" : "Preview");
        }
    }

    @Override
    protected void ausm$mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (previewHidden) {
            super.ausm$mouseClicked(mouseX, mouseY, mouseButton);
            self().updateFocusFromMouse(mouseX, mouseY);
            return;
        }

        if (searchField != null && mouseButton == 1 && self().isMouseOverSearchField(mouseX, mouseY)) {
            if (!MinecraftReflectionCompat.guiTextFieldText(searchField).isEmpty()) {
                self().clearSearch();
                MinecraftReflectionCompat.setGuiTextFieldFocused(searchField, true);
                page = 0;
                activeDropdown = null;
                activeProfileDropdown = null;
                self().rebuildButtons();
                if (searchField != null) {
                    MinecraftReflectionCompat.setGuiTextFieldFocused(searchField, true);
                }
            }
            return;
        }

        if (searchField != null) {
            MinecraftReflectionCompat.guiTextFieldMouseClicked(searchField, mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 1 && self().handleSidebarRightClick(mouseX, mouseY)) {
            return;
        }

        if (activeProfileDropdown != null
                && mouseButton == 1
                && profileButton != null
                && self().isMouseOver(profileButton, mouseX, mouseY)) {
            self().applyPreviousProfile(self().properties());
            activeProfileDropdown = null;
            self().rebuildButtons();
            return;
        }

        if (activeProfileDropdown != null) {
            String selectedProfile = activeProfileDropdown.valueAt(mouseX, mouseY);
            if (selectedProfile != null) {
                self().applyProfile(self().properties(), selectedProfile);
                activeProfileDropdown = null;
                self().rebuildButtons();
                return;
            }

            activeProfileDropdown = null;
            return;
        }

        if (activeDropdown != null) {
            String selectedValue = activeDropdown.valueAt(mouseX, mouseY);
            if (selectedValue != null) {
                self().setPendingOptionValue(activeDropdown.option, selectedValue);
                activeDropdown = null;
                self().rebuildButtons();
                return;
            }

            activeDropdown = null;
            return;
        }

        if (mouseButton == 1 && profileButton != null && self().isMouseOver(profileButton, mouseX, mouseY)) {
            self().applyPreviousProfile(self().properties());
            activeProfileDropdown = null;
            self().rebuildButtons();
            return;
        }

        super.ausm$mouseClicked(mouseX, mouseY, mouseButton);
        self().updateFocusFromMouse(mouseX, mouseY);
    }

    protected void handleEntryClick(GuiButton button) {
        int buttonId = MinecraftReflectionCompat.guiButtonId(button);
        int index = buttonId - OPTION_BASE_ID;
        if (index < 0 || index >= visibleEntries.size()) {
            return;
        }

        ShaderProperties properties = self().properties();
        ShaderScreenEntry entry = visibleEntries.get(index);
        if (entry.type() == ShaderScreenEntry.Type.SCREEN && properties.screens().containsKey(entry.name())) {
            self().navigateToScreen(entry.name());
            return;
        }

        if (entry.type() == ShaderScreenEntry.Type.PROFILE) {
            if (button instanceof GuiShaderOptions.GuiShaderProfileDropdown) {
                GuiShaderOptions.GuiShaderProfileDropdown dropdown = (GuiShaderOptions.GuiShaderProfileDropdown) button;
                activeDropdown = null;
                activeProfileDropdown = activeProfileDropdown == dropdown ? null : dropdown;
                if (activeProfileDropdown == dropdown) {
                    dropdown.opened();
                }
            } else {
                self().applyNextProfile(properties);
                self().rebuildButtons();
            }
            return;
        }

        if (entry.type() != ShaderScreenEntry.Type.OPTION) {
            return;
        }

        ShaderOption option = properties.options().get(entry.name());
        if (option == null) {
            self().logVoidOption("click-missing", null,
                    "screen=" + selectedScreen + ", entry=" + entry.name() + ", id=" + buttonId);
            return;
        }
        self().logVoidOption("click", option, "screen=" + selectedScreen
                + ", button=" + button.getClass().getSimpleName()
                + ", current=" + self().valueFor(option)
                + ", pending=" + pendingValues.get(option.name())
                + ", next=" + option.withValue(self().valueFor(option)).nextValue()
                + ", choices=" + option.choices()
                + ", slider=" + option.slider()
                + ", toggle=" + option.toggle()
                + ", rendersAsToggle=" + self().rendersAsToggle(option));

        if (button instanceof GuiShaderOptions.GuiShaderOptionSlider) {
            GuiShaderOptions.GuiShaderOptionSlider slider = (GuiShaderOptions.GuiShaderOptionSlider) button;
            String value = slider.selectedValue();
            self().setPendingOptionValue(option, value);
            if (applyButton != null) {
                MinecraftReflectionCompat.setGuiButtonEnabled(applyButton, true);
            }
            return;
        } else if (button instanceof GuiShaderOptions.GuiShaderOptionDropdown) {
            GuiShaderOptions.GuiShaderOptionDropdown dropdown = (GuiShaderOptions.GuiShaderOptionDropdown) button;
            activeProfileDropdown = null;
            activeDropdown = activeDropdown == dropdown ? null : dropdown;
            if (activeDropdown == dropdown) {
                dropdown.opened();
            }
            return;
        } else {
            self().setPendingOptionValue(option, option.withValue(self().valueFor(option)).nextValue());
        }
        self().rebuildButtons();
    }

    protected void initSearchField() {
        String existing = searchField == null ? "" : MinecraftReflectionCompat.guiTextFieldText(searchField);
        boolean focused = searchField != null && MinecraftReflectionCompat.guiTextFieldFocused(searchField);
        searchField = new GuiTextField(900, fontRenderer, 12, 38, sidebarWidth - 24, 18);
        MinecraftReflectionCompat.setGuiTextFieldMaxLength(searchField, 80);
        MinecraftReflectionCompat.setGuiTextFieldBackground(searchField, true);
        MinecraftReflectionCompat.setGuiTextFieldText(searchField, existing);
        MinecraftReflectionCompat.setGuiTextFieldFocused(searchField, focused);
    }

    protected boolean searchActive() {
        return searchField != null && !MinecraftReflectionCompat.guiTextFieldText(searchField).trim().isEmpty();
    }

    protected List<ShaderScreenEntry> searchEntries(ShaderProperties properties) {
        String query = self().normalizeSearch(MinecraftReflectionCompat.guiTextFieldText(searchField));
        if (query.isEmpty()) {
            return List.of();
        }

        List<ShaderScreenEntry> entries = new ArrayList<>();
        Set<String> seenScreens = new HashSet<>();
        Set<String> seenOptions = new HashSet<>();
        for (String screen : properties.screens().keySet()) {
            if (self().matchesSearch(query, screen, self().label(screen), self().translationOrNull("screen." + screen + ".comment"))
                    && seenScreens.add(screen)) {
                entries.add(new ShaderScreenEntry(ShaderScreenEntry.Type.SCREEN, screen));
            }
        }
        Set<String> visibleOptionNames = self().visibleOptionNames(properties);
        for (String optionName : visibleOptionNames) {
            ShaderOption option = properties.options().get(optionName);
            if (option == null) {
                continue;
            }
            if (self().matchesSearch(query, option.name(), self().optionName(option.name()), self().optionComment(option.name()))
                    && seenOptions.add(option.name())) {
                entries.add(new ShaderScreenEntry(ShaderScreenEntry.Type.OPTION, option.name()));
            }
        }
        return entries;
    }

    protected Set<String> visibleOptionNames(ShaderProperties properties) {
        Set<String> names = new HashSet<>();
        for (ShaderScreen screen : properties.screens().values()) {
            for (ShaderScreenEntry entry : screen.entries()) {
                if (entry.type() == ShaderScreenEntry.Type.OPTION) {
                    names.add(entry.name());
                }
            }
        }
        return names;
    }

    protected boolean matchesSearch(String query, String... values) {
        for (String value : values) {
            if (value != null && self().normalizeSearch(value).contains(query)) {
                return true;
            }
        }
        return false;
    }

    protected String normalizeSearch(String value) {
        return self().stripFormatting(value)
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    protected String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    protected boolean adjustHoveredOption(int mouseX, int mouseY, int direction) {
        GuiButton button = self().adjustableButtonAt(mouseX, mouseY);
        if (button == null || !MinecraftReflectionCompat.guiButtonEnabled(button)) {
            return false;
        }

        if (button instanceof GuiShaderOptions.GuiShaderOptionSlider) {
            GuiShaderOptions.GuiShaderOptionSlider slider = (GuiShaderOptions.GuiShaderOptionSlider) button;
            if (GuiControlHints.isShiftDown()) {
                slider.stepFine(direction);
            } else {
                slider.stepNotch(direction);
            }
            return true;
        }
        if (button instanceof GuiShaderOptions.GuiShaderOptionDropdown) {
            GuiShaderOptions.GuiShaderOptionDropdown dropdown = (GuiShaderOptions.GuiShaderOptionDropdown) button;
            dropdown.step(direction);
            return true;
        }
        if (button instanceof GuiShaderOptions.GuiShaderProfileDropdown) {
            self().applyProfile(self().properties(), direction);
            self().rebuildButtons();
            return true;
        }

        int index = MinecraftReflectionCompat.guiButtonId(button) - OPTION_BASE_ID;
        if (index < 0 || index >= visibleEntries.size()) {
            return false;
        }
        ShaderScreenEntry entry = visibleEntries.get(index);
        if (entry.type() != ShaderScreenEntry.Type.OPTION) {
            return false;
        }

        ShaderOption option = self().properties().options().get(entry.name());
        if (option == null || option.choices().isEmpty()) {
            return false;
        }

        self().setOptionValue(option, self().shiftedChoice(option, self().valueFor(option), direction));
        return true;
    }

    protected GuiButton adjustableButtonAt(int mouseX, int mouseY) {
        for (GuiButton button : buttonList) {
            if (MinecraftReflectionCompat.guiButtonVisible(button) && self().isMouseOver(button, mouseX, mouseY)
                    && (button instanceof GuiShaderOptions.GuiShaderOptionSlider
                    || button instanceof GuiShaderOptions.GuiShaderOptionDropdown
                    || button instanceof GuiShaderOptions.GuiShaderProfileDropdown
                    || MinecraftReflectionCompat.guiButtonId(button) >= OPTION_BASE_ID)) {
                return button;
            }
        }
        return null;
    }

    protected void setOptionValue(ShaderOption option, String value) {
        self().setPendingOptionValue(option, value);
        if (applyButton != null) {
            MinecraftReflectionCompat.setGuiButtonEnabled(applyButton, true);
        }
        self().rebuildButtons();
    }

    protected void setPendingOptionValue(ShaderOption option, String value) {
        String previous = self().valueFor(option);
        if (option.defaultValue().equals(value)) {
            pendingValues.remove(option.name());
        } else {
            pendingValues.put(option.name(), value);
        }
        self().logVoidOption("set-pending", option, "previous=" + previous
                + ", requested=" + value
                + ", stored=" + pendingValues.get(option.name())
                + ", default=" + option.defaultValue()
                + ", dirty=" + self().isDirty());
        self().syncProfileWithCurrentValues(properties);
    }

    protected void logVoidOption(String stage, ShaderOption option, String detail) {
    }

    protected String shiftedChoice(ShaderOption option, String current, int direction) {
        if (option.choices().isEmpty()) {
            return current;
        }

        int index = option.choices().indexOf(current);
        if (index < 0) {
            index = option.choices().indexOf(option.defaultValue());
        }
        if (index < 0) {
            index = direction >= 0 ? -1 : 0;
        }
        return option.choices().get(Math.floorMod(index + direction, option.choices().size()));
    }

    protected String valueFor(ShaderOption option) {
        if (pendingValues.containsKey(option.name())) {
            return pendingValues.get(option.name());
        }
        // ShaderOption.value() belongs to the last loaded properties snapshot. If the
        // pending override was removed because the user selected the default value,
        // the live GUI state is the default, not the stale snapshot value.
        return option.defaultValue();
    }

    protected String optionLabel(ShaderOption option) {
        String value = self().valueFor(option);
        if (self().rendersAsToggle(option)) {
            value = option.withValue(value).asBoolean() ? "ON" : "OFF";
        }
        return self().optionName(option.name()) + ": " + self().optionValue(option.name(), value);
    }

    protected boolean rendersAsToggle(ShaderOption option) {
        return option.toggle() || self().isBinaryToggleOption(option);
    }

    protected boolean isBinaryToggleOption(ShaderOption option) {
        if (option.slider() || option.choices().size() != 2 || self().isEnumLikeBinaryOption(option.name())) {
            return false;
        }
        return option.choices().stream().allMatch(self()::isBooleanChoiceValue);
    }

    protected boolean isEnumLikeBinaryOption(String name) {
        return name.endsWith("_TYPE")
                || name.endsWith("_STYLE")
                || name.endsWith("_MODE")
                || name.endsWith("_SOURCE")
                || name.endsWith("_SLIDER");
    }
}
