package com.l.ausm.impl.client.gui;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.client.ClientSettingsConfig;
import com.l.ausm.impl.client.dynamic.DynamicLightConfig;
import com.l.ausm.impl.client.dynamic.DynamicLightManager;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class GuiDynamicLights extends GuiScreen {
    private static final int ID_DONE = 200;
    private static final int ID_TOGGLE = 201;
    private static final int ID_MULTIPLIER_DOWN = 202;
    private static final int ID_MULTIPLIER_UP = 203;
    private static final int ID_RELOAD = 204;
    private static final int ID_OPEN_CONFIG = 205;
    private static final int ID_REFRESH = 206;
    private static final int ID_PORTAL_SHADERS = 207;
    private static final int ID_RELOAD_CLIENT_CONFIG = 208;
    private static final int ID_OPEN_CLIENT_CONFIG = 209;
    private static final double MULTIPLIER_STEP = 0.25D;

    private final GuiScreen parent;
    private GuiButton portalShaderButton;
    private GuiButton toggleButton;
    private GuiButton multiplierDownButton;
    private GuiButton multiplierUpButton;
    private GuiButton refreshButton;
    private String notificationText;
    private int notificationTicks;

    public GuiDynamicLights(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int y = 68;
        this.portalShaderButton = new GuiFlatButton(ID_PORTAL_SHADERS, centerX - 90, y, 180, 20, portalShaderLabel());
        this.buttonList.add(portalShaderButton);

        y += 54;
        this.toggleButton = new GuiFlatButton(ID_TOGGLE, centerX - 70, y, 140, 20, toggleLabel());
        this.buttonList.add(toggleButton);

        y += 48;
        this.multiplierDownButton = new GuiFlatButton(ID_MULTIPLIER_DOWN, centerX - 92, y, 42, 20, "-");
        this.buttonList.add(multiplierDownButton);
        this.multiplierUpButton = new GuiFlatButton(ID_MULTIPLIER_UP, centerX + 50, y, 42, 20, "+");
        this.buttonList.add(multiplierUpButton);

        y += 42;
        this.buttonList.add(new GuiFlatButton(ID_RELOAD, centerX - 146, y, 92, 20, "Reload Config"));
        this.buttonList.add(new GuiFlatButton(ID_OPEN_CONFIG, centerX - 46, y, 92, 20, "Open Config"));
        this.refreshButton = new GuiFlatButton(ID_REFRESH, centerX + 54, y, 92, 20, "Refresh Light");
        this.buttonList.add(refreshButton);

        y += 28;
        this.buttonList.add(new GuiFlatButton(ID_RELOAD_CLIENT_CONFIG, centerX - 96, y, 92, 20, "Reload Settings"));
        this.buttonList.add(new GuiFlatButton(ID_OPEN_CLIENT_CONFIG, centerX + 4, y, 92, 20, "Open Settings"));

        int bottom = this.height - 28;
        this.buttonList.add(new GuiFlatButton(ID_DONE, centerX - 50, bottom, 100, 20, I18n.format("gui.done")));
        updateButtons();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (notificationTicks > 0) {
            notificationTicks--;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }

        switch (button.id) {
            case ID_DONE:
                com.l.ausm.impl.util.MinecraftReflectionCompat.displayGuiScreen(this.mc, parent);
                break;
            case ID_TOGGLE:
                setEnabled(!enabled());
                break;
            case ID_MULTIPLIER_DOWN:
                changeMultiplier(-MULTIPLIER_STEP);
                break;
            case ID_MULTIPLIER_UP:
                changeMultiplier(MULTIPLIER_STEP);
                break;
            case ID_RELOAD:
                reloadConfig();
                break;
            case ID_OPEN_CONFIG:
                openConfig();
                break;
            case ID_REFRESH:
                DynamicLightManager.refreshAfterConfigChange();
                notifyUser("Light refreshed");
                break;
            case ID_PORTAL_SHADERS:
                togglePortalShaders();
                break;
            case ID_RELOAD_CLIENT_CONFIG:
                reloadClientConfig();
                break;
            case ID_OPEN_CLIENT_CONFIG:
                openClientConfig();
                break;
            default:
                break;
        }
        updateButtons();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.displayGuiScreen(this.mc, GuiControlHints.isShiftDown() ? null : parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(0, 0, this.width, this.height, 0x550B1016);

        int centerX = this.width / 2;
        this.drawCenteredString(this.fontRenderer, "AUSM Settings", centerX, 20, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "Renderer compatibility", centerX, 34, 0x9DA7B3);
        if (!available()) {
            this.drawCenteredString(this.fontRenderer, unavailableReason(), centerX, 46, 0xFFD27D);
        }

        int labelColor = 0x8EA0B5;
        int valueColor = 0xFFFFFF;
        this.drawCenteredString(this.fontRenderer, "Through-Portal Shaders", centerX, 56, labelColor);
        this.drawCenteredString(this.fontRenderer, portalShaderStatus(), centerX, 91,
                portalShadersEffective() ? 0xB4F28A : portalShadersConfigured() ? 0xFFD27D : 0xFF8A8A);

        this.drawCenteredString(this.fontRenderer, "Dynamic Lights", centerX, 110, labelColor);
        this.drawCenteredString(this.fontRenderer, "Shaderless renderer", centerX, 143, labelColor);
        this.drawCenteredString(this.fontRenderer, "Light Multiplier", centerX, 158, labelColor);
        this.drawCenteredString(this.fontRenderer, multiplierLabel(), centerX, 176, valueColor);

        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        int customCount = config != null ? config.customItemCount() : 0;
        this.drawCenteredString(this.fontRenderer, "Custom Items: " + customCount, centerX, 202, labelColor);

        drawNotification();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawLockoutTooltip(mouseX, mouseY);
        drawPortalShaderTooltip(mouseX, mouseY);
        drawEscapeHintTooltip(mouseX, mouseY);
    }

    private void setEnabled(boolean enabled) {
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        if (config == null) {
            notifyUser("Dynamic lights config unavailable");
            return;
        }
        if (enabled && !config.available()) {
            notifyUser(config.unavailableReason());
            updateButtons();
            return;
        }

        config.setEnabled(enabled);
        DynamicLightManager.refreshAfterConfigChange();
        notifyUser(enabled ? "Dynamic lights enabled" : "Dynamic lights disabled");
    }

    private void changeMultiplier(double delta) {
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        if (config == null) {
            notifyUser("Dynamic lights config unavailable");
            return;
        }
        if (!config.available()) {
            notifyUser(config.unavailableReason());
            updateButtons();
            return;
        }

        config.setLightMultiplier(config.lightMultiplier() + delta);
        DynamicLightManager.refreshAfterConfigChange();
        notifyUser("Multiplier " + multiplierLabel());
    }

    private void reloadConfig() {
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        if (config == null) {
            notifyUser("Dynamic lights config unavailable");
            return;
        }

        config.load();
        DynamicLightManager.refreshAfterConfigChange();
        notifyUser("Dynamic lights config reloaded");
    }

    private void openConfig() {
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        if (config == null) {
            notifyUser("Dynamic lights config unavailable");
            return;
        }

        try {
            Path file = config.configFile();
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                config.load();
            }
            if (!Desktop.isDesktopSupported()) {
                notifyUser("Desktop open unavailable");
                return;
            }
            Desktop.getDesktop().open(file.toFile());
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[DynamicLights] Failed to open config", e);
            notifyUser("Failed to open config");
        }
    }

    private void togglePortalShaders() {
        ClientSettingsConfig config = MainMod.getClientSettingsConfig();
        if (config == null) {
            notifyUser("Client settings unavailable");
            return;
        }

        boolean enabled = !config.portalShadersEnabled();
        config.setPortalShadersEnabled(enabled);
        notifyUser(enabled ? "Portal shaders enabled" : "Portal shaders disabled");
    }

    private void reloadClientConfig() {
        ClientSettingsConfig config = MainMod.getClientSettingsConfig();
        if (config == null) {
            notifyUser("Client settings unavailable");
            return;
        }

        config.load();
        notifyUser("Client settings reloaded");
    }

    private void openClientConfig() {
        ClientSettingsConfig config = MainMod.getClientSettingsConfig();
        if (config == null) {
            notifyUser("Client settings unavailable");
            return;
        }

        try {
            Path file = config.configFile();
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                config.load();
            }
            if (!Desktop.isDesktopSupported()) {
                notifyUser("Desktop open unavailable");
                return;
            }
            Desktop.getDesktop().open(file.toFile());
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[ClientSettings] Failed to open config", e);
            notifyUser("Failed to open settings");
        }
    }

    private void updateButtons() {
        boolean available = available();
        if (portalShaderButton != null) {
            portalShaderButton.displayString = portalShaderLabel();
            portalShaderButton.enabled = MainMod.getClientSettingsConfig() != null;
        }
        if (toggleButton != null) {
            toggleButton.displayString = toggleLabel();
            toggleButton.enabled = available;
        }
        if (multiplierDownButton != null) {
            multiplierDownButton.enabled = available && multiplier() > 0.0D;
        }
        if (multiplierUpButton != null) {
            multiplierUpButton.enabled = available && multiplier() < 4.0D;
        }
        if (refreshButton != null) {
            refreshButton.enabled = available;
        }
    }

    private String portalShaderLabel() {
        return portalShadersConfigured() ? "Portal Shaders: On" : "Portal Shaders: Off";
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

    private String portalShaderStatus() {
        if (MainMod.getClientSettingsConfig() == null) {
            return "Unavailable";
        }
        if (!portalShadersConfigured()) {
            return "Off: portal views render shaderless";
        }
        if (!BetterPortalsCompat.isInstalled()) {
            return "On: Better Portals not installed";
        }
        if (!BetterPortalsCompat.isSeeThroughPortalsEnabled()) {
            return "Blocked: Better Portals see-through off";
        }
        if (!BetterPortalsCompat.isNestedShaderPipelineAvailable()) {
            return "Unavailable: nested portal shader path disabled";
        }
        return MainMod.getShaderPackManager().areShadersEnabled()
                ? "On: portal views use AUSM shaders"
                : "On: waiting for shaders to be enabled";
    }

    private String toggleLabel() {
        if (!available()) {
            return "Unavailable";
        }
        return enabled() ? "Disable" : "Enable";
    }

    private boolean enabled() {
        return MainMod.getDynamicLightConfig() != null
                && MainMod.getDynamicLightConfig().available()
                && MainMod.getDynamicLightConfig().enabled();
    }

    private boolean available() {
        return MainMod.getDynamicLightConfig() != null && MainMod.getDynamicLightConfig().available();
    }

    private String unavailableReason() {
        if (MainMod.getDynamicLightConfig() == null) {
            return "Dynamic lights config unavailable";
        }
        String reason = MainMod.getDynamicLightConfig().unavailableReason();
        return reason.isEmpty() ? "Dynamic lights unavailable" : reason;
    }

    private double multiplier() {
        return MainMod.getDynamicLightConfig() != null ? MainMod.getDynamicLightConfig().lightMultiplier() : 0.5D;
    }

    private String multiplierLabel() {
        return String.format(Locale.ROOT, "%.2fx", multiplier());
    }

    private void drawNotification() {
        if (notificationTicks <= 0 || notificationText == null || notificationText.isEmpty()) {
            return;
        }

        int textWidth = this.fontRenderer.getStringWidth(notificationText);
        int x = (this.width - textWidth) / 2;
        int y = this.height - 58;
        drawRect(x - 8, y - 6, x + textWidth + 8, y + 14, 0xAA101418);
        this.drawString(this.fontRenderer, notificationText, x, y, 0xFFE7C86E);
    }

    private void notifyUser(String text) {
        notificationText = text;
        notificationTicks = 100;
    }

    private void drawLockoutTooltip(int mouseX, int mouseY) {
        if (available() || toggleButton == null || !toggleButton.visible
                || !GuiControlHints.isMouseOverButton(toggleButton, mouseX, mouseY)) {
            return;
        }
        drawHoveringText(List.of("AUSM dynamic lights are disabled.", unavailableReason()), mouseX, mouseY);
    }

    private void drawPortalShaderTooltip(int mouseX, int mouseY) {
        if (portalShaderButton == null || !portalShaderButton.visible
                || !GuiControlHints.isMouseOverButton(portalShaderButton, mouseX, mouseY)) {
            return;
        }

        if (BetterPortalsCompat.isInstalled() && !BetterPortalsCompat.isSeeThroughPortalsEnabled()) {
            drawHoveringText(List.of(
                    "Ignored because Better Portals see-through portals is disabled.",
                    "AUSM will not try to render shaders through opaque portals."
            ), mouseX, mouseY);
            return;
        }

        drawHoveringText(List.of(
                "On: Better Portals child views use the active AUSM shader pipeline.",
                "Off: child views intentionally use the vanilla/shaderless renderer."
        ), mouseX, mouseY);
    }

    private void drawEscapeHintTooltip(int mouseX, int mouseY) {
        GuiControlHints.drawEscapeHintLabel(this.fontRenderer, this.width);
        if (GuiControlHints.isMouseOverEscapeHint(this.fontRenderer, this.width, mouseX, mouseY)) {
            drawHoveringText(GuiControlHints.escapeTooltip(), mouseX, mouseY);
        }
    }
}
