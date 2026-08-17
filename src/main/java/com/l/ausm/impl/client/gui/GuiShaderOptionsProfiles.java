package com.l.ausm.impl.client.gui;

import com.l.ausm.api.pipeline.pack.ShaderOption;
import com.l.ausm.api.pipeline.pack.ShaderOptions;
import com.l.ausm.api.pipeline.pack.ShaderScreen;
import com.l.ausm.api.pipeline.pack.ShaderScreenEntry;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;

abstract class GuiShaderOptionsProfiles extends GuiShaderOptionsLayout {
    protected boolean isDirty() {
        return !self().comparableOptions(pendingValues).equals(self().comparableOptions(savedValues));
    }

    protected Map<String, String> comparableOptions(Map<String, String> values) {
        Map<String, String> comparable = new LinkedHashMap<>(values);
        comparable.remove("<profile>");
        return comparable;
    }

    protected String profileLabel() {
        String selected = pendingValues.getOrDefault("<profile>", CUSTOM_PROFILE);
        return "Profile: " + self().profileName(selected);
    }

    protected void applyNextProfile(ShaderProperties properties) {
        self().applyProfile(properties, 1);
    }

    protected void applyPreviousProfile(ShaderProperties properties) {
        self().applyProfile(properties, -1);
    }

    protected void applyProfile(ShaderProperties properties, int direction) {
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
        self().applyProfileValues(properties, next);
    }

    protected void applyProfile(ShaderProperties properties, String profile) {
        if (!properties.profiles().containsKey(profile)) {
            return;
        }
        self().applyProfileValues(properties, profile);
    }

    protected void applyProfileValues(ShaderProperties properties, String profile) {
        properties.options().all().keySet().forEach(pendingValues::remove);
        pendingValues.putAll(properties.profileOverrides(profile));
        pendingValues.put("<profile>", profile);
        self().syncProfileWithCurrentValues(properties);
    }

    protected String resetProfile(ShaderProperties properties) {
        String selected = pendingValues.get("<profile>");
        if (selected != null && !CUSTOM_PROFILE.equals(selected) && properties.profiles().containsKey(selected)) {
            return selected;
        }

        String saved = MainMod.getShaderPackManager().getOptionOverrides(packName).get("<profile>");
        if (saved != null && !CUSTOM_PROFILE.equals(saved) && properties.profiles().containsKey(saved)) {
            return saved;
        }

        String matchingDefault = self().matchingProfile(properties, Map.of());
        if (matchingDefault != null) {
            return matchingDefault;
        }

        return properties.profiles().keySet().stream().findFirst().orElse(null);
    }

    protected void syncProfileWithCurrentValues(ShaderProperties properties) {
        String profile = self().matchingProfile(properties);
        pendingValues.put("<profile>", profile == null ? CUSTOM_PROFILE : profile);
        if (profileButton != null) {
            MinecraftReflectionCompat.setGuiButtonText(profileButton, self().profileLabel());
        }
    }

    protected void syncProfileWithCurrentValuesIfNeeded(ShaderProperties properties) {
        String selected = pendingValues.get("<profile>");
        if (selected == null || CUSTOM_PROFILE.equals(selected) || !properties.profiles().containsKey(selected)) {
            self().syncProfileWithCurrentValues(properties);
        }
    }

    protected String matchingProfile(ShaderProperties properties) {
        return self().matchingProfile(properties, pendingValues);
    }

    protected String matchingProfile(ShaderProperties properties, Map<String, String> values) {
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
            if (self().profileMatches(options, values, profileValues)) {
                return profile;
            }
        }
        return null;
    }

    protected boolean profileMatches(ShaderOptions options, Map<String, String> values, Map<String, String> profileValues) {
        for (ShaderOption option : options.all().values()) {
            String current = values.getOrDefault(option.name(), option.defaultValue());
            String profileValue = profileValues.getOrDefault(option.name(), option.defaultValue());
            if (!Objects.equals(current, profileValue)) {
                return false;
            }
        }
        return true;
    }

    protected boolean isMouseOver(GuiButton button, int mouseX, int mouseY) {
        return GuiControlHints.isMouseOverButton(button, mouseX, mouseY);
    }

    protected int maxPage() {
        int entriesPerPage = self().entriesPerPage(self().optionGrid());
        return Math.max(0, (visibleEntries.size() - 1) / entriesPerPage);
    }

    protected GuiShaderOptions.OptionGrid optionGrid() {
        int availableWidth = Math.max(100, self().width - self().optionPanelLeft() - 20);
        int rowWidth = Math.min(400, availableWidth);
        int columns = rowWidth >= 300 ? 2 : 1;
        int availableHeight = Math.max(OPTION_ROW_HEIGHT, self().height - OPTION_PANEL_TOP - 64);
        int rows = Math.max(1, availableHeight / OPTION_ROW_HEIGHT);
        return new GuiShaderOptions.OptionGrid(columns, rows);
    }

    protected int entriesPerPage(GuiShaderOptions.OptionGrid grid) {
        return Math.max(1, grid.columns() * grid.rows());
    }

    protected int optionPanelLeft() {
        return sidebarWidth + 16;
    }

    protected int sidebarListTop() {
        return 62;
    }

    protected int sidebarListBottom() {
        return Math.max(self().sidebarListTop() + 20, self().height - 36);
    }

    protected int visibleSidebarRows() {
        return Math.max(1, (self().sidebarListBottom() - self().sidebarListTop()) / 22);
    }

    protected int maxSidebarScroll() {
        return Math.max(0, sidebarItems.size() - self().visibleSidebarRows());
    }

    protected void clampSidebarScroll() {
        sidebarScrollOffset = self().clamp(sidebarScrollOffset, 0, self().maxSidebarScroll());
    }

    protected boolean adjustSidebarScroll(int direction) {
        int previous = sidebarScrollOffset;
        sidebarScrollOffset = self().clamp(sidebarScrollOffset + direction, 0, self().maxSidebarScroll());
        if (sidebarScrollOffset != previous) {
            self().rebuildButtons();
            return true;
        }
        return false;
    }

    protected boolean isMouseOverSidebar(int mouseX, int mouseY) {
        return mouseX >= 8 && mouseX < sidebarWidth + 8 && mouseY >= 34 && mouseY < self().height - 34;
    }

    protected boolean isMouseOverSearchField(int mouseX, int mouseY) {
        if (searchField == null) {
            return false;
        }
        int x = MinecraftReflectionCompat.guiTextFieldX(searchField);
        int y = MinecraftReflectionCompat.guiTextFieldY(searchField);
        return mouseX >= x && mouseY >= y
                && mouseX < x + MinecraftReflectionCompat.guiTextFieldWidth(searchField)
                && mouseY < y + MinecraftReflectionCompat.guiTextFieldHeight(searchField);
    }

    protected int computeSidebarWidth(ShaderProperties properties) {
        int widest = MIN_SIDEBAR_WIDTH;
        List<GuiShaderOptions.SidebarItem> items = sidebarItems.isEmpty() ? self().sidebarItems(properties) : sidebarItems;
        for (GuiShaderOptions.SidebarItem item : items) {
            int width = 28 + item.depth() * 10 + MinecraftReflectionCompat.fontStringWidth(fontRenderer, self().sidebarLabel(properties, item));
            widest = Math.max(widest, width);
        }
        widest = Math.max(widest, 24 + MinecraftReflectionCompat.fontStringWidth(fontRenderer, "Search options"));
        int maxByScreen = Math.max(MIN_SIDEBAR_WIDTH, self().width - 190);
        return Math.clamp(widest, MIN_SIDEBAR_WIDTH, Math.min(MAX_SIDEBAR_WIDTH, maxByScreen));
    }

    protected int sidebarButtonRight(ShaderProperties properties) {
        int right = 12 + 44;
        for (GuiShaderOptions.SidebarItem item : sidebarItems) {
            int width = 18 + MinecraftReflectionCompat.fontStringWidth(fontRenderer, self().sidebarLabel(properties, item));
            right = Math.max(right, 12 + item.depth() * 10 + width);
        }
        return Math.min(sidebarWidth - 12, right);
    }

    protected void navigateToScreen(String screen) {
        if (screen == null || !self().properties().screens().containsKey(screen)) {
            return;
        }
        if (!screen.equals(selectedScreen)) {
            screenHistory.add(selectedScreen);
        }
        selectedScreen = screen;
        page = 0;
        activeDropdown = null;
        activeProfileDropdown = null;
        self().clearSearch();
        self().expandSidebarPathTo(screen);
        sidebarItems = self().sidebarItems(self().properties());
        self().ensureSelectedSidebarVisible();
        self().rebuildButtons();
    }

    protected boolean navigateBack() {
        if (screenHistory.isEmpty()) {
            return false;
        }
        String previous = screenHistory.remove(screenHistory.size() - 1);
        if (!self().properties().screens().containsKey(previous)) {
            return self().navigateBack();
        }
        selectedScreen = previous;
        page = 0;
        activeDropdown = null;
        activeProfileDropdown = null;
        self().expandSidebarPathTo(previous);
        sidebarItems = self().sidebarItems(self().properties());
        self().ensureSelectedSidebarVisible();
        self().rebuildButtons();
        return true;
    }

    protected boolean handleSidebarRightClick(int mouseX, int mouseY) {
        for (GuiButton button : buttonList) {
            int buttonId = MinecraftReflectionCompat.guiButtonId(button);
            if (buttonId < CATEGORY_BASE_ID || buttonId >= OPTION_BASE_ID
                    || !MinecraftReflectionCompat.guiButtonVisible(button) || !self().isMouseOver(button, mouseX, mouseY)) {
                continue;
            }
            int index = buttonId - CATEGORY_BASE_ID;
            if (index < 0 || index >= sidebarItems.size()) {
                return false;
            }
            GuiShaderOptions.SidebarItem item = sidebarItems.get(index);
            if (!self().hasSidebarChildren(self().properties(), item.screen())) {
                return false;
            }
            if (!expandedSidebarScreens.remove(item.screen())) {
                expandedSidebarScreens.add(item.screen());
            }
            self().clampSidebarScroll();
            self().rebuildButtons();
            return true;
        }
        return false;
    }

    protected void clearSearch() {
        if (searchField != null) {
            MinecraftReflectionCompat.setGuiTextFieldText(searchField, "");
            MinecraftReflectionCompat.setGuiTextFieldFocused(searchField, false);
        }
    }

    protected void ensureSelectedSidebarVisible() {
        for (int i = 0; i < sidebarItems.size(); i++) {
            if (!sidebarItems.get(i).screen().equals(selectedScreen)) {
                continue;
            }
            if (i < sidebarScrollOffset) {
                sidebarScrollOffset = i;
            } else if (i >= sidebarScrollOffset + self().visibleSidebarRows()) {
                sidebarScrollOffset = i - self().visibleSidebarRows() + 1;
            }
            self().clampSidebarScroll();
            return;
        }
    }

    protected void expandSidebarPathTo(String targetScreen) {
        List<String> path = self().findScreenPath(self().properties(), targetScreen);
        if (path.isEmpty()) {
            return;
        }
        for (int i = 0; i < path.size() - 1; i++) {
            expandedSidebarScreens.add(path.get(i));
        }
    }

    protected List<String> findScreenPath(ShaderProperties properties, String targetScreen) {
        if (properties.screens().containsKey("screen")) {
            List<String> path = self().findScreenPath(properties, "screen", targetScreen, new HashSet<>());
            if (!path.isEmpty()) {
                return path;
            }
        }
        for (String screen : properties.screens().keySet()) {
            List<String> path = self().findScreenPath(properties, screen, targetScreen, new HashSet<>());
            if (!path.isEmpty()) {
                return path;
            }
        }
        return List.of();
    }

    protected List<String> findScreenPath(ShaderProperties properties, String currentScreen, String targetScreen, Set<String> visited) {
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
            List<String> childPath = self().findScreenPath(properties, entry.name(), targetScreen, visited);
            if (!childPath.isEmpty()) {
                List<String> path = new ArrayList<>();
                path.add(currentScreen);
                path.addAll(childPath);
                return path;
            }
        }
        return List.of();
    }

    protected List<GuiShaderOptions.SidebarItem> sidebarItems(ShaderProperties properties) {
        List<GuiShaderOptions.SidebarItem> items = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        if (properties.screens().containsKey("screen")) {
            items.add(new GuiShaderOptions.SidebarItem("screen", 0));
            visited.add("screen");
            self().addSidebarChildren(properties, "screen", 0, items, visited, true);
            return items;
        }
        for (String screen : properties.screens().keySet()) {
            items.add(new GuiShaderOptions.SidebarItem(screen, 0));
            if (expandedSidebarScreens.contains(screen)) {
                self().addSidebarChildren(properties, screen, 1, items, new HashSet<>(Set.of(screen)), false);
            }
        }
        return items;
    }

    protected void addSidebarChildren(ShaderProperties properties, String screen, int depth, List<GuiShaderOptions.SidebarItem> items, Set<String> visited, boolean rootChildrenAlwaysVisible) {
        ShaderScreen shaderScreen = properties.screens().get(screen);
        if (shaderScreen == null || !rootChildrenAlwaysVisible && !expandedSidebarScreens.contains(screen)) {
            return;
        }
        for (ShaderScreenEntry entry : shaderScreen.entries()) {
            if (entry.type() != ShaderScreenEntry.Type.SCREEN || !properties.screens().containsKey(entry.name()) || visited.contains(entry.name())) {
                continue;
            }
            items.add(new GuiShaderOptions.SidebarItem(entry.name(), depth));
            visited.add(entry.name());
            self().addSidebarChildren(properties, entry.name(), depth + 1, items, visited, false);
            visited.remove(entry.name());
        }
    }

    protected boolean hasSidebarChildren(ShaderProperties properties, String screen) {
        ShaderScreen shaderScreen = properties.screens().get(screen);
        if (shaderScreen == null) {
            return false;
        }
        return shaderScreen.entries().stream()
                .anyMatch(entry -> entry.type() == ShaderScreenEntry.Type.SCREEN && properties.screens().containsKey(entry.name()));
    }

    protected String sidebarLabel(ShaderProperties properties, GuiShaderOptions.SidebarItem item) {
        if (item.screen().equals("screen")) {
            return self().label(item.screen());
        }
        String prefix = self().hasSidebarChildren(properties, item.screen())
                ? (expandedSidebarScreens.contains(item.screen()) ? "- " : "+ ")
                : "  ";
        return prefix + self().label(item.screen());
    }

    protected String label(String id) {
        if (id.equals("screen")) {
            return "Main";
        }
        return self().properties().translate("screen." + id, id.replace('_', ' '));
    }

    protected String optionName(String id) {
        return self().properties().translate("option." + id, id.replace('_', ' '));
    }

    protected String optionValue(String option, String value) {
        return self().properties().translate("value." + option + "." + value, value);
    }

    protected String profileName(String profile) {
        if (CUSTOM_PROFILE.equals(profile)) {
            return CUSTOM_PROFILE;
        }
        return self().properties().translate("profile." + profile, profile);
    }

    @Override
    protected void ausm$drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hoveredCommentTitle = List.of();
        hoveredCommentBody = List.of();
        if (previewHidden) {
            super.ausm$drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        drawRect(0, 0, self().width, self().height, 0x330B1016);
        drawRect(8, 34, sidebarWidth + 8, self().height - 34, 0x66101418);
        drawRect(self().optionPanelLeft() - 4, 34, self().width - 8, self().height - 34, 0x66101418);
        self().drawCenteredString(self().fontRenderer, "Shader Options - " + self().displayPackName(), self().width / 2, 16, 0xFFFFFF);
        String pageText = (page + 1) + " / " + (self().maxPage() + 1);
        self().drawCenteredString(self().fontRenderer, pageText, self().width / 2, self().height - 22, 0xA0A0A0);
        if (searchField != null) {
            MinecraftReflectionCompat.drawGuiTextField(searchField);
            if (!MinecraftReflectionCompat.guiTextFieldFocused(searchField)
                    && MinecraftReflectionCompat.guiTextFieldText(searchField).isEmpty()) {
                MinecraftReflectionCompat.fontDrawString(fontRenderer, "Search options",
                        MinecraftReflectionCompat.guiTextFieldX(searchField) + 4,
                        MinecraftReflectionCompat.guiTextFieldY(searchField) + 5, 0xFF6F7E8D);
            }
        }
        self().drawSidebarScrollbar();
        boolean dropdownOpen = activeDropdown != null || activeProfileDropdown != null;
        super.ausm$drawScreen(dropdownOpen ? -1 : mouseX, dropdownOpen ? -1 : mouseY, partialTicks);
        self().drawFocusedButtonOutline();
        if (activeDropdown != null) {
            activeDropdown.drawDropdown(mouseX, mouseY);
        }
        if (activeProfileDropdown != null) {
            activeProfileDropdown.drawDropdown(mouseX, mouseY);
        }
        self().drawShaderTooltip(mouseX, mouseY);
        self().drawBottomCommentPanel();
        self().drawEscapeHintTooltip(mouseX, mouseY);
    }

    protected void drawSidebarScrollbar() {
        int maxScroll = self().maxSidebarScroll();
        if (maxScroll <= 0) {
            return;
        }

        int trackLeft = sidebarWidth;
        int trackRight = sidebarWidth + 4;
        int trackTop = self().sidebarListTop();
        int trackBottom = self().sidebarListBottom() - 2;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(12, trackHeight * self().visibleSidebarRows() / Math.max(self().visibleSidebarRows(), sidebarItems.size()));
        int thumbY = trackTop + (trackHeight - thumbHeight) * sidebarScrollOffset / maxScroll;
        drawRect(trackLeft, trackTop, trackRight, trackBottom, 0x55182026);
        drawRect(trackLeft, thumbY, trackRight, thumbY + thumbHeight, 0xAA6E8197);
    }

    protected void drawShaderTooltip(int mouseX, int mouseY) {
        if (activeDropdown != null) {
            return;
        }
        if (activeProfileDropdown != null) {
            return;
        }

        for (GuiButton button : buttonList) {
            if (!MinecraftReflectionCompat.guiButtonVisible(button) || !self().isMouseOver(button, mouseX, mouseY)) {
                continue;
            }

            List<String> tooltip = self().tooltipFor(button);
            if (!tooltip.isEmpty()) {
                self().setHoveredComment(button, tooltip);
            }
            return;
        }
    }

    protected void setHoveredComment(GuiButton button, List<String> tooltip) {
        if (button instanceof GuiShaderOptions.GuiShaderOptionSlider) {
            GuiShaderOptions.GuiShaderOptionSlider slider = (GuiShaderOptions.GuiShaderOptionSlider) button;
            hoveredCommentTitle = List.of(self().optionName(slider.option.name()));
        } else if (button instanceof GuiShaderOptions.GuiShaderOptionDropdown) {
            GuiShaderOptions.GuiShaderOptionDropdown dropdown = (GuiShaderOptions.GuiShaderOptionDropdown) button;
            hoveredCommentTitle = List.of(self().optionName(dropdown.option.name()));
        } else if (button instanceof GuiShaderOptions.GuiShaderProfileDropdown) {
            hoveredCommentTitle = List.of("Profile");
        } else if (MinecraftReflectionCompat.guiButtonId(button) >= OPTION_BASE_ID) {
            int index = MinecraftReflectionCompat.guiButtonId(button) - OPTION_BASE_ID;
            if (index >= 0 && index < visibleEntries.size()) {
                hoveredCommentTitle = List.of(self().label(visibleEntries.get(index).name()));
            }
        } else {
            hoveredCommentTitle = List.of(MinecraftReflectionCompat.guiButtonText(button));
        }
        hoveredCommentBody = tooltip;
    }

    protected void drawBottomCommentPanel() {
        if (hoveredCommentBody.isEmpty()) {
            return;
        }

        int panelWidth = Math.min(314, self().width - 24);
        int panelHeight = Math.max(50, 18 + hoveredCommentBody.size() * 10);
        int x = (self().width - panelWidth) / 2;
        int y = self().height - panelHeight - 36;

        drawRect(x, y, x + panelWidth, y + panelHeight, 0xDD101820);
        drawRect(x, y, x + panelWidth, y + 1, 0xFF42566D);
        drawRect(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xFF05080C);

        int titleColor = 0xFFFFFFFF;
        if (!hoveredCommentTitle.isEmpty()) {
            MinecraftReflectionCompat.fontDrawString(self().fontRenderer, hoveredCommentTitle.get(0), x + 5, y + 5, titleColor);
        }
        for (int i = 0; i < hoveredCommentBody.size(); i++) {
            MinecraftReflectionCompat.fontDrawString(self().fontRenderer, hoveredCommentBody.get(i), x + 5, y + 17 + i * 10, 0xFFD8DEE8);
        }
    }

    protected boolean handleOpenDropdownKey(int keyCode) {
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
                self().applyProfile(self().properties(), -1);
                self().rebuildButtons();
                activeProfileDropdown = null;
                return true;
            }
            if (keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_RIGHT) {
                self().applyProfile(self().properties(), 1);
                self().rebuildButtons();
                activeProfileDropdown = null;
                return true;
            }
            return true;
        }
        return false;
    }

    protected boolean handleKeyboardNavigation(int keyCode) throws IOException {
        self().ensureFocusedControl();
        if (keyCode == Keyboard.KEY_TAB) {
            self().moveFocusLinear(GuiControlHints.isShiftDown() ? -1 : 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_UP) {
            self().moveFocusSpatial(0, -1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            self().moveFocusSpatial(0, 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            GuiButton button = self().focusedButton();
            if (button instanceof GuiShaderOptions.GuiShaderOptionSlider) {
                GuiShaderOptions.GuiShaderOptionSlider slider = (GuiShaderOptions.GuiShaderOptionSlider) button;
                slider.step(-1);
            } else if (button instanceof GuiShaderOptions.GuiShaderOptionDropdown) {
                GuiShaderOptions.GuiShaderOptionDropdown dropdown = (GuiShaderOptions.GuiShaderOptionDropdown) button;
                dropdown.step(-1);
            } else if (button instanceof GuiShaderOptions.GuiShaderProfileDropdown) {
                self().applyProfile(self().properties(), -1);
                self().rebuildButtons();
            } else {
                self().moveFocusSpatial(-1, 0);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            GuiButton button = self().focusedButton();
            if (button instanceof GuiShaderOptions.GuiShaderOptionSlider) {
                GuiShaderOptions.GuiShaderOptionSlider slider = (GuiShaderOptions.GuiShaderOptionSlider) button;
                slider.step(1);
            } else if (button instanceof GuiShaderOptions.GuiShaderOptionDropdown) {
                GuiShaderOptions.GuiShaderOptionDropdown dropdown = (GuiShaderOptions.GuiShaderOptionDropdown) button;
                dropdown.step(1);
            } else if (button instanceof GuiShaderOptions.GuiShaderProfileDropdown) {
                self().applyProfile(self().properties(), 1);
                self().rebuildButtons();
            } else {
                self().moveFocusSpatial(1, 0);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_SPACE) {
            GuiButton button = self().focusedButton();
            if (button != null) {
                self().ausm$actionPerformed(button);
            }
            return true;
        }
        return false;
    }

    protected void moveFocusLinear(int direction) {
        List<GuiButton> focusables = self().focusableButtons();
        if (focusables.isEmpty()) {
            focusedControl = -1;
            return;
        }
        GuiButton current = self().focusedButton();
        int currentIndex = current == null ? -1 : focusables.indexOf(current);
        int next = Math.floorMod(currentIndex + direction, focusables.size());
        focusedControl = buttonList.indexOf(focusables.get(next));
    }

    protected void moveFocusSpatial(int dx, int dy) {
        List<GuiButton> focusables = self().focusableButtons();
        if (focusables.isEmpty()) {
            focusedControl = -1;
            return;
        }

        GuiButton current = self().focusedButton();
        if (current == null) {
            focusedControl = buttonList.indexOf(focusables.get(0));
            return;
        }

        int currentX = MinecraftReflectionCompat.guiButtonX(current)
                + MinecraftReflectionCompat.guiButtonWidth(current) / 2;
        int currentY = MinecraftReflectionCompat.guiButtonY(current)
                + MinecraftReflectionCompat.guiButtonHeight(current) / 2;
        GuiButton best = null;
        int bestScore = Integer.MAX_VALUE;
        for (GuiButton candidate : focusables) {
            if (candidate == current) {
                continue;
            }
            int candidateX = MinecraftReflectionCompat.guiButtonX(candidate)
                    + MinecraftReflectionCompat.guiButtonWidth(candidate) / 2;
            int candidateY = MinecraftReflectionCompat.guiButtonY(candidate)
                    + MinecraftReflectionCompat.guiButtonHeight(candidate) / 2;
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

    protected void updateFocusFromMouse(int mouseX, int mouseY) {
        for (int i = 0; i < buttonList.size(); i++) {
            GuiButton button = buttonList.get(i);
            if (self().isFocusable(button) && self().isMouseOver(button, mouseX, mouseY)) {
                focusedControl = i;
                return;
            }
        }
    }

    protected void ensureFocusedControl() {
        if (previewHidden) {
            focusedControl = self().indexOfButton(ID_PREVIEW);
            return;
        }
        if (focusedControl >= buttonList.size() || focusedControl >= 0 && !self().isFocusable(buttonList.get(focusedControl))) {
            List<GuiButton> focusables = self().focusableButtons();
            focusedControl = focusables.isEmpty() ? -1 : buttonList.indexOf(focusables.get(0));
        }
    }

    protected GuiButton focusedButton() {
        if (focusedControl >= 0 && focusedControl < buttonList.size()) {
            GuiButton button = buttonList.get(focusedControl);
            return self().isFocusable(button) ? button : null;
        }
        return null;
    }

    protected List<GuiButton> focusableButtons() {
        return buttonList.stream().filter(self()::isFocusable).toList();
    }

    protected boolean isFocusable(GuiButton button) {
        return GuiControlHints.isFocusable(button);
    }

    protected int indexOfButton(int id) {
        for (int i = 0; i < buttonList.size(); i++) {
            if (MinecraftReflectionCompat.guiButtonId(buttonList.get(i)) == id) {
                return i;
            }
        }
        return -1;
    }

    protected void drawFocusedButtonOutline() {
        GuiControlHints.drawFocusedButtonOutline(self().focusedButton());
    }

    protected void drawEscapeHintTooltip(int mouseX, int mouseY) {
        GuiControlHints.drawEscapeHintLabel(self().fontRenderer, self().width);
        if (GuiControlHints.isMouseOverEscapeHint(self().fontRenderer, self().width, mouseX, mouseY)) {
            drawHoveringText(GuiControlHints.escapeTooltip(), mouseX, mouseY);
        }
    }
}
