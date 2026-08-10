package com.l.ausm.impl.client.gui;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.pack.ShaderPackManager;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
        this.leftPanelRight = computeLeftPanelRight();
        this.shaderList = new GuiSlotShaders(this, this.mc, leftPanelRight, 76, this.height - 42, 20);

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

        this.refreshButton = new GuiFlatButton(ID_REFRESH, leftPanelRight - 66, 53, 54, 16, "Refresh");
        this.buttonList.add(this.refreshButton);

        this.toggleEnabledButton = new GuiFlatButton(ID_TOGGLE_ENABLED, 20, 53, 74, 16, MainMod.getShaderPackManager().areShadersEnabled() ? "Disable" : "Enable");
        MinecraftReflectionCompat.setGuiButtonEnabled(this.toggleEnabledButton,
                canConfigure(MainMod.getShaderPackManager().getSelectedPackName()));
        this.buttonList.add(this.toggleEnabledButton);

        this.settingsButton = new GuiFlatButton(ID_SETTINGS, 98, 53, 116, 16, settingsLabel());
        this.buttonList.add(this.settingsButton);

        this.previewButton = new GuiFlatButton(ID_PREVIEW, this.width - 90, utilityY, 80, 20, "Preview");
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
        this.shaderList.handleMouseInput();
    }

    @Override
    protected void ausm$actionPerformed(GuiButton button) throws IOException {
        if (!MinecraftReflectionCompat.guiButtonEnabled(button)) {
            return;
        }

        switch (MinecraftReflectionCompat.guiButtonId(button)) {
            case ID_DONE:
                if (this.parentScreen == null) {
                    com.l.ausm.impl.util.MinecraftReflectionCompat.setField((this.mc), (null), "field_71462_r", "currentScreen");;
                    com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((this.mc), new String[] {"func_71381_h", "setIngameFocus"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);;
                    return;
                }
                com.l.ausm.impl.util.MinecraftReflectionCompat.displayGuiScreen(this.mc, this.parentScreen);
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
                boolean enabled = !MainMod.getShaderPackManager().areShadersEnabled();
                MainMod.getShaderPackManager().setShadersEnabled(enabled);
                updateEnabledButton();
                break;
            case ID_SETTINGS:
                com.l.ausm.impl.util.MinecraftReflectionCompat.displayGuiScreen(this.mc, new GuiDynamicLights(this));
                break;
            case ID_PREVIEW:
                setPreviewHidden(!previewHidden);
                break;
            case ID_OPEN_FOLDER:
                openShaderpacksFolder();
                break;
            case ID_CANCEL:
                com.l.ausm.impl.util.MinecraftReflectionCompat.displayGuiScreen(this.mc, this.parentScreen);
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
                    selectedPack != null && !selectedPack.equals(currentPack));
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

        MainMod.getShaderPackManager().loadPack(selectedPack);
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
        com.l.ausm.impl.util.MinecraftReflectionCompat.displayGuiScreen(this.mc, new GuiShaderOptions(this, packName));
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

        drawRect(0, 0, this.width, this.height, 0x330B1016);
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
                com.l.ausm.impl.util.MinecraftReflectionCompat.displayGuiScreen(this.mc, null);
                return;
            }
            if (previewHidden) {
                setPreviewHidden(false);
                return;
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.displayGuiScreen(this.mc, parentScreen);
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
        return Mouse.getX() * this.width / com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(this.mc);
    }

    private int currentMouseY() {
        return this.height - Mouse.getY() * this.height / com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(this.mc) - 1;
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
        int bottom = this.height - 36;
        int detailsLeft = detailsLeft();
        drawRect(10, 50, leftPanelRight, bottom, 0x66101418);
        drawRect(detailsLeft, 50, this.width - 10, bottom, 0x66101418);
        drawRect(10, 50, leftPanelRight, 70, 0x8818202A);
        drawRect(detailsLeft, 50, this.width - 10, 70, 0x8818202A);
        this.drawString(this.fontRenderer, "Packs", 20, 56, 0xDCE5F0);
        this.drawString(this.fontRenderer, "Details", detailsLeft + 12, 56, 0xDCE5F0);
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
        int y = 86;
        this.drawString(this.fontRenderer, "Selected", x, y, 0x8EA0B5);
        this.drawString(this.fontRenderer, selectedPack, x, y + 12, 0xFFFFFF);
        this.drawString(this.fontRenderer, "Active", x, y + 34, 0x8EA0B5);
        this.drawString(this.fontRenderer, currentPack, x, y + 46, 0xFFFFFF);
        this.drawString(this.fontRenderer, "Available packs", x, y + 68, 0x8EA0B5);
        this.drawString(this.fontRenderer, Integer.toString(shaderList.getPackCount()), x, y + 80, 0xFFFFFF);

        ShaderProperties properties = selectedProperties != null ? selectedProperties : PipelineContext.getInstance().getShaderProperties();
        this.drawString(this.fontRenderer, "Current options", x, y + 102, 0x8EA0B5);
        this.drawString(this.fontRenderer, Integer.toString(properties.options().all().size()), x, y + 114, 0xFFFFFF);
        this.drawString(this.fontRenderer, "Enabled", x, y + 136, 0x8EA0B5);
        this.drawString(this.fontRenderer, MainMod.getShaderPackManager().areShadersEnabled() ? "Yes" : "No", x, y + 148,
                MainMod.getShaderPackManager().areShadersEnabled() ? 0xB4F28A : 0xFF8A8A);
        this.drawString(this.fontRenderer, "Dynamic lights", x, y + 170, 0x8EA0B5);
        this.drawString(this.fontRenderer, dynamicLightsStatus(), x, y + 182,
                dynamicLightsEnabled() ? 0xB4F28A : dynamicLightsAvailable() ? 0xFF8A8A : 0xFFD27D);
        this.drawString(this.fontRenderer, "Portal shaders", x, y + 204, 0x8EA0B5);
        this.drawString(this.fontRenderer, portalShadersStatus(), x, y + 216,
                portalShadersEffective() ? 0xB4F28A : portalShadersConfigured() ? 0xFFD27D : 0xFF8A8A);

        boolean pending = this.applyButton != null && MinecraftReflectionCompat.guiButtonEnabled(this.applyButton);
        String status = pending ? "Pending pack change" : "No pending pack change";
        this.drawString(this.fontRenderer, status, x, this.height - 56, pending ? 0xFFD27D : 0x8EA0B5);
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
        this.shaderList = new GuiSlotShaders(this, this.mc, leftPanelRight, 76, this.height - 42, 20);
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

        int desired = 28 + textWidth + 24;
        int max = Math.max(220, this.width - 180);
        return Math.min(Math.max(296, desired), max);
    }

    private int detailsLeft() {
        return leftPanelRight + 10;
    }

    private boolean canConfigure(String packName) {
        return packName != null && !packName.equalsIgnoreCase("OFF");
    }

    private void updateEnabledButton() {
        if (toggleEnabledButton == null) {
            return;
        }
        MinecraftReflectionCompat.setGuiButtonText(toggleEnabledButton,
                MainMod.getShaderPackManager().areShadersEnabled() ? "Disable" : "Enable");
        MinecraftReflectionCompat.setGuiButtonEnabled(toggleEnabledButton,
                canConfigure(MainMod.getShaderPackManager().getSelectedPackName()));
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
                + " / " + String.format(java.util.Locale.ROOT, "%.2fx", MainMod.getDynamicLightConfig().lightMultiplier());
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
            com.l.ausm.impl.util.MinecraftReflectionCompat.addScheduledTask(this.mc, () -> handleDroppedFiles(paths));
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
