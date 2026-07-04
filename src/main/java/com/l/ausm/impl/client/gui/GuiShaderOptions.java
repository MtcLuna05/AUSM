package com.l.ausm.impl.client.gui;

import com.l.ausm.impl.MainMod;
import com.l.ausm.api.pipeline.pack.ShaderOption;
import com.l.ausm.api.pipeline.pack.ShaderOptions;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.api.pipeline.pack.ShaderScreen;
import com.l.ausm.api.pipeline.pack.ShaderScreenEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GuiShaderOptions extends GuiScreen {
    private static final int ID_DONE = 200;
    private static final int ID_APPLY = 201;
    private static final int ID_RESET = 202;
    private static final int ID_PREVIOUS_PAGE = 203;
    private static final int ID_NEXT_PAGE = 204;
    private static final int ID_PREVIEW = 205;

    private static final int CATEGORY_BASE_ID = 1000;
    private static final int OPTION_BASE_ID = 3000;
    private static final String CUSTOM_PROFILE = "Custom";
    private static final int OPTION_PANEL_TOP = 42;
    private static final int OPTION_ROW_HEIGHT = 24;
    private static final int MIN_SIDEBAR_WIDTH = 128;
    private static final int MAX_SIDEBAR_WIDTH = 260;

    private final GuiScreen parent;
    private final String packName;
    private final Map<String, String> savedValues = new LinkedHashMap<>();
    private final Map<String, String> pendingValues = new LinkedHashMap<>();
    private final List<String> screenHistory = new ArrayList<>();
    private final Set<String> expandedSidebarScreens = new HashSet<>();
    private ShaderProperties properties;
    private String selectedScreen = "screen";
    private List<ShaderScreenEntry> visibleEntries = List.of();
    private List<SidebarItem> sidebarItems = List.of();
    private GuiTextField searchField;
    private GuiButton applyButton;
    private GuiButton previousPageButton;
    private GuiButton nextPageButton;
    private GuiButton profileButton;
    private GuiButton previewButton;
    private boolean previewHidden;
    private GuiShaderOptionDropdown activeDropdown;
    private GuiShaderProfileDropdown activeProfileDropdown;
    private List<String> hoveredCommentTitle = List.of();
    private List<String> hoveredCommentBody = List.of();
    private int page;
    private int sidebarWidth = MIN_SIDEBAR_WIDTH;
    private int sidebarScrollOffset;
    private int lastMouseX;
    private int lastMouseY;
    private int focusedControl = -1;

    public GuiShaderOptions(GuiScreen parent) {
        this(parent, MainMod.getShaderPackManager().getCurrentPack().getName());
    }

    public GuiShaderOptions(GuiScreen parent, String packName) {
        this.parent = parent;
        this.packName = packName;
        this.savedValues.putAll(MainMod.getShaderPackManager().getOptionOverrides(packName));
        this.pendingValues.putAll(savedValues);
        this.properties = MainMod.getShaderPackManager().getShaderProperties(packName, pendingValues);
        syncProfileWithCurrentValuesIfNeeded(this.properties);
    }

    @Override
    public void initGui() {
        String activeOptionName = activeDropdown != null ? activeDropdown.option.name() : null;
        boolean profileDropdownOpen = activeProfileDropdown != null;
        rebuildButtons();
        restoreOpenDropdowns(activeOptionName, profileDropdownOpen);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void rebuildButtons() {
        this.buttonList.clear();
        this.profileButton = null;

        ShaderProperties properties = properties();
        if (!properties.screens().containsKey(selectedScreen)) {
            selectedScreen = properties.screens().containsKey("screen") ? "screen" : properties.screens().keySet().stream().findFirst().orElse("screen");
        }

        sidebarItems = sidebarItems(properties);
        sidebarWidth = computeSidebarWidth(properties);
        clampSidebarScroll();
        initSearchField();
        page = Math.min(page, maxPage());
        addCategoryButtons(properties);
        addOptionButtons(properties);

        int bottom = this.height - 28;
        this.buttonList.add(new GuiFlatButton(ID_DONE, this.width - 92, bottom, 82, 20, I18n.format("gui.done")));
        this.applyButton = new GuiFlatButton(ID_APPLY, this.width - 180, bottom, 82, 20, "Apply");
        this.applyButton.enabled = isDirty();
        this.buttonList.add(this.applyButton);
        this.buttonList.add(new GuiFlatButton(ID_RESET, 12, bottom, 82, 20, "Reset"));
        this.previewButton = new GuiFlatButton(ID_PREVIEW, 100, bottom, 82, 20, "Preview");
        this.buttonList.add(this.previewButton);

        this.previousPageButton = new GuiFlatButton(ID_PREVIOUS_PAGE, this.width / 2 - 56, bottom, 24, 20, "<");
        this.nextPageButton = new GuiFlatButton(ID_NEXT_PAGE, this.width / 2 + 32, bottom, 24, 20, ">");
        int maxPage = maxPage();
        this.previousPageButton.enabled = page > 0;
        this.nextPageButton.enabled = page < maxPage;
        this.buttonList.add(previousPageButton);
        this.buttonList.add(nextPageButton);
        updatePreviewVisibility();
        ensureFocusedControl();
    }

    private void restoreOpenDropdowns(String activeOptionName, boolean profileDropdownOpen) {
        activeDropdown = null;
        activeProfileDropdown = null;
        if (activeOptionName == null && !profileDropdownOpen) {
            return;
        }

        for (GuiButton button : buttonList) {
            if (activeOptionName != null && button instanceof GuiShaderOptionDropdown) {
                GuiShaderOptionDropdown dropdown = (GuiShaderOptionDropdown) button;
                if (dropdown.option.name().equals(activeOptionName)) {
                    activeDropdown = dropdown;
                    return;
                }
            }
            if (profileDropdownOpen && button instanceof GuiShaderProfileDropdown) {
                activeProfileDropdown = (GuiShaderProfileDropdown) button;
            }
        }
    }

    private int preferredEntryWidth(ShaderOptions options, ShaderScreenEntry entry, int maxWidth) {
        ShaderScreenEntry.Type type = entry.type();
        if (type == ShaderScreenEntry.Type.SCREEN) {
            return Math.max(1, Math.min(maxWidth, contentButtonWidth(label(entry.name()) + "...", 22, 88, maxWidth)));
        }
        if (type == ShaderScreenEntry.Type.PROFILE) {
            int widestProfile = fontRenderer.getStringWidth(profileLabel()) + 30;
            for (String profile : properties().profiles().keySet()) {
                widestProfile = Math.max(widestProfile, fontRenderer.getStringWidth(profileName(profile)) + 30);
            }
            return Math.max(1, Math.min(maxWidth, clamp(widestProfile, 116, maxWidth)));
        }
        if (type == ShaderScreenEntry.Type.OPTION) {
            ShaderOption option = options.get(entry.name());
            int preferred;
            if (option == null) {
                preferred = contentButtonWidth(entry.name(), 16, 88, maxWidth);
            } else if (option.slider()) {
                preferred = sliderButtonWidth(option, maxWidth);
            } else if (option.choices().size() > 1 && !rendersAsToggle(option)) {
                preferred = dropdownButtonWidth(option, maxWidth);
            } else {
                preferred = contentButtonWidth(optionLabel(option), 16, 88, maxWidth);
            }
            return Math.max(1, Math.min(maxWidth, preferred));
        }
        return Math.max(1, Math.min(maxWidth, 88));
    }

    private GuiButton createEntryButton(ShaderOptions options, ShaderScreenEntry entry, int id, int x, int y, int width) {
        ShaderScreenEntry.Type type = entry.type();
        if (type == ShaderScreenEntry.Type.SCREEN) {
            return new GuiFlatButton(id, x, y, width, 20, label(entry.name()) + "...");
        }
        if (type == ShaderScreenEntry.Type.PROFILE) {
            this.profileButton = new GuiShaderProfileDropdown(id, x, y, width, 20);
            return this.profileButton;
        }
        if (type == ShaderScreenEntry.Type.OPTION) {
            ShaderOption option = options.get(entry.name());
            if (option == null) {
                GuiButton missing = new GuiFlatButton(id, x, y, width, 20, entry.name());
                missing.enabled = false;
                return missing;
            }
            if (option.slider() && option.choices().size() > 1) {
                return new GuiShaderOptionSlider(id, x, y, width, 20, option, valueFor(option));
            }
            if (option.choices().size() > 1 && !rendersAsToggle(option)) {
                return new GuiShaderOptionDropdown(id, x, y, width, 20, option, valueFor(option));
            }
            return new GuiFlatButton(id, x, y, width, 20, optionLabel(option));
        }
        return new GuiFlatButton(id, x, y, width, 20, "");
    }

    private boolean isBooleanChoiceValue(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("0")
                || lower.equals("1")
                || lower.equals("false")
                || lower.equals("true")
                || lower.equals("off")
                || lower.equals("on");
    }

    private static final class OptionGrid {
        private final int columns;
        private final int rows;

        private OptionGrid(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }

        private int columns() {
            return columns;
        }

        private int rows() {
            return rows;
        }
    }

    private static final class SidebarItem {
        private final String screen;
        private final int depth;

        private SidebarItem(String screen, int depth) {
            this.screen = screen;
            this.depth = depth;
        }

        private String screen() {
            return screen;
        }

        private int depth() {
            return depth;
        }
    }

    private void addCategoryButtons(ShaderProperties properties) {
        int y = sidebarListTop();
        int buttonRight = sidebarButtonRight(properties);
        int end = Math.min(sidebarItems.size(), sidebarScrollOffset + visibleSidebarRows());
        for (int i = sidebarScrollOffset; i < end; i++) {
            SidebarItem item = sidebarItems.get(i);
            String label = sidebarLabel(properties, item);
            int x = 12 + item.depth() * 10;
            int buttonWidth = Math.max(44, Math.min(sidebarWidth - 24 - item.depth() * 10, buttonRight - x));
            GuiButton button = new GuiFlatButton(CATEGORY_BASE_ID + i, x, y, buttonWidth, 20, label);
            button.enabled = !item.screen().equals(selectedScreen);
            this.buttonList.add(button);
            y += 22;
        }
    }

    private void addOptionButtons(ShaderProperties properties) {
        if (searchActive()) {
            this.visibleEntries = searchEntries(properties);
        } else {
            ShaderScreen screen = properties.screens().get(selectedScreen);
            this.visibleEntries = screen == null ? List.of() : screen.entries().stream()
                    .filter(entry -> entry.type() != ShaderScreenEntry.Type.EMPTY)
                    .toList();
        }

        OptionGrid grid = optionGrid();
        int entriesPerPage = entriesPerPage(grid);
        int start = Math.min(page * entriesPerPage, visibleEntries.size());
        int end = Math.min(start + entriesPerPage, visibleEntries.size());
        int panelLeft = optionPanelLeft();
        int availableWidth = Math.max(100, this.width - panelLeft - 20);
        int rowWidth = Math.min(400, availableWidth);
        int columns = grid.columns();
        int x0 = panelLeft + (availableWidth - rowWidth) / 2;
        int y0 = OPTION_PANEL_TOP;
        int cellWidth = Math.max(100, (rowWidth - (columns - 1) * 8) / columns);
        int visibleButtonWidth = visibleEntryButtonWidth(properties.options(), start, end, cellWidth);

        for (int i = start; i < end; i++) {
            ShaderScreenEntry entry = visibleEntries.get(i);
            int local = i - start;
            int cellX = x0 + (local % columns) * (cellWidth + 8);
            int y = y0 + (local / columns) * 24;
            int buttonWidth = visibleButtonWidth;
            int x = cellX + (cellWidth - buttonWidth) / 2;

            GuiButton button = createEntryButton(properties.options(), entry, OPTION_BASE_ID + i, x, y, buttonWidth);
            this.buttonList.add(button);
        }
    }

    private int visibleEntryButtonWidth(ShaderOptions options, int start, int end, int maxWidth) {
        int width = 88;
        for (int i = start; i < end; i++) {
            width = Math.max(width, preferredEntryWidth(options, visibleEntries.get(i), maxWidth));
        }
        return Math.max(1, Math.min(maxWidth, width));
    }

    private int sliderButtonWidth(ShaderOption option, int maxWidth) {
        int widestValue = 0;
        for (String choice : option.choices()) {
            widestValue = Math.max(widestValue, fontRenderer.getStringWidth(optionValue(option.name(), choice)));
        }
        int labelWidth = fontRenderer.getStringWidth(optionName(option.name()));
        return clamp(labelWidth + widestValue + 28, 130, maxWidth);
    }

    private int dropdownButtonWidth(ShaderOption option, int maxWidth) {
        int widest = fontRenderer.getStringWidth(optionName(option.name()) + ": " + optionValue(option.name(), valueFor(option)));
        for (String choice : option.choices()) {
            widest = Math.max(widest, fontRenderer.getStringWidth(optionValue(option.name(), choice)) + 24);
        }
        return clamp(widest + 30, 104, maxWidth);
    }

    private int contentButtonWidth(String text, int padding, int minWidth, int maxWidth) {
        return clamp(fontRenderer.getStringWidth(text) + padding, minWidth, maxWidth);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            super.handleMouseInput();
            return;
        }
        if (previewHidden) {
            super.handleMouseInput();
            return;
        }

        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
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
        if (isMouseOverSidebar(mouseX, mouseY) && adjustSidebarScroll(direction)) {
            return;
        }
        if (!adjustHoveredOption(mouseX, mouseY, wheel > 0 ? 1 : -1)) {
            int nextPage = Math.max(0, Math.min(maxPage(), page + direction));
            if (nextPage != page) {
                page = nextPage;
                rebuildButtons();
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (GuiControlHints.isShiftDown()) {
                this.mc.displayGuiScreen(null);
                return;
            }
            if (previewHidden) {
                setPreviewHidden(false);
                return;
            }
            if (searchActive()) {
                clearSearch();
                page = 0;
                rebuildButtons();
                return;
            }
            if (activeDropdown != null || activeProfileDropdown != null) {
                activeDropdown = null;
                activeProfileDropdown = null;
                return;
            }
            if (navigateBack()) {
                return;
            }
            this.mc.displayGuiScreen(parent);
            return;
        }

        if (searchField != null && searchField.isFocused()) {
            String before = searchField.getText();
            if (searchField.textboxKeyTyped(typedChar, keyCode)) {
                if (!Objects.equals(before, searchField.getText())) {
                    page = 0;
                    activeDropdown = null;
                    activeProfileDropdown = null;
                    rebuildButtons();
                    if (searchField != null) {
                        searchField.setFocused(true);
                    }
                }
                return;
            }
        }

        if (handleOpenDropdownKey(keyCode) || handleKeyboardNavigation(keyCode)) {
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }

        if (button.id == ID_DONE) {
            this.mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == ID_APPLY) {
            MainMod.getShaderPackManager().setShaderOptions(packName, pendingValues);
            savedValues.clear();
            savedValues.putAll(pendingValues);
            if (applyButton != null) {
                applyButton.enabled = false;
            }
            rebuildButtons();
            return;
        }
        if (button.id == ID_RESET) {
            String resetProfile = resetProfile(properties());
            pendingValues.clear();
            if (resetProfile != null) {
                applyProfileValues(properties(), resetProfile);
            } else {
                syncProfileWithCurrentValues(properties);
            }
            if (applyButton != null) {
                applyButton.enabled = isDirty();
            }
            rebuildButtons();
            return;
        }
        if (button.id == ID_PREVIEW) {
            setPreviewHidden(!previewHidden);
            return;
        }
        if (button.id == ID_PREVIOUS_PAGE) {
            page = Math.max(0, page - 1);
            rebuildButtons();
            return;
        }
        if (button.id == ID_NEXT_PAGE) {
            page = Math.min(maxPage(), page + 1);
            rebuildButtons();
            return;
        }
        if (button.id >= CATEGORY_BASE_ID && button.id < OPTION_BASE_ID) {
            int index = button.id - CATEGORY_BASE_ID;
            if (index >= 0 && index < sidebarItems.size()) {
                navigateToScreen(sidebarItems.get(index).screen());
            }
            return;
        }
        if (button.id >= OPTION_BASE_ID) {
            handleEntryClick(button);
        }
    }

    private void setPreviewHidden(boolean previewHidden) {
        this.previewHidden = previewHidden;
        if (previewHidden) {
            activeDropdown = null;
            activeProfileDropdown = null;
        }
        updatePreviewVisibility();
    }

    private void updatePreviewVisibility() {
        for (GuiButton button : this.buttonList) {
            button.visible = !previewHidden || button.id == ID_PREVIEW;
        }
        if (previewButton != null) {
            previewButton.displayString = previewHidden ? "Show GUI" : "Preview";
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (previewHidden) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            updateFocusFromMouse(mouseX, mouseY);
            return;
        }

        if (searchField != null && mouseButton == 1 && isMouseOverSearchField(mouseX, mouseY)) {
            if (!searchField.getText().isEmpty()) {
                clearSearch();
                searchField.setFocused(true);
                page = 0;
                activeDropdown = null;
                activeProfileDropdown = null;
                rebuildButtons();
                if (searchField != null) {
                    searchField.setFocused(true);
                }
            }
            return;
        }

        if (searchField != null) {
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 1 && handleSidebarRightClick(mouseX, mouseY)) {
            return;
        }

        if (activeProfileDropdown != null
                && mouseButton == 1
                && profileButton != null
                && isMouseOver(profileButton, mouseX, mouseY)) {
            applyPreviousProfile(properties());
            activeProfileDropdown = null;
            rebuildButtons();
            return;
        }

        if (activeProfileDropdown != null) {
            String selectedProfile = activeProfileDropdown.valueAt(mouseX, mouseY);
            if (selectedProfile != null) {
                applyProfile(properties(), selectedProfile);
                activeProfileDropdown = null;
                rebuildButtons();
                return;
            }

            activeProfileDropdown = null;
            return;
        }

        if (activeDropdown != null) {
            String selectedValue = activeDropdown.valueAt(mouseX, mouseY);
            if (selectedValue != null) {
                setPendingOptionValue(activeDropdown.option, selectedValue);
                activeDropdown = null;
                rebuildButtons();
                return;
            }

            activeDropdown = null;
            return;
        }

        if (mouseButton == 1 && profileButton != null && isMouseOver(profileButton, mouseX, mouseY)) {
            applyPreviousProfile(properties());
            activeProfileDropdown = null;
            rebuildButtons();
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
        updateFocusFromMouse(mouseX, mouseY);
    }

    private void handleEntryClick(GuiButton button) {
        int index = button.id - OPTION_BASE_ID;
        if (index < 0 || index >= visibleEntries.size()) {
            return;
        }

        ShaderProperties properties = properties();
        ShaderScreenEntry entry = visibleEntries.get(index);
        if (entry.type() == ShaderScreenEntry.Type.SCREEN && properties.screens().containsKey(entry.name())) {
            navigateToScreen(entry.name());
            return;
        }

        if (entry.type() == ShaderScreenEntry.Type.PROFILE) {
            if (button instanceof GuiShaderProfileDropdown) {
                GuiShaderProfileDropdown dropdown = (GuiShaderProfileDropdown) button;
                activeDropdown = null;
                activeProfileDropdown = activeProfileDropdown == dropdown ? null : dropdown;
                if (activeProfileDropdown == dropdown) {
                    dropdown.opened();
                }
            } else {
                applyNextProfile(properties);
                rebuildButtons();
            }
            return;
        }

        if (entry.type() != ShaderScreenEntry.Type.OPTION) {
            return;
        }

        ShaderOption option = properties.options().get(entry.name());
        if (option == null) {
            return;
        }

        if (button instanceof GuiShaderOptionSlider) {
            GuiShaderOptionSlider slider = (GuiShaderOptionSlider) button;
            String value = slider.selectedValue();
            setPendingOptionValue(option, value);
            if (applyButton != null) {
                applyButton.enabled = true;
            }
            return;
        } else if (button instanceof GuiShaderOptionDropdown) {
            GuiShaderOptionDropdown dropdown = (GuiShaderOptionDropdown) button;
            activeProfileDropdown = null;
            activeDropdown = activeDropdown == dropdown ? null : dropdown;
            if (activeDropdown == dropdown) {
                dropdown.opened();
            }
            return;
        } else {
            setPendingOptionValue(option, option.withValue(valueFor(option)).nextValue());
        }
        rebuildButtons();
    }

    private void initSearchField() {
        String existing = searchField == null ? "" : searchField.getText();
        boolean focused = searchField != null && searchField.isFocused();
        searchField = new GuiTextField(900, fontRenderer, 12, 38, sidebarWidth - 24, 18);
        searchField.setMaxStringLength(80);
        searchField.setEnableBackgroundDrawing(true);
        searchField.setText(existing);
        searchField.setFocused(focused);
    }

    private boolean searchActive() {
        return searchField != null && !searchField.getText().trim().isEmpty();
    }

    private List<ShaderScreenEntry> searchEntries(ShaderProperties properties) {
        String query = normalizeSearch(searchField.getText());
        if (query.isEmpty()) {
            return List.of();
        }

        List<ShaderScreenEntry> entries = new ArrayList<>();
        Set<String> seenScreens = new HashSet<>();
        Set<String> seenOptions = new HashSet<>();
        for (String screen : properties.screens().keySet()) {
            if (matchesSearch(query, screen, label(screen), translationOrNull("screen." + screen + ".comment"))
                    && seenScreens.add(screen)) {
                entries.add(new ShaderScreenEntry(ShaderScreenEntry.Type.SCREEN, screen));
            }
        }
        Set<String> visibleOptionNames = visibleOptionNames(properties);
        for (String optionName : visibleOptionNames) {
            ShaderOption option = properties.options().get(optionName);
            if (option == null) {
                continue;
            }
            if (matchesSearch(query, option.name(), optionName(option.name()), optionComment(option.name()))
                    && seenOptions.add(option.name())) {
                entries.add(new ShaderScreenEntry(ShaderScreenEntry.Type.OPTION, option.name()));
            }
        }
        return entries;
    }

    private Set<String> visibleOptionNames(ShaderProperties properties) {
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

    private boolean matchesSearch(String query, String... values) {
        for (String value : values) {
            if (value != null && normalizeSearch(value).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSearch(String value) {
        return stripFormatting(value)
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private boolean adjustHoveredOption(int mouseX, int mouseY, int direction) {
        GuiButton button = adjustableButtonAt(mouseX, mouseY);
        if (button == null || !button.enabled) {
            return false;
        }

        if (button instanceof GuiShaderOptionSlider) {
            GuiShaderOptionSlider slider = (GuiShaderOptionSlider) button;
            if (isShiftKeyDown()) {
                slider.stepFine(direction);
            } else {
                slider.stepNotch(direction);
            }
            return true;
        }
        if (button instanceof GuiShaderOptionDropdown) {
            GuiShaderOptionDropdown dropdown = (GuiShaderOptionDropdown) button;
            dropdown.step(direction);
            return true;
        }
        if (button instanceof GuiShaderProfileDropdown) {
            applyProfile(properties(), direction);
            rebuildButtons();
            return true;
        }

        int index = button.id - OPTION_BASE_ID;
        if (index < 0 || index >= visibleEntries.size()) {
            return false;
        }
        ShaderScreenEntry entry = visibleEntries.get(index);
        if (entry.type() != ShaderScreenEntry.Type.OPTION) {
            return false;
        }

        ShaderOption option = properties().options().get(entry.name());
        if (option == null || option.choices().isEmpty()) {
            return false;
        }

        setOptionValue(option, shiftedChoice(option, valueFor(option), direction));
        return true;
    }

    private GuiButton adjustableButtonAt(int mouseX, int mouseY) {
        for (GuiButton button : buttonList) {
            if (button.visible && isMouseOver(button, mouseX, mouseY)
                    && (button instanceof GuiShaderOptionSlider
                    || button instanceof GuiShaderOptionDropdown
                    || button instanceof GuiShaderProfileDropdown
                    || button.id >= OPTION_BASE_ID)) {
                return button;
            }
        }
        return null;
    }

    private void setOptionValue(ShaderOption option, String value) {
        setPendingOptionValue(option, value);
        if (applyButton != null) {
            applyButton.enabled = true;
        }
        rebuildButtons();
    }

    private void setPendingOptionValue(ShaderOption option, String value) {
        if (option.defaultValue().equals(value)) {
            pendingValues.remove(option.name());
        } else {
            pendingValues.put(option.name(), value);
        }
        syncProfileWithCurrentValues(properties);
    }

    private String shiftedChoice(ShaderOption option, String current, int direction) {
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

    private String valueFor(ShaderOption option) {
        return pendingValues.getOrDefault(option.name(), option.value());
    }

    private String optionLabel(ShaderOption option) {
        String value = valueFor(option);
        if (rendersAsToggle(option)) {
            value = option.withValue(value).asBoolean() ? "ON" : "OFF";
        }
        return optionName(option.name()) + ": " + optionValue(option.name(), value);
    }

    private boolean rendersAsToggle(ShaderOption option) {
        return option.toggle() || isBinaryToggleOption(option);
    }

    private boolean isBinaryToggleOption(ShaderOption option) {
        if (option.slider() || option.choices().size() != 2 || isEnumLikeBinaryOption(option.name())) {
            return false;
        }
        return option.choices().stream().allMatch(this::isBooleanChoiceValue);
    }

    private boolean isEnumLikeBinaryOption(String name) {
        return name.endsWith("_TYPE")
                || name.endsWith("_STYLE")
                || name.endsWith("_MODE")
                || name.endsWith("_SOURCE")
                || name.endsWith("_SLIDER");
    }

    private boolean isDirty() {
        return !comparableOptions(pendingValues).equals(comparableOptions(savedValues));
    }

    private Map<String, String> comparableOptions(Map<String, String> values) {
        Map<String, String> comparable = new LinkedHashMap<>(values);
        comparable.remove("<profile>");
        return comparable;
    }

    private String profileLabel() {
        String selected = pendingValues.getOrDefault("<profile>", CUSTOM_PROFILE);
        return "Profile: " + profileName(selected);
    }

    private void applyNextProfile(ShaderProperties properties) {
        applyProfile(properties, 1);
    }

    private void applyPreviousProfile(ShaderProperties properties) {
        applyProfile(properties, -1);
    }

    private void applyProfile(ShaderProperties properties, int direction) {
        if (properties.profiles().isEmpty()) {
            return;
        }

        List<String> names = new ArrayList<>(properties.profiles().keySet());
        String current = pendingValues.get("<profile>");
        int index = current == null ? -1 : names.indexOf(current);
        int nextIndex = index < 0
                ? (direction < 0 ? names.size() - 1 : 0)
                : Math.floorMod(index + direction, names.size());
        String next = names.get(nextIndex);
        applyProfileValues(properties, next);
    }

    private void applyProfile(ShaderProperties properties, String profile) {
        if (!properties.profiles().containsKey(profile)) {
            return;
        }
        applyProfileValues(properties, profile);
    }

    private void applyProfileValues(ShaderProperties properties, String profile) {
        properties.options().all().keySet().forEach(pendingValues::remove);
        pendingValues.putAll(properties.profileOverrides(profile));
        pendingValues.put("<profile>", profile);
        syncProfileWithCurrentValues(properties);
    }

    private String resetProfile(ShaderProperties properties) {
        String selected = pendingValues.get("<profile>");
        if (selected != null && !CUSTOM_PROFILE.equals(selected) && properties.profiles().containsKey(selected)) {
            return selected;
        }

        String saved = MainMod.getShaderPackManager().getOptionOverrides(packName).get("<profile>");
        if (saved != null && !CUSTOM_PROFILE.equals(saved) && properties.profiles().containsKey(saved)) {
            return saved;
        }

        String matchingDefault = matchingProfile(properties, Map.of());
        if (matchingDefault != null) {
            return matchingDefault;
        }

        return properties.profiles().keySet().stream().findFirst().orElse(null);
    }

    private void syncProfileWithCurrentValues(ShaderProperties properties) {
        String profile = matchingProfile(properties);
        pendingValues.put("<profile>", profile == null ? CUSTOM_PROFILE : profile);
        if (profileButton != null) {
            profileButton.displayString = profileLabel();
        }
    }

    private void syncProfileWithCurrentValuesIfNeeded(ShaderProperties properties) {
        String selected = pendingValues.get("<profile>");
        if (selected == null || CUSTOM_PROFILE.equals(selected) || !properties.profiles().containsKey(selected)) {
            syncProfileWithCurrentValues(properties);
        }
    }

    private String matchingProfile(ShaderProperties properties) {
        return matchingProfile(properties, pendingValues);
    }

    private String matchingProfile(ShaderProperties properties, Map<String, String> values) {
        if (properties.profiles().isEmpty()) {
            return null;
        }

        ShaderOptions options = properties.options();
        for (String key : values.keySet()) {
            if (!key.equals("<profile>") && !options.contains(key)) {
                return null;
            }
        }

        for (String profile : properties.profiles().keySet()) {
            Map<String, String> profileValues = properties.profileOverrides(profile);
            if (profileMatches(options, values, profileValues)) {
                return profile;
            }
        }
        return null;
    }

    private boolean profileMatches(ShaderOptions options, Map<String, String> values, Map<String, String> profileValues) {
        for (ShaderOption option : options.all().values()) {
            String current = values.getOrDefault(option.name(), option.defaultValue());
            String profileValue = profileValues.getOrDefault(option.name(), option.defaultValue());
            if (!Objects.equals(current, profileValue)) {
                return false;
            }
        }
        return true;
    }

    private boolean isMouseOver(GuiButton button, int mouseX, int mouseY) {
        return GuiControlHints.isMouseOverButton(button, mouseX, mouseY);
    }

    private int maxPage() {
        int entriesPerPage = entriesPerPage(optionGrid());
        return Math.max(0, (visibleEntries.size() - 1) / entriesPerPage);
    }

    private OptionGrid optionGrid() {
        int availableWidth = Math.max(100, this.width - optionPanelLeft() - 20);
        int rowWidth = Math.min(400, availableWidth);
        int columns = rowWidth >= 300 ? 2 : 1;
        int availableHeight = Math.max(OPTION_ROW_HEIGHT, this.height - OPTION_PANEL_TOP - 64);
        int rows = Math.max(1, availableHeight / OPTION_ROW_HEIGHT);
        return new OptionGrid(columns, rows);
    }

    private int entriesPerPage(OptionGrid grid) {
        return Math.max(1, grid.columns() * grid.rows());
    }

    private int optionPanelLeft() {
        return sidebarWidth + 16;
    }

    private int sidebarListTop() {
        return 62;
    }

    private int sidebarListBottom() {
        return Math.max(sidebarListTop() + 20, this.height - 36);
    }

    private int visibleSidebarRows() {
        return Math.max(1, (sidebarListBottom() - sidebarListTop()) / 22);
    }

    private int maxSidebarScroll() {
        return Math.max(0, sidebarItems.size() - visibleSidebarRows());
    }

    private void clampSidebarScroll() {
        sidebarScrollOffset = clamp(sidebarScrollOffset, 0, maxSidebarScroll());
    }

    private boolean adjustSidebarScroll(int direction) {
        int previous = sidebarScrollOffset;
        sidebarScrollOffset = clamp(sidebarScrollOffset + direction, 0, maxSidebarScroll());
        if (sidebarScrollOffset != previous) {
            rebuildButtons();
            return true;
        }
        return false;
    }

    private boolean isMouseOverSidebar(int mouseX, int mouseY) {
        return mouseX >= 8 && mouseX < sidebarWidth + 8 && mouseY >= 34 && mouseY < this.height - 34;
    }

    private boolean isMouseOverSearchField(int mouseX, int mouseY) {
        return searchField != null
                && mouseX >= searchField.x
                && mouseY >= searchField.y
                && mouseX < searchField.x + searchField.width
                && mouseY < searchField.y + searchField.height;
    }

    private int computeSidebarWidth(ShaderProperties properties) {
        int widest = MIN_SIDEBAR_WIDTH;
        List<SidebarItem> items = sidebarItems.isEmpty() ? sidebarItems(properties) : sidebarItems;
        for (SidebarItem item : items) {
            int width = 28 + item.depth() * 10 + fontRenderer.getStringWidth(sidebarLabel(properties, item));
            widest = Math.max(widest, width);
        }
        widest = Math.max(widest, 24 + fontRenderer.getStringWidth("Search options"));
        int maxByScreen = Math.max(MIN_SIDEBAR_WIDTH, this.width - 190);
        return Math.max(MIN_SIDEBAR_WIDTH, Math.min(Math.min(MAX_SIDEBAR_WIDTH, maxByScreen), widest));
    }

    private int sidebarButtonRight(ShaderProperties properties) {
        int right = 12 + 44;
        for (SidebarItem item : sidebarItems) {
            int width = 18 + fontRenderer.getStringWidth(sidebarLabel(properties, item));
            right = Math.max(right, 12 + item.depth() * 10 + width);
        }
        return Math.min(sidebarWidth - 12, right);
    }

    private void navigateToScreen(String screen) {
        if (screen == null || !properties().screens().containsKey(screen)) {
            return;
        }
        if (!screen.equals(selectedScreen)) {
            screenHistory.add(selectedScreen);
        }
        selectedScreen = screen;
        page = 0;
        activeDropdown = null;
        activeProfileDropdown = null;
        clearSearch();
        expandSidebarPathTo(screen);
        sidebarItems = sidebarItems(properties());
        ensureSelectedSidebarVisible();
        rebuildButtons();
    }

    private boolean navigateBack() {
        if (screenHistory.isEmpty()) {
            return false;
        }
        String previous = screenHistory.remove(screenHistory.size() - 1);
        if (!properties().screens().containsKey(previous)) {
            return navigateBack();
        }
        selectedScreen = previous;
        page = 0;
        activeDropdown = null;
        activeProfileDropdown = null;
        expandSidebarPathTo(previous);
        sidebarItems = sidebarItems(properties());
        ensureSelectedSidebarVisible();
        rebuildButtons();
        return true;
    }

    private boolean handleSidebarRightClick(int mouseX, int mouseY) {
        for (GuiButton button : buttonList) {
            if (button.id < CATEGORY_BASE_ID || button.id >= OPTION_BASE_ID || !button.visible || !isMouseOver(button, mouseX, mouseY)) {
                continue;
            }
            int index = button.id - CATEGORY_BASE_ID;
            if (index < 0 || index >= sidebarItems.size()) {
                return false;
            }
            SidebarItem item = sidebarItems.get(index);
            if (!hasSidebarChildren(properties(), item.screen())) {
                return false;
            }
            if (!expandedSidebarScreens.remove(item.screen())) {
                expandedSidebarScreens.add(item.screen());
            }
            clampSidebarScroll();
            rebuildButtons();
            return true;
        }
        return false;
    }

    private void clearSearch() {
        if (searchField != null) {
            searchField.setText("");
            searchField.setFocused(false);
        }
    }

    private void ensureSelectedSidebarVisible() {
        for (int i = 0; i < sidebarItems.size(); i++) {
            if (!sidebarItems.get(i).screen().equals(selectedScreen)) {
                continue;
            }
            if (i < sidebarScrollOffset) {
                sidebarScrollOffset = i;
            } else if (i >= sidebarScrollOffset + visibleSidebarRows()) {
                sidebarScrollOffset = i - visibleSidebarRows() + 1;
            }
            clampSidebarScroll();
            return;
        }
    }

    private void expandSidebarPathTo(String targetScreen) {
        List<String> path = findScreenPath(properties(), targetScreen);
        if (path.isEmpty()) {
            return;
        }
        for (int i = 0; i < path.size() - 1; i++) {
            expandedSidebarScreens.add(path.get(i));
        }
    }

    private List<String> findScreenPath(ShaderProperties properties, String targetScreen) {
        if (properties.screens().containsKey("screen")) {
            List<String> path = findScreenPath(properties, "screen", targetScreen, new HashSet<>());
            if (!path.isEmpty()) {
                return path;
            }
        }
        for (String screen : properties.screens().keySet()) {
            List<String> path = findScreenPath(properties, screen, targetScreen, new HashSet<>());
            if (!path.isEmpty()) {
                return path;
            }
        }
        return List.of();
    }

    private List<String> findScreenPath(ShaderProperties properties, String currentScreen, String targetScreen, Set<String> visited) {
        if (!visited.add(currentScreen)) {
            return List.of();
        }
        if (currentScreen.equals(targetScreen)) {
            return new ArrayList<>(List.of(currentScreen));
        }
        ShaderScreen screen = properties.screens().get(currentScreen);
        if (screen == null) {
            return List.of();
        }
        for (ShaderScreenEntry entry : screen.entries()) {
            if (entry.type() != ShaderScreenEntry.Type.SCREEN || !properties.screens().containsKey(entry.name())) {
                continue;
            }
            List<String> childPath = findScreenPath(properties, entry.name(), targetScreen, visited);
            if (!childPath.isEmpty()) {
                List<String> path = new ArrayList<>();
                path.add(currentScreen);
                path.addAll(childPath);
                return path;
            }
        }
        return List.of();
    }

    private List<SidebarItem> sidebarItems(ShaderProperties properties) {
        List<SidebarItem> items = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        if (properties.screens().containsKey("screen")) {
            items.add(new SidebarItem("screen", 0));
            visited.add("screen");
            addSidebarChildren(properties, "screen", 0, items, visited, true);
            return items;
        }
        for (String screen : properties.screens().keySet()) {
            items.add(new SidebarItem(screen, 0));
            if (expandedSidebarScreens.contains(screen)) {
                addSidebarChildren(properties, screen, 1, items, new HashSet<>(Set.of(screen)), false);
            }
        }
        return items;
    }

    private void addSidebarChildren(ShaderProperties properties, String screen, int depth, List<SidebarItem> items, Set<String> visited, boolean rootChildrenAlwaysVisible) {
        ShaderScreen shaderScreen = properties.screens().get(screen);
        if (shaderScreen == null || !rootChildrenAlwaysVisible && !expandedSidebarScreens.contains(screen)) {
            return;
        }
        for (ShaderScreenEntry entry : shaderScreen.entries()) {
            if (entry.type() != ShaderScreenEntry.Type.SCREEN || !properties.screens().containsKey(entry.name()) || visited.contains(entry.name())) {
                continue;
            }
            items.add(new SidebarItem(entry.name(), depth));
            visited.add(entry.name());
            addSidebarChildren(properties, entry.name(), depth + 1, items, visited, false);
            visited.remove(entry.name());
        }
    }

    private boolean hasSidebarChildren(ShaderProperties properties, String screen) {
        ShaderScreen shaderScreen = properties.screens().get(screen);
        if (shaderScreen == null) {
            return false;
        }
        return shaderScreen.entries().stream()
                .anyMatch(entry -> entry.type() == ShaderScreenEntry.Type.SCREEN && properties.screens().containsKey(entry.name()));
    }

    private String sidebarLabel(ShaderProperties properties, SidebarItem item) {
        if (item.screen().equals("screen")) {
            return label(item.screen());
        }
        String prefix = hasSidebarChildren(properties, item.screen())
                ? (expandedSidebarScreens.contains(item.screen()) ? "- " : "+ ")
                : "  ";
        return prefix + label(item.screen());
    }

    private String label(String id) {
        if (id.equals("screen")) {
            return "Main";
        }
        return properties().translate("screen." + id, id.replace('_', ' '));
    }

    private String optionName(String id) {
        return properties().translate("option." + id, id.replace('_', ' '));
    }

    private String optionValue(String option, String value) {
        return properties().translate("value." + option + "." + value, value);
    }

    private String profileName(String profile) {
        if (CUSTOM_PROFILE.equals(profile)) {
            return CUSTOM_PROFILE;
        }
        return properties().translate("profile." + profile, profile);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hoveredCommentTitle = List.of();
        hoveredCommentBody = List.of();
        if (previewHidden) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        drawRect(0, 0, this.width, this.height, 0x330B1016);
        drawRect(8, 34, sidebarWidth + 8, this.height - 34, 0x66101418);
        drawRect(optionPanelLeft() - 4, 34, this.width - 8, this.height - 34, 0x66101418);
        this.drawCenteredString(this.fontRenderer, "Shader Options - " + displayPackName(), this.width / 2, 16, 0xFFFFFF);
        String pageText = (page + 1) + " / " + (maxPage() + 1);
        this.drawCenteredString(this.fontRenderer, pageText, this.width / 2, this.height - 22, 0xA0A0A0);
        if (searchField != null) {
            searchField.drawTextBox();
            if (!searchField.isFocused() && searchField.getText().isEmpty()) {
                fontRenderer.drawString("Search options", searchField.x + 4, searchField.y + 5, 0xFF6F7E8D);
            }
        }
        drawSidebarScrollbar();
        boolean dropdownOpen = activeDropdown != null || activeProfileDropdown != null;
        super.drawScreen(dropdownOpen ? -1 : mouseX, dropdownOpen ? -1 : mouseY, partialTicks);
        drawFocusedButtonOutline();
        if (activeDropdown != null) {
            activeDropdown.drawDropdown(mouseX, mouseY);
        }
        if (activeProfileDropdown != null) {
            activeProfileDropdown.drawDropdown(mouseX, mouseY);
        }
        drawShaderTooltip(mouseX, mouseY);
        drawBottomCommentPanel();
        drawEscapeHintTooltip(mouseX, mouseY);
    }

    private void drawSidebarScrollbar() {
        int maxScroll = maxSidebarScroll();
        if (maxScroll <= 0) {
            return;
        }

        int trackLeft = sidebarWidth;
        int trackRight = sidebarWidth + 4;
        int trackTop = sidebarListTop();
        int trackBottom = sidebarListBottom() - 2;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(12, trackHeight * visibleSidebarRows() / Math.max(visibleSidebarRows(), sidebarItems.size()));
        int thumbY = trackTop + (trackHeight - thumbHeight) * sidebarScrollOffset / maxScroll;
        drawRect(trackLeft, trackTop, trackRight, trackBottom, 0x55182026);
        drawRect(trackLeft, thumbY, trackRight, thumbY + thumbHeight, 0xAA6E8197);
    }

    private void drawShaderTooltip(int mouseX, int mouseY) {
        if (activeDropdown != null) {
            return;
        }
        if (activeProfileDropdown != null) {
            return;
        }

        for (GuiButton button : buttonList) {
            if (!button.visible || !isMouseOver(button, mouseX, mouseY)) {
                continue;
            }

            List<String> tooltip = tooltipFor(button);
            if (!tooltip.isEmpty()) {
                setHoveredComment(button, tooltip);
            }
            return;
        }
    }

    private void setHoveredComment(GuiButton button, List<String> tooltip) {
        if (button instanceof GuiShaderOptionSlider) {
            GuiShaderOptionSlider slider = (GuiShaderOptionSlider) button;
            hoveredCommentTitle = List.of(optionName(slider.option.name()));
        } else if (button instanceof GuiShaderOptionDropdown) {
            GuiShaderOptionDropdown dropdown = (GuiShaderOptionDropdown) button;
            hoveredCommentTitle = List.of(optionName(dropdown.option.name()));
        } else if (button instanceof GuiShaderProfileDropdown) {
            hoveredCommentTitle = List.of("Profile");
        } else if (button.id >= OPTION_BASE_ID) {
            int index = button.id - OPTION_BASE_ID;
            if (index >= 0 && index < visibleEntries.size()) {
                hoveredCommentTitle = List.of(label(visibleEntries.get(index).name()));
            }
        } else {
            hoveredCommentTitle = List.of(button.displayString);
        }
        hoveredCommentBody = tooltip;
    }

    private void drawBottomCommentPanel() {
        if (hoveredCommentBody.isEmpty()) {
            return;
        }

        int panelWidth = Math.min(314, this.width - 24);
        int panelHeight = Math.max(50, 18 + hoveredCommentBody.size() * 10);
        int x = (this.width - panelWidth) / 2;
        int y = this.height - panelHeight - 36;

        drawRect(x, y, x + panelWidth, y + panelHeight, 0xDD101820);
        drawRect(x, y, x + panelWidth, y + 1, 0xFF42566D);
        drawRect(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xFF05080C);

        int titleColor = 0xFFFFFFFF;
        if (!hoveredCommentTitle.isEmpty()) {
            this.fontRenderer.drawString(hoveredCommentTitle.get(0), x + 5, y + 5, titleColor);
        }
        for (int i = 0; i < hoveredCommentBody.size(); i++) {
            this.fontRenderer.drawString(hoveredCommentBody.get(i), x + 5, y + 17 + i * 10, 0xFFD8DEE8);
        }
    }

    private boolean handleOpenDropdownKey(int keyCode) {
        if (activeDropdown != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_SPACE) {
                activeDropdown = null;
                return true;
            }
            if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_LEFT) {
                activeDropdown.step(-1);
                return true;
            }
            if (keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_RIGHT) {
                activeDropdown.step(1);
                return true;
            }
            return true;
        }

        if (activeProfileDropdown != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_SPACE) {
                activeProfileDropdown = null;
                return true;
            }
            if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_LEFT) {
                applyProfile(properties(), -1);
                rebuildButtons();
                activeProfileDropdown = null;
                return true;
            }
            if (keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_RIGHT) {
                applyProfile(properties(), 1);
                rebuildButtons();
                activeProfileDropdown = null;
                return true;
            }
            return true;
        }
        return false;
    }

    private boolean handleKeyboardNavigation(int keyCode) throws IOException {
        ensureFocusedControl();
        if (keyCode == Keyboard.KEY_TAB) {
            moveFocusLinear(isShiftKeyDown() ? -1 : 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_UP) {
            moveFocusSpatial(0, -1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            moveFocusSpatial(0, 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            GuiButton button = focusedButton();
            if (button instanceof GuiShaderOptionSlider) {
                GuiShaderOptionSlider slider = (GuiShaderOptionSlider) button;
                slider.step(-1);
            } else if (button instanceof GuiShaderOptionDropdown) {
                GuiShaderOptionDropdown dropdown = (GuiShaderOptionDropdown) button;
                dropdown.step(-1);
            } else if (button instanceof GuiShaderProfileDropdown) {
                applyProfile(properties(), -1);
                rebuildButtons();
            } else {
                moveFocusSpatial(-1, 0);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            GuiButton button = focusedButton();
            if (button instanceof GuiShaderOptionSlider) {
                GuiShaderOptionSlider slider = (GuiShaderOptionSlider) button;
                slider.step(1);
            } else if (button instanceof GuiShaderOptionDropdown) {
                GuiShaderOptionDropdown dropdown = (GuiShaderOptionDropdown) button;
                dropdown.step(1);
            } else if (button instanceof GuiShaderProfileDropdown) {
                applyProfile(properties(), 1);
                rebuildButtons();
            } else {
                moveFocusSpatial(1, 0);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_SPACE) {
            GuiButton button = focusedButton();
            if (button != null) {
                actionPerformed(button);
            }
            return true;
        }
        return false;
    }

    private void moveFocusLinear(int direction) {
        List<GuiButton> focusables = focusableButtons();
        if (focusables.isEmpty()) {
            focusedControl = -1;
            return;
        }
        GuiButton current = focusedButton();
        int currentIndex = current == null ? -1 : focusables.indexOf(current);
        int next = Math.floorMod(currentIndex + direction, focusables.size());
        focusedControl = buttonList.indexOf(focusables.get(next));
    }

    private void moveFocusSpatial(int dx, int dy) {
        List<GuiButton> focusables = focusableButtons();
        if (focusables.isEmpty()) {
            focusedControl = -1;
            return;
        }

        GuiButton current = focusedButton();
        if (current == null) {
            focusedControl = buttonList.indexOf(focusables.get(0));
            return;
        }

        int currentX = current.x + current.width / 2;
        int currentY = current.y + current.height / 2;
        GuiButton best = null;
        int bestScore = Integer.MAX_VALUE;
        for (GuiButton candidate : focusables) {
            if (candidate == current) {
                continue;
            }
            int candidateX = candidate.x + candidate.width / 2;
            int candidateY = candidate.y + candidate.height / 2;
            int deltaX = candidateX - currentX;
            int deltaY = candidateY - currentY;
            if (dx < 0 && deltaX >= 0 || dx > 0 && deltaX <= 0 || dy < 0 && deltaY >= 0 || dy > 0 && deltaY <= 0) {
                continue;
            }
            int primary = dx != 0 ? Math.abs(deltaX) : Math.abs(deltaY);
            int secondary = dx != 0 ? Math.abs(deltaY) : Math.abs(deltaX);
            int score = primary * 1000 + secondary;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null) {
            focusedControl = buttonList.indexOf(best);
        }
    }

    private void updateFocusFromMouse(int mouseX, int mouseY) {
        for (int i = 0; i < buttonList.size(); i++) {
            GuiButton button = buttonList.get(i);
            if (isFocusable(button) && isMouseOver(button, mouseX, mouseY)) {
                focusedControl = i;
                return;
            }
        }
    }

    private void ensureFocusedControl() {
        if (previewHidden) {
            focusedControl = indexOfButton(ID_PREVIEW);
            return;
        }
        if (focusedControl >= buttonList.size() || focusedControl >= 0 && !isFocusable(buttonList.get(focusedControl))) {
            List<GuiButton> focusables = focusableButtons();
            focusedControl = focusables.isEmpty() ? -1 : buttonList.indexOf(focusables.get(0));
        }
    }

    private GuiButton focusedButton() {
        if (focusedControl >= 0 && focusedControl < buttonList.size()) {
            GuiButton button = buttonList.get(focusedControl);
            return isFocusable(button) ? button : null;
        }
        return null;
    }

    private List<GuiButton> focusableButtons() {
        return buttonList.stream().filter(this::isFocusable).toList();
    }

    private boolean isFocusable(GuiButton button) {
        return GuiControlHints.isFocusable(button);
    }

    private int indexOfButton(int id) {
        for (int i = 0; i < buttonList.size(); i++) {
            if (buttonList.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    private void drawFocusedButtonOutline() {
        GuiControlHints.drawFocusedButtonOutline(focusedButton());
    }

    private void drawEscapeHintTooltip(int mouseX, int mouseY) {
        GuiControlHints.drawEscapeHintLabel(this.fontRenderer, this.width);
        if (GuiControlHints.isMouseOverEscapeHint(this.fontRenderer, this.width, mouseX, mouseY)) {
            drawHoveringText(GuiControlHints.escapeTooltip(), mouseX, mouseY);
        }
    }

    private List<String> tooltipFor(GuiButton button) {
        String comment = null;
        if (button instanceof GuiShaderProfileDropdown) {
            comment = translationOrNull("profile.comment");
        } else if (button instanceof GuiShaderOptionSlider) {
            GuiShaderOptionSlider slider = (GuiShaderOptionSlider) button;
            comment = optionComment(slider.option.name());
        } else if (button instanceof GuiShaderOptionDropdown) {
            GuiShaderOptionDropdown dropdown = (GuiShaderOptionDropdown) button;
            comment = optionComment(dropdown.option.name());
        } else if (button.id >= OPTION_BASE_ID) {
            int index = button.id - OPTION_BASE_ID;
            if (index >= 0 && index < visibleEntries.size()) {
                ShaderScreenEntry entry = visibleEntries.get(index);
                if (entry.type() == ShaderScreenEntry.Type.OPTION) {
                    comment = optionComment(entry.name());
                } else if (entry.type() == ShaderScreenEntry.Type.SCREEN) {
                    comment = translationOrNull("screen." + entry.name() + ".comment");
                } else if (entry.type() == ShaderScreenEntry.Type.PROFILE) {
                    comment = translationOrNull("profile.comment");
                }
            }
        } else if (button.id >= CATEGORY_BASE_ID && button.id < OPTION_BASE_ID) {
            int index = button.id - CATEGORY_BASE_ID;
            if (index >= 0 && index < sidebarItems.size()) {
                comment = translationOrNull("screen." + sidebarItems.get(index).screen() + ".comment");
            }
        }

        if (comment == null || comment.isBlank()) {
            return List.of();
        }
        return wrapTooltip(comment);
    }

    private String optionComment(String optionName) {
        String comment = translationOrNull("option." + optionName + ".comment");
        if (comment != null) {
            return comment;
        }
        return translationOrNull("options." + optionName + ".comment");
    }

    private String translationOrNull(String key) {
        return properties().translations().get(key);
    }

    private List<String> wrapTooltip(String text) {
        List<String> lines = new ArrayList<>();
        for (String segment : text.split("\\\\n|\\n")) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                lines.addAll(fontRenderer.listFormattedStringToWidth(trimmed, 260));
            }
        }
        return lines;
    }

    private ShaderProperties properties() {
        if (properties == null) {
            properties = MainMod.getShaderPackManager().getShaderProperties(packName, pendingValues);
        }
        return properties;
    }

    private String displayPackName() {
        return packName == null || packName.equals("(internal)") ? "OFF" : packName;
    }

    private final class GuiShaderOptionSlider extends GuiButton {
        private final ShaderOption option;
        private final boolean numeric;
        private final boolean continuous;
        private final double minValue;
        private final double maxValue;
        private final int scale;
        private float sliderValue;
        private boolean dragging;
        private String selectedValue;

        private GuiShaderOptionSlider(int buttonId, int x, int y, int widthIn, int heightIn, ShaderOption option, String currentValue) {
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

        private String selectedValue() {
            return selectedValue;
        }

        private String calculateSelectedValue() {
            if (numeric) {
                double value = minValue + sliderValue * (maxValue - minValue);
                return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
            }

            int index = Math.round(sliderValue * (option.choices().size() - 1));
            return option.choices().get(Math.max(0, Math.min(option.choices().size() - 1, index)));
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) {
                return;
            }
            if (dragging) {
                updateFromMouse(mouseX, false);
            }

            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            int background = hovered ? 0xFF202C38 : 0xFF151D26;
            drawRect(x, y, x + width, y + height, background);
            drawRect(x, y, x + width, y + 1, 0xFF40566C);
            drawRect(x, y + height - 1, x + width, y + height, 0xFF070B10);

            boolean showSlider = hovered || dragging;
            String leftText = optionName(option.name());
            String rightText = showSlider ? "" : optionValue(option.name(), selectedValue);
            int textColor = enabled ? 0xFFFFFF : 0x707070;
            if (!showSlider) {
                mc.fontRenderer.drawString(leftText, x + 7, y + 5, textColor);
                mc.fontRenderer.drawString(rightText, x + width - 7 - mc.fontRenderer.getStringWidth(rightText), y + 5, textColor);
            }

            if (showSlider) {
                int trackLeft = x + 2;
                int trackTop = y + 2;
                int trackWidth = width - 4;
                int trackHeight = height - 4;
                drawRect(trackLeft, trackTop, trackLeft + trackWidth, trackTop + trackHeight, 0xFF07101A);
                drawRect(trackLeft, trackTop, trackLeft + Math.round(sliderValue * trackWidth), trackTop + trackHeight, 0xFF204D7A);
                drawNotches(trackLeft, trackTop, trackWidth, trackHeight);

                int thumbWidth = 6;
                int thumbX = trackLeft + Math.round(sliderValue * (trackWidth - thumbWidth));
                drawRect(thumbX, trackTop + 2, thumbX + thumbWidth, trackTop + trackHeight - 2, dragging ? 0xFFFFFFFF : 0xFFE7EEF8);
                drawRect(thumbX + 1, trackTop + 3, thumbX + thumbWidth - 1, trackTop + trackHeight - 3, 0xFF7CB7FF);

                String valueText = optionValue(option.name(), selectedValue);
                mc.fontRenderer.drawString(
                        valueText,
                        x + width / 2 - mc.fontRenderer.getStringWidth(valueText) / 2,
                        y + 6,
                        textColor
                );
            }
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                if (isShiftKeyDown()) {
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
            if (visible && dragging) {
                updateFromMouse(mouseX, false);
            }
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            dragging = false;
        }

        private void updateFromMouse(int mouseX, boolean snapToNearbyNotch) {
            sliderValue = (float) (mouseX - (x + 7)) / (float) (width - 14);
            sliderValue = Math.max(0.0f, Math.min(1.0f, sliderValue));
            if (!continuous) {
                sliderValue = quantizedSliderValue(sliderValue);
            } else if (snapToNearbyNotch) {
                sliderValue = nearestNotchValue(mouseX, sliderValue);
            }
            selectedValue = calculateSelectedValue();
            setPendingOptionValue(option, selectedValue);
            updateDisplay();
            if (applyButton != null) {
                applyButton.enabled = true;
            }
        }

        private void updateDisplay() {
            selectedValue = calculateSelectedValue();
            this.displayString = optionName(option.name());
        }

        private void resetToDefault() {
            sliderValue = initialSliderValue(option.defaultValue());
            selectedValue = calculateSelectedValue();
            setPendingOptionValue(option, option.defaultValue());
            updateDisplay();
            if (applyButton != null) {
                applyButton.enabled = true;
            }
        }

        private void step(int direction) {
            stepFine(direction);
        }

        private void stepFine(int direction) {
            if (option.choices().isEmpty()) {
                return;
            }

            if (numeric) {
                double value = parseDouble(selectedValue, minValue) + numericStepSize() * direction;
                value = Math.max(minValue, Math.min(maxValue, value));
                String formatted = BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
                sliderValue = initialSliderValue(formatted);
            } else {
                String value = shiftedChoice(option, selectedValue, direction);
                sliderValue = initialSliderValue(value);
            }
            selectedValue = calculateSelectedValue();
            setPendingOptionValue(option, selectedValue);
            updateDisplay();
            if (applyButton != null) {
                applyButton.enabled = true;
            }
        }

        private void stepNotch(int direction) {
            if (option.choices().isEmpty()) {
                return;
            }

            String value = shiftedChoice(option, selectedValue, direction);
            sliderValue = initialSliderValue(value);
            selectedValue = calculateSelectedValue();
            setPendingOptionValue(option, selectedValue);
            updateDisplay();
            if (applyButton != null) {
                applyButton.enabled = true;
            }
        }

        private float initialSliderValue(String currentValue) {
            if (numeric) {
                double value = parseDouble(currentValue, minValue);
                if (maxValue == minValue) {
                    return 0.0f;
                }
                return (float) Math.max(0.0, Math.min(1.0, (value - minValue) / (maxValue - minValue)));
            }

            int index = Math.max(0, option.choices().indexOf(currentValue));
            return option.choices().size() <= 1 ? 0.0f : (float) index / (float) (option.choices().size() - 1);
        }

        private float nearestNotchValue(int mouseX, float fallback) {
            int railLeft = x + 7;
            int railWidth = width - 14;
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

        private float quantizedSliderValue(float value) {
            if (!numeric) {
                int index = Math.round(value * (option.choices().size() - 1));
                return option.choices().size() <= 1 ? 0.0f : (float) index / (float) (option.choices().size() - 1);
            }
            double rounded = Math.rint(minValue + value * (maxValue - minValue));
            if (maxValue == minValue) {
                return 0.0f;
            }
            return (float) Math.max(0.0, Math.min(1.0, (rounded - minValue) / (maxValue - minValue)));
        }

        private double numericStepSize() {
            return scale <= 0 ? 1.0 : Math.pow(10.0, -scale);
        }

        private void drawNotches(int trackLeft, int trackTop, int trackWidth, int trackHeight) {
            for (String choice : option.choices()) {
                int notchX = trackLeft + Math.round(choicePosition(choice) * trackWidth);
                drawRect(notchX, trackTop + trackHeight - 4, notchX + 1, trackTop + trackHeight - 1, 0xFFB7C7D8);
            }
        }

        private float choicePosition(String choice) {
            if (numeric) {
                double value = parseDouble(choice, minValue);
                if (maxValue == minValue) {
                    return 0.0f;
                }
                return (float) Math.max(0.0, Math.min(1.0, (value - minValue) / (maxValue - minValue)));
            }

            int index = Math.max(0, option.choices().indexOf(choice));
            return option.choices().size() <= 1 ? 0.0f : (float) index / (float) (option.choices().size() - 1);
        }

        private boolean allChoicesNumeric(ShaderOption option) {
            return option.choices().stream().allMatch(choice -> {
                try {
                    Double.parseDouble(choice);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            });
        }

        private double parseDouble(String value, double fallback) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private int maxScale(List<String> values) {
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

    private final class GuiShaderOptionDropdown extends GuiButton {
        private final ShaderOption option;
        private int scrollOffset;

        private GuiShaderOptionDropdown(int buttonId, int x, int y, int widthIn, int heightIn, ShaderOption option, String currentValue) {
            super(buttonId, x, y, widthIn, heightIn, "");
            this.option = option;
            updateDisplay(currentValue);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) {
                return;
            }

            boolean open = activeDropdown == this;
            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            int background = open ? 0xFF24354A : hovered ? 0xFF202C38 : 0xFF151D26;
            drawRect(x, y, x + width, y + height, background);
            drawRect(x, y, x + width, y + 1, 0xFF40566C);
            drawRect(x, y + height - 1, x + width, y + height, 0xFF070B10);
            drawRect(x + width - 18, y + 1, x + width - 17, y + height - 1, 0xFF0A0F15);

            mc.fontRenderer.drawString(displayString, x + 7, y + 6, enabled ? 0xFFFFFF : 0x707070);
            mc.fontRenderer.drawString(open ? "^" : "v", x + width - 12, y + 6, 0xC8CED6);
        }

        private void updateDisplay(String value) {
            this.displayString = optionName(option.name()) + ": " + optionValue(option.name(), value);
        }

        private boolean isMouseOver(int mouseX, int mouseY) {
            int bottom = activeDropdown == this ? dropdownBottom() : y + height;
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < bottom;
        }

        private String valueAt(int mouseX, int mouseY) {
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

        private void opened() {
            scrollOffset = 0;
            int selectedIndex = option.choices().indexOf(valueFor(option));
            if (selectedIndex >= visibleRows()) {
                scrollOffset = selectedIndex - visibleRows() + 1;
            }
            clampScroll();
        }

        private void scroll(int rows) {
            scrollOffset += rows;
            clampScroll();
        }

        private void step(int direction) {
            String value = shiftedChoice(option, valueFor(option), direction);
            setPendingOptionValue(option, value);
            updateDisplay(value);
            if (applyButton != null) {
                applyButton.enabled = true;
            }
        }

        private void clampScroll() {
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
        }

        private int maxScroll() {
            return Math.max(0, option.choices().size() - visibleRows());
        }

        private int visibleRows() {
            int available = Math.max(height, GuiShaderOptions.this.height - (y + height) - 36);
            return Math.max(1, Math.min(option.choices().size(), available / height));
        }

        private int dropdownBottom() {
            return y + height + visibleRows() * height;
        }

        private void drawDropdown(int mouseX, int mouseY) {
            int top = y + height;
            int rows = visibleRows();
            int bottom = top + rows * height;
            drawRect(x, top, x + width, bottom, 0xEE121922);
            drawRect(x, top, x + width, top + 1, 0xFF4B5E73);
            drawRect(x, bottom - 1, x + width, bottom, 0xFF05080C);

            for (int row = 0; row < rows; row++) {
                int i = scrollOffset + row;
                int rowTop = top + row * height;
                boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowTop && mouseY < rowTop + height;
                boolean selected = option.choices().get(i).equals(valueFor(option));
                if (hovered) {
                    drawRect(x + 1, rowTop + 1, x + width - 1, rowTop + height - 1, 0xFF26384A);
                } else if (selected) {
                    drawRect(x + 1, rowTop + 1, x + width - 1, rowTop + height - 1, 0xFF1D2D3C);
                }
                String value = option.choices().get(i);
                fontRenderer.drawString(optionValue(option.name(), value), x + 6, rowTop + 6, selected ? 0xFFFFFF : 0xC8CED6);
            }
            drawScrollbar(x, width, top, bottom, option.choices().size(), rows, scrollOffset);
        }
    }

    private final class GuiShaderProfileDropdown extends GuiButton {
        private int scrollOffset;

        private GuiShaderProfileDropdown(int buttonId, int x, int y, int widthIn, int heightIn) {
            super(buttonId, x, y, widthIn, heightIn, profileLabel());
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) {
                return;
            }

            boolean open = activeProfileDropdown == this;
            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            int background = open ? 0xFF24354A : hovered ? 0xFF202C38 : 0xFF151D26;
            drawRect(x, y, x + width, y + height, background);
            drawRect(x, y, x + width, y + 1, 0xFF40566C);
            drawRect(x, y + height - 1, x + width, y + height, 0xFF070B10);
            drawRect(x + width - 18, y + 1, x + width - 17, y + height - 1, 0xFF0A0F15);

            mc.fontRenderer.drawString(profileLabel(), x + 7, y + 6, enabled ? 0xFFFFFF : 0x707070);
            mc.fontRenderer.drawString(open ? "^" : "v", x + width - 12, y + 6, 0xC8CED6);
        }

        private boolean isMouseOver(int mouseX, int mouseY) {
            int bottom = activeProfileDropdown == this ? dropdownBottom() : y + height;
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < bottom;
        }

        private String valueAt(int mouseX, int mouseY) {
            int top = y + height;
            if (mouseX < x || mouseX >= x + width || mouseY < top || mouseY >= dropdownBottom()) {
                return null;
            }

            List<String> profiles = new ArrayList<>(properties().profiles().keySet());
            int index = scrollOffset + (mouseY - top) / height;
            if (index < 0 || index >= profiles.size()) {
                return null;
            }
            return profiles.get(index);
        }

        private void opened() {
            List<String> profiles = new ArrayList<>(properties().profiles().keySet());
            scrollOffset = 0;
            int selectedIndex = profiles.indexOf(pendingValues.get("<profile>"));
            if (selectedIndex >= visibleRows()) {
                scrollOffset = selectedIndex - visibleRows() + 1;
            }
            clampScroll();
        }

        private void scroll(int rows) {
            scrollOffset += rows;
            clampScroll();
        }

        private void clampScroll() {
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
        }

        private int maxScroll() {
            return Math.max(0, properties().profiles().size() - visibleRows());
        }

        private int visibleRows() {
            int available = Math.max(height, GuiShaderOptions.this.height - (y + height) - 36);
            return Math.max(1, Math.min(properties().profiles().size(), available / height));
        }

        private int dropdownBottom() {
            return y + height + visibleRows() * height;
        }

        private void drawDropdown(int mouseX, int mouseY) {
            List<String> profiles = new ArrayList<>(properties().profiles().keySet());
            int top = y + height;
            int rows = visibleRows();
            int bottom = top + rows * height;
            drawRect(x, top, x + width, bottom, 0xEE121922);
            drawRect(x, top, x + width, top + 1, 0xFF4B5E73);
            drawRect(x, bottom - 1, x + width, bottom, 0xFF05080C);

            String selectedProfile = pendingValues.get("<profile>");
            for (int row = 0; row < rows; row++) {
                int i = scrollOffset + row;
                String profile = profiles.get(i);
                int rowTop = top + row * height;
                boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowTop && mouseY < rowTop + height;
                boolean selected = profile.equals(selectedProfile);
                if (hovered) {
                    drawRect(x + 1, rowTop + 1, x + width - 1, rowTop + height - 1, 0xFF26384A);
                } else if (selected) {
                    drawRect(x + 1, rowTop + 1, x + width - 1, rowTop + height - 1, 0xFF1D2D3C);
                }
                fontRenderer.drawString(profileName(profile), x + 6, rowTop + 6, selected ? 0xFFFFFF : 0xC8CED6);
            }
            drawScrollbar(x, width, top, bottom, profiles.size(), rows, scrollOffset);
        }
    }

    private void drawScrollbar(int left, int dropdownWidth, int top, int bottom, int totalRows, int visibleRows, int scrollOffset) {
        if (totalRows <= visibleRows) {
            return;
        }

        int trackLeft = left + dropdownWidth - 5;
        int trackTop = top + 2;
        int trackBottom = bottom - 2;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(8, trackHeight * visibleRows / totalRows);
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
        drawRect(trackLeft, trackTop, trackLeft + 3, trackBottom, 0xAA06090D);
        drawRect(trackLeft, thumbTop, trackLeft + 3, thumbTop + thumbHeight, 0xFF6D849B);
    }
}
