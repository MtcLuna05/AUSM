package com.luna.ausm.impl.client.gui;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.pack.ShaderPackManager;
import com.luna.ausm.impl.pipeline.pack.ShaderProperties;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public class GuiShaders extends MappingSafeGuiScreen {
    private static final int ID_DONE = 200;
    private static final int ID_APPLY = 201;
    private static final int ID_REFRESH = 202;
    private static final int ID_OPTIONS = 203;
    private static final int ID_TOGGLE_ENABLED = 204;
    private static final int ID_PREVIEW = 205;
    private static final int ID_OPEN_FOLDER = 206;
    private static final int ID_CANCEL = 207;
    private static final int ID_SETTINGS = 208;

    private final GuiScreen parentScreen;
    private GuiSlotShaders shaderList;

    private GuiButton applyButton;
    private GuiButton refreshButton;
    private GuiButton optionsButton;
    private GuiButton toggleEnabledButton;
    private GuiButton settingsButton;
    private GuiButton previewButton;
    private GuiButton openFolderButton;
    private boolean previewHidden;
    private String notificationText;
    private int notificationTicks;
    private GLFWDropCallback dropCallback;
    private GLFWDropCallback previousDropCallback;
    private int focusedControl = -1;
    private int lastMouseX;
    private int lastMouseY;
    private ShaderProperties selectedProperties;
    private String selectedPropertiesPack;
    private int leftPanelRight;
    private int detailsScrollOffset;
    private boolean pendingShadersEnabled;

    public GuiShaders(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    protected boolean ausm$doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void ausm$initGui() {
        installDropCallback();
        this.buttonList.clear();
        this.pendingShadersEnabled = MainMod.getShaderPackManager().areShadersEnabled();
        this.leftPanelRight = computeLeftPanelRight();
        this.shaderList = new GuiSlotShaders(this, this.mc, leftPanelRight, 80, contentBottom(), 24);
        this.detailsScrollOffset = Math.clamp(this.detailsScrollOffset, 0, maxDetailsScroll());

        int bottom = this.height - 28;
        int bottomButtonWidth = 100;
        int bottomGap = 4;
        int bottomStart = (this.width - bottomButtonWidth * 3 - bottomGap * 2) / 2;
        this.buttonList.add(new GuiFlatButton(ID_CANCEL, bottomStart, bottom, bottomButtonWidth, 20, "Cancel"));

        this.applyButton = new GuiFlatButton(ID_APPLY, bottomStart + bottomButtonWidth + bottomGap, bottom, bottomButtonWidth, 20, "Apply");
        MinecraftReflectionCompat.setGuiButtonEnabled(this.applyButton, false);
        this.buttonList.add(this.applyButton);

        this.buttonList.add(new GuiFlatButton(ID_DONE, bottomStart + (bottomButtonWidth + bottomGap) * 2,
                bottom, bottomButtonWidth, 20, MinecraftReflectionCompat.i18nFormat("gui.done")));

        int utilityY = this.height - 51;
        int bottomCenter = this.width / 2;
        this.openFolderButton = new GuiFlatButton(ID_OPEN_FOLDER, bottomCenter - 156, utilityY, 152, 20, "Open Shader Pack Folder...");
        this.buttonList.add(this.openFolderButton);

        this.optionsButton = new GuiFlatButton(ID_OPTIONS, bottomCenter + 4, utilityY, 152, 20, "Shader Pack Settings...");
        MinecraftReflectionCompat.setGuiButtonEnabled(this.optionsButton, canConfigure(shaderList.getSelectedPackName()));
        this.buttonList.add(this.optionsButton);

        this.toggleEnabledButton = new GuiFlatButton(ID_TOGGLE_ENABLED, 0, 0, 0, 0, "");
        MinecraftReflectionCompat.setGuiButtonEnabled(this.toggleEnabledButton,
                canConfigure(MainMod.getShaderPackManager().getSelectedPackName()));

        int toolbarY = 53;
        int toolbarGap = 3;
        int settingsWidth = 90;
        int refreshWidth = 72;
        int previewWidth = 72;
        int toolbarX = leftPanelRight - settingsWidth - refreshWidth - previewWidth - toolbarGap * 2 - 12;
        this.settingsButton = new GuiFlatButton(ID_SETTINGS, toolbarX, toolbarY, settingsWidth, 20, settingsLabel());
        this.buttonList.add(this.settingsButton);

        this.refreshButton = new GuiFlatButton(ID_REFRESH, toolbarX + settingsWidth + toolbarGap, toolbarY, refreshWidth, 20, "Refresh");
        this.buttonList.add(this.refreshButton);

        this.previewButton = new GuiFlatButton(ID_PREVIEW,
                toolbarX + settingsWidth + refreshWidth + toolbarGap * 2, toolbarY, previewWidth, 20, "Preview");
        this.buttonList.add(this.previewButton);
        updateSelectedProperties();
        updateSettingsButton();
        updatePreviewVisibility();
        ensureFocusedControl();
    }

    @Override
    protected void ausm$onGuiClosed() {
        restoreDropCallback();
        super.ausm$onGuiClosed();
    }

    @Override
    protected void ausm$updateScreen() {
        super.ausm$updateScreen();
        if (notificationTicks > 0) {
            notificationTicks--;
        }
    }

    @Override
    protected void ausm$handleMouseInput() throws IOException {
        super.ausm$handleMouseInput();
        if (previewHidden) {
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && isMouseOverDetails(currentMouseX(), currentMouseY())) {
            this.detailsScrollOffset = Math.clamp(this.detailsScrollOffset - Integer.compare(wheel, 0),
                    0, maxDetailsScroll());
            return;
        }
        this.shaderList.handleMouseInput(wheel);
    }

    @Override
    protected void ausm$actionPerformed(GuiButton button) throws IOException {
        if (!MinecraftReflectionCompat.guiButtonEnabled(button)) {
            return;
        }

        switch (MinecraftReflectionCompat.guiButtonId(button)) {
            case ID_DONE:
                if (this.parentScreen == null) {
                    MinecraftReflectionCompat.setField(this.mc, null, "field_71462_r", "currentScreen");
                    MinecraftReflectionCompat.invoke(this.mc, new String[]{"func_71381_h", "setIngameFocus"}, MinecraftReflectionCompat.NO_PARAMETERS);
                    return;
                }
                MinecraftReflectionCompat.displayGuiScreen(this.mc, this.parentScreen);
                break;
            case ID_APPLY:
                applySelectedPack();
                break;
            case ID_REFRESH:
                refreshShaderList();
                break;
            case ID_OPTIONS:
                openSelectedPackOptions();
                break;
            case ID_TOGGLE_ENABLED:
                toggleShadersEnabledPending();
                break;
            case ID_SETTINGS:
                GuiScreen settingsScreen = new GuiDynamicLights(this);
                try {
                    settingsScreen = (GuiScreen) Class.forName("com.luna.ausm.impl.client.gui.GuiAusmSettings")
                            .getConstructor(GuiScreen.class)
                            .newInstance(this);
                } catch (ReflectiveOperationException ignored) {
                    // The live client can safely keep its legacy settings screen until the next restart.
                }
                MinecraftReflectionCompat.displayGuiScreen(this.mc, settingsScreen);
                break;
            case ID_PREVIEW:
                setPreviewHidden(!previewHidden);
                break;
            case ID_OPEN_FOLDER:
                openShaderpacksFolder();
                break;
            case ID_CANCEL:
                MinecraftReflectionCompat.displayGuiScreen(this.mc, this.parentScreen);
                break;
            default:
                break;
        }
    }

    private void openShaderpacksFolder() {
        try {
            Path folder = MainMod.getShaderPackManager().getShaderpacksDir();
            Files.createDirectories(folder);
            if (!Desktop.isDesktopSupported()) {
                MainMod.LOGGER.warn("Cannot open shaderpacks folder because desktop integration is unavailable: {}", folder.toAbsolutePath());
                return;
            }
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("Failed to open shaderpacks folder", e);
        }
    }

    private void setPreviewHidden(boolean previewHidden) {
        this.previewHidden = previewHidden;
        updatePreviewVisibility();
    }

    private void updatePreviewVisibility() {
        for (GuiButton guiButton : this.buttonList) {
            MinecraftReflectionCompat.setGuiButtonVisible(guiButton,
                    !previewHidden || MinecraftReflectionCompat.guiButtonId(guiButton) == ID_PREVIEW);
        }
        if (previewButton != null) {
            MinecraftReflectionCompat.setGuiButtonText(previewButton, previewHidden ? "Show GUI" : "Preview");
        }
    }

    public void onSelectionChanged() {
        if (this.applyButton != null) {
            String selectedPack = this.shaderList.getSelectedPackName();
            String currentPack = MainMod.getShaderPackManager().getSelectedPackName();
            if (currentPack == null || currentPack.equals("(internal)")) currentPack = "OFF";

            MinecraftReflectionCompat.setGuiButtonEnabled(this.applyButton,
                    selectedPack != null && (!selectedPack.equals(currentPack)
                            || pendingShadersEnabled != MainMod.getShaderPackManager().areShadersEnabled()));
        }
        if (this.optionsButton != null) {
            MinecraftReflectionCompat.setGuiButtonEnabled(this.optionsButton,
                    canConfigure(shaderList.getSelectedPackName()));
        }
        updateEnabledButton();
        updateSelectedProperties();
    }

    public void applySelectedPack() {
        String selectedPack = this.shaderList.getSelectedPackName();
        if (selectedPack == null) {
            return;
        }

        if (selectedPack.equalsIgnoreCase("OFF")) {
            MainMod.getShaderPackManager().setShadersEnabled(pendingShadersEnabled);
            if (this.applyButton != null) {
                MinecraftReflectionCompat.setGuiButtonEnabled(this.applyButton, false);
            }
            updateEnabledButton();
            updateSelectedProperties();
            return;
        }

        MainMod.getShaderPackManager().loadPack(selectedPack);
        if (pendingShadersEnabled != MainMod.getShaderPackManager().areShadersEnabled()) {
            MainMod.getShaderPackManager().setShadersEnabled(pendingShadersEnabled);
        }
        if (this.applyButton != null) {
            MinecraftReflectionCompat.setGuiButtonEnabled(this.applyButton, false);
        }
        if (this.optionsButton != null) {
            MinecraftReflectionCompat.setGuiButtonEnabled(this.optionsButton, canConfigure(selectedPack));
        }
        updateEnabledButton();
        updateSelectedProperties();
    }

    public void openSelectedPackOptions() {
        openPackOptions(shaderList.getSelectedPackName());
    }

    private void openPackOptions(String packName) {
        if (!canConfigure(packName)) {
            return;
        }
        MinecraftReflectionCompat.displayGuiScreen(this.mc, new GuiShaderOptions(this, packName));
    }

    @Override
    protected void ausm$drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (previewHidden) {
            super.ausm$drawScreen(mouseX, mouseY, partialTicks);
            drawFocusedButtonOutline();
            return;
        }

        drawPanels();
        this.shaderList.drawScreen(mouseX, mouseY, partialTicks, focusedControl == -1);
        drawHeader();
        drawPackDetails();
        drawNotification();

        super.ausm$drawScreen(mouseX, mouseY, partialTicks);
        drawFocusedButtonOutline();
        drawEscapeHintTooltip(mouseX, mouseY);
    }

    @Override
    protected void ausm$mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (previewHidden) {
            super.ausm$mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        this.shaderList.mouseClicked(mouseX, mouseY, mouseButton);
        super.ausm$mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void ausm$keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (GuiControlHints.isShiftDown()) {
                MinecraftReflectionCompat.displayGuiScreen(this.mc, null);
                return;
            }
            if (previewHidden) {
                setPreviewHidden(false);
                return;
            }
            MinecraftReflectionCompat.displayGuiScreen(this.mc, parentScreen);
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            String hoveredPack = shaderList != null ? shaderList.getPackNameAt(currentMouseX(), currentMouseY()) : null;
            openPackOptions(hoveredPack != null ? hoveredPack : shaderList.getSelectedPackName());
            return;
        }
        if (handleKeyboardNavigation(keyCode)) {
            return;
        }
        super.ausm$keyTyped(typedChar, keyCode);
    }

    private boolean handleKeyboardNavigation(int keyCode) throws IOException {
        ensureFocusedControl();
        if (keyCode == Keyboard.KEY_UP) {
            if (focusedControl == -1) {
                shaderList.moveSelection(-1);
            } else {
                moveFocusVertical(-1);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            if (focusedControl == -1) {
                shaderList.moveSelection(1);
            } else {
                moveFocusVertical(1);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            moveFocusHorizontal(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            moveFocusHorizontal(1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_SPACE) {
            if (focusedControl == -1) {
                applySelectedPack();
                return true;
            }
            GuiButton focused = focusedButton();
            if (focused != null) {
                ausm$actionPerformed(focused);
            }
            return true;
        }
        return false;
    }

    private int currentMouseX() {
        return Mouse.getX() * this.width / MinecraftReflectionCompat.displayWidth(this.mc);
    }

    private int currentMouseY() {
        return this.height - Mouse.getY() * this.height / MinecraftReflectionCompat.displayHeight(this.mc) - 1;
    }

    private void moveFocusHorizontal(int direction) {
        if (previewHidden) {
            focusedControl = indexOfButton(ID_PREVIEW);
            return;
        }
        List<GuiButton> focusables = focusableButtons();
        if (focusedControl == -1) {
            focusedControl = direction > 0 ? firstButtonIndex(focusables) : lastButtonIndex(focusables);
            return;
        }
        int listIndex = focusables.indexOf(focusedButton());
        if (listIndex < 0) {
            focusedControl = -1;
            return;
        }
        int next = listIndex + direction;
        focusedControl = next < 0 || next >= focusables.size() ? -1 : this.buttonList.indexOf(focusables.get(next));
    }

    private void moveFocusVertical(int direction) {
        if (previewHidden) {
            focusedControl = indexOfButton(ID_PREVIEW);
            return;
        }
        focusedControl = -1;
        if (direction > 0) {
            shaderList.moveSelection(1);
        } else {
            shaderList.moveSelection(-1);
        }
    }

    private void ensureFocusedControl() {
        if (previewHidden) {
            focusedControl = indexOfButton(ID_PREVIEW);
            return;
        }
        if (focusedControl >= this.buttonList.size()
                || (focusedControl >= 0 && !isFocusable(this.buttonList.get(focusedControl)))) {
            focusedControl = -1;
        }
    }

    private GuiButton focusedButton() {
        if (focusedControl >= 0 && focusedControl < this.buttonList.size()) {
            GuiButton button = this.buttonList.get(focusedControl);
            return isFocusable(button) ? button : null;
        }
        return null;
    }

    private List<GuiButton> focusableButtons() {
        return this.buttonList.stream().filter(this::isFocusable).toList();
    }

    private boolean isFocusable(GuiButton button) {
        return GuiControlHints.isFocusable(button);
    }

    private int firstButtonIndex(List<GuiButton> buttons) {
        return buttons.isEmpty() ? -1 : this.buttonList.indexOf(buttons.get(0));
    }

    private int lastButtonIndex(List<GuiButton> buttons) {
        return buttons.isEmpty() ? -1 : this.buttonList.indexOf(buttons.get(buttons.size() - 1));
    }

    private int indexOfButton(int id) {
        for (int i = 0; i < this.buttonList.size(); i++) {
            if (MinecraftReflectionCompat.guiButtonId(this.buttonList.get(i)) == id) {
                return i;
            }
        }
        return -1;
    }

    private void drawFocusedButtonOutline() {
        GuiControlHints.drawFocusedButtonOutline(focusedButton());
    }

    private void drawHeader() {
        this.drawString(this.fontRenderer, "Shaders", 16, 16, 0xFFFFFF);
        this.drawString(this.fontRenderer, "Select a shader pack and configure its options.", 16, 29, 0x9DA7B3);
    }

    private void drawPanels() {
        int topBandBottom = 46;
        int bottomBandTop = Math.max(topBandBottom, this.height - 62);
        int detailsLeft = detailsLeft();
        this.mc.getTextureManager().bindTexture(Gui.OPTIONS_BACKGROUND);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(0, topBandBottom, 0).tex(0, topBandBottom / 32.0F).color(64, 64, 64, 255).endVertex();
        buffer.pos(this.width, topBandBottom, 0).tex(this.width / 32.0F, topBandBottom / 32.0F).color(64, 64, 64, 255).endVertex();
        buffer.pos(this.width, 0, 0).tex(this.width / 32.0F, 0).color(64, 64, 64, 255).endVertex();
        buffer.pos(0, 0, 0).tex(0, 0).color(64, 64, 64, 255).endVertex();
        tessellator.draw();
        drawRect(0, topBandBottom, this.width, topBandBottom + 2, 0x66000000);
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(0, this.height, 0).tex(0, this.height / 32.0F).color(64, 64, 64, 255).endVertex();
        buffer.pos(this.width, this.height, 0).tex(this.width / 32.0F, this.height / 32.0F).color(64, 64, 64, 255).endVertex();
        buffer.pos(this.width, bottomBandTop, 0).tex(this.width / 32.0F, bottomBandTop / 32.0F).color(64, 64, 64, 255).endVertex();
        buffer.pos(0, bottomBandTop, 0).tex(0, bottomBandTop / 32.0F).color(64, 64, 64, 255).endVertex();
        tessellator.draw();
        drawRect(0, bottomBandTop - 2, this.width, bottomBandTop, 0x66000000);
        // Keep the panel clear of the bottom bar's upward shadow just as it starts below the top shadow.
        int bottom = bottomBandTop - 4;
        drawRect(10, 50, leftPanelRight, bottom, 0x660E0E0E);
        drawRect(detailsLeft, 50, this.width - 10, bottom, 0x660E0E0E);
        drawRect(10, 50, leftPanelRight, 76, 0x88181818);
        drawRect(detailsLeft, 50, this.width - 10, 76, 0x88181818);
        this.drawString(this.fontRenderer, "Packs", 20, 59, 0xFFD6D6D6);
        this.drawString(this.fontRenderer, "Details", detailsLeft + 12, 59, 0xFFD6D6D6);
    }

    private void drawPackDetails() {
        ShaderPackManager manager = MainMod.getShaderPackManager();
        String currentPack = manager.areShadersEnabled() ? manager.getCurrentPack().getName() : "OFF";
        if (currentPack == null || currentPack.equals("(internal)")) {
            currentPack = "OFF";
        }
        String selectedPack = shaderList.getSelectedPackName();
        if (selectedPack == null) {
            selectedPack = "None";
        }

        int x = detailsLeft() + 12;
        int y = 86 - this.detailsScrollOffset * 34;
        int contentBottom = contentBottom();
        y = drawDetailPair(x, y, contentBottom, "Selected", selectedPack, 0xFFFFFF);
        y = drawDetailPair(x, y, contentBottom, "Active", currentPack, 0xFFFFFF);
        y = drawDetailPair(x, y, contentBottom, "Available packs", Integer.toString(shaderList.getPackCount()), 0xFFFFFF);
        ShaderProperties properties = selectedProperties != null ? selectedProperties : PipelineContext.getInstance().getShaderProperties();
        y = drawDetailPair(x, y, contentBottom, "Current options", Integer.toString(properties.options().all().size()), 0xFFFFFF);
        y = drawDetailPair(x, y, contentBottom, "Enabled", MainMod.getShaderPackManager().areShadersEnabled() ? "Yes" : "No",
                MainMod.getShaderPackManager().areShadersEnabled() ? 0xB4F28A : 0xFF8A8A);
        y = drawDetailPair(x, y, contentBottom, "Dynamic lights", dynamicLightsStatus(),
                dynamicLightsEnabled() ? 0xB4F28A : dynamicLightsAvailable() ? 0xFF8A8A : 0xFFD27D);
        if (BetterPortalsCompat.isInstalled()) {
            drawDetailPair(x, y, contentBottom, "Portal shaders", portalShadersStatus(),
                    portalShadersEffective() ? 0xB4F28A : portalShadersConfigured() ? 0xFFD27D : 0xFF8A8A);
        }
        drawDetailsScrollbar(contentBottom);
    }

    private void drawNotification() {
        if (notificationTicks <= 0 || notificationText == null || notificationText.isEmpty()) {
            return;
        }

        int textWidth = MinecraftReflectionCompat.fontStringWidth(this.fontRenderer, notificationText);
        int x = (this.width - textWidth) / 2;
        int y = this.height - 76;
        drawRect(x - 8, y - 6, x + textWidth + 8, y + 14, 0xAA101418);
        this.drawString(this.fontRenderer, notificationText, x, y, 0xFFE7C86E);
    }

    private void refreshShaderList() {
        this.leftPanelRight = computeLeftPanelRight();
        this.shaderList = new GuiSlotShaders(this, this.mc, leftPanelRight, 80, contentBottom(), 24);
        MinecraftReflectionCompat.setGuiButtonEnabled(this.applyButton, false);
        MinecraftReflectionCompat.setGuiButtonEnabled(this.optionsButton,
                canConfigure(shaderList.getSelectedPackName()));
        updateEnabledButton();
        updateSettingsButton();
        updateSelectedProperties();
    }

    private void updateSelectedProperties() {
        String selectedPack = shaderList != null ? shaderList.getSelectedPackName() : null;
        if (selectedPack == null || selectedPack.equals(selectedPropertiesPack)) {
            return;
        }

        selectedPropertiesPack = selectedPack;
        selectedProperties = MainMod.getShaderPackManager().getShaderProperties(selectedPack);
    }

    private int computeLeftPanelRight() {
        int textWidth = 0;
        for (String packName : MainMod.getShaderPackManager().getAvailablePacks()) {
            textWidth = Math.max(textWidth, MinecraftReflectionCompat.fontStringWidth(this.fontRenderer, packName));
        }

        int desired = 48 + textWidth;
        int minimum = 350;
        int maximum = Math.max(180, this.width - 220);
        return Math.min(Math.max(minimum, desired), maximum);
    }

    private int detailsLeft() {
        return leftPanelRight + 10;
    }

    private int contentBottom() {
        return Math.max(80, this.height - 66);
    }

    private int drawDetailPair(int x, int y, int bottom, String label, String value, int valueColor) {
        if (y + 12 >= 80 && y < bottom) {
            int available = Math.max(1, this.width - 22 - x);
            this.drawString(this.fontRenderer, label, x, y, 0x8EA0B5);
            this.drawString(this.fontRenderer, truncateToWidth(value, available), x, y + 12, valueColor);
        }
        return y + 34;
    }

    private int maxDetailsScroll() {
        int pairCount = BetterPortalsCompat.isInstalled() ? 7 : 6;
        int contentHeight = 26 + (pairCount - 1) * 34;
        int visibleHeight = Math.max(1, contentBottom() - 80);
        return Math.max(0, (contentHeight - visibleHeight + 33) / 34);
    }

    private void drawDetailsScrollbar(int bottom) {
        int maxScroll = maxDetailsScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackLeft = this.width - 15;
        int trackTop = 80;
        int trackHeight = Math.max(1, bottom - trackTop);
        int thumbHeight = Math.max(12, trackHeight / (maxScroll + 1));
        int thumbTop = trackTop + (trackHeight - thumbHeight) * this.detailsScrollOffset / maxScroll;
        drawRect(trackLeft, trackTop, trackLeft + 3, bottom, 0x6605080C);
        drawRect(trackLeft, thumbTop, trackLeft + 3, thumbTop + thumbHeight, 0xFF5D7894);
    }

    private boolean isMouseOverDetails(int mouseX, int mouseY) {
        return mouseX >= detailsLeft() && mouseX < this.width - 10 && mouseY >= 80 && mouseY < contentBottom();
    }

    private String truncateToWidth(String text, int width) {
        String display = text == null ? "" : text;
        while (MinecraftReflectionCompat.fontStringWidth(this.fontRenderer, display) > width && display.length() > 3) {
            display = display.substring(0, display.length() - 4) + "...";
        }
        return display;
    }

    private boolean canConfigure(String packName) {
        return packName != null && !packName.equalsIgnoreCase("OFF");
    }

    private void updateEnabledButton() {
        if (toggleEnabledButton == null) {
            return;
        }
        MinecraftReflectionCompat.setGuiButtonText(toggleEnabledButton,
                pendingShadersEnabled ? "Disable" : "Enable");
        MinecraftReflectionCompat.setGuiButtonEnabled(toggleEnabledButton,
                canConfigure(MainMod.getShaderPackManager().getSelectedPackName()));
    }

    public boolean shadersEnabledForDisplay() {
        return pendingShadersEnabled;
    }

    public void toggleShadersEnabledPending() {
        pendingShadersEnabled = !pendingShadersEnabled;
        onSelectionChanged();
    }

    private void updateSettingsButton() {
        if (settingsButton != null) {
            MinecraftReflectionCompat.setGuiButtonText(settingsButton, settingsLabel());
            MinecraftReflectionCompat.setGuiButtonEnabled(settingsButton, true);
        }
    }

    private String settingsLabel() {
        return "Settings...";
    }

    private boolean dynamicLightsEnabled() {
        return MainMod.getDynamicLightConfig() != null
                && MainMod.getDynamicLightConfig().available()
                && MainMod.getDynamicLightConfig().enabled();
    }

    private String dynamicLightsStatus() {
        if (MainMod.getDynamicLightConfig() == null) {
            return "Unavailable";
        }
        if (!MainMod.getDynamicLightConfig().available()) {
            return "Disabled by Celeritas";
        }
        return (dynamicLightsEnabled() ? "On" : "Off")
                + " / " + String.format(Locale.ROOT, "%.2fx", MainMod.getDynamicLightConfig().lightMultiplier());
    }

    private boolean dynamicLightsAvailable() {
        return MainMod.getDynamicLightConfig() != null && MainMod.getDynamicLightConfig().available();
    }

    private String dynamicLightsUnavailableReason() {
        if (MainMod.getDynamicLightConfig() == null) {
            return "Dynamic lights config is unavailable";
        }
        String reason = MainMod.getDynamicLightConfig().unavailableReason();
        return reason.isEmpty() ? "Dynamic lights are unavailable" : reason;
    }

    private boolean portalShadersConfigured() {
        return MainMod.getClientSettingsConfig() != null
                && MainMod.getClientSettingsConfig().portalShadersEnabled();
    }

    private boolean portalShadersEffective() {
        return portalShadersConfigured()
                && BetterPortalsCompat.isNestedShaderPipelineAvailable()
                && BetterPortalsCompat.isInstalled()
                && BetterPortalsCompat.isSeeThroughPortalsEnabled()
                && MainMod.getShaderPackManager().areShadersEnabled();
    }

    private String portalShadersStatus() {
        if (MainMod.getClientSettingsConfig() == null) {
            return "Unavailable";
        }
        if (!portalShadersConfigured()) {
            return "Off";
        }
        if (!BetterPortalsCompat.isInstalled()) {
            return "On / Better Portals missing";
        }
        if (!BetterPortalsCompat.isSeeThroughPortalsEnabled()) {
            return "On / BP see-through off";
        }
        if (!BetterPortalsCompat.isNestedShaderPipelineAvailable()) {
            return "On / nested path disabled";
        }
        return MainMod.getShaderPackManager().areShadersEnabled() ? "On" : "On / shaders disabled";
    }

    private boolean isMouseOver(GuiButton button, int mouseX, int mouseY) {
        return button != null && MinecraftReflectionCompat.guiButtonEnabled(button)
                && GuiControlHints.isMouseOverButton(button, mouseX, mouseY);
    }

    private boolean isMouseOverButton(GuiButton button, int mouseX, int mouseY) {
        return GuiControlHints.isMouseOverButton(button, mouseX, mouseY);
    }

    private void drawEscapeHintTooltip(int mouseX, int mouseY) {
        GuiControlHints.drawEscapeHintLabel(this.fontRenderer, this.width);
        if (GuiControlHints.isMouseOverEscapeHint(this.fontRenderer, this.width, mouseX, mouseY)) {
            drawHoveringText(GuiControlHints.escapeTooltip(), mouseX, mouseY);
        }
    }

    private void installDropCallback() {
        if (dropCallback != null) {
            return;
        }

        long window = Display.getWindow();
        if (window == 0L) {
            MainMod.LOGGER.debug("Skipping shaderpack drop callback because no GLFW window handle is available.");
            return;
        }

        dropCallback = GLFWDropCallback.create((droppedWindow, count, names) -> {
            List<Path> paths = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String name = GLFWDropCallback.getName(names, i);
                if (name != null && !name.isBlank()) {
                    paths.add(Paths.get(name));
                }
            }
            MinecraftReflectionCompat.addScheduledTask(this.mc, () -> handleDroppedFiles(paths));
        });
        previousDropCallback = GLFW.glfwSetDropCallback(window, dropCallback);
    }

    private void restoreDropCallback() {
        if (dropCallback == null) {
            return;
        }

        long window = Display.getWindow();
        if (window != 0L) {
            GLFW.glfwSetDropCallback(window, previousDropCallback);
        }
        dropCallback.free();
        dropCallback = null;
        previousDropCallback = null;
    }

    private void handleDroppedFiles(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }

        ShaderPackManager manager = MainMod.getShaderPackManager();
        List<String> imported = new ArrayList<>();
        int ignored = 0;
        for (Path path : paths) {
            try {
                String packName = manager.importShaderPack(path);
                if (packName == null) {
                    ignored++;
                } else {
                    imported.add(packName);
                }
            } catch (FileAlreadyExistsException e) {
                notifyUser("Shader pack already exists: " + path.getFileName());
                MainMod.LOGGER.warn("Dropped shaderpack already exists: {}", path, e);
                refreshShaderList();
                return;
            } catch (IOException | RuntimeException e) {
                notifyUser("Failed to import shader pack: " + path.getFileName());
                MainMod.LOGGER.warn("Failed to import dropped shaderpack '{}'", path, e);
                refreshShaderList();
                return;
            }
        }

        refreshShaderList();
        if (!imported.isEmpty()) {
            String selected = imported.get(imported.size() - 1);
            shaderList.selectPack(selected);
            notifyUser(imported.size() == 1 ? "Added shader pack: " + selected : "Added " + imported.size() + " shader packs");
        } else if (ignored > 0) {
            notifyUser(ignored == 1 ? "Dropped file is not a shader pack" : "Dropped files are not shader packs");
        }
    }

    private void notifyUser(String text) {
        notificationText = text;
        notificationTicks = 100;
    }
}
