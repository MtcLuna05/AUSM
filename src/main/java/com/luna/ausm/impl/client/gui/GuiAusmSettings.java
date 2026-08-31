package com.luna.ausm.impl.client.gui;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.client.ClientSettingsConfig;
import com.luna.ausm.impl.client.dynamic.DynamicLightConfig;
import com.luna.ausm.impl.client.dynamic.DynamicLightManager;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Keyboard;

/** AUSM-wide configuration screen grouped by renderer ownership. */
public final class GuiAusmSettings extends MappingSafeGuiScreen {
    private static final int ID_DONE = 200;
    private static final int ID_CATEGORY_RENDERING = 300;
    private static final int ID_CATEGORY_SHADERLESS = 301;
    private static final int ID_CATEGORY_DYNAMIC_LIGHTS = 302;
    private static final int ID_CATEGORY_SHADERED = 303;
    private static final int ID_PORTAL_SHADERS = 310;
    private static final int ID_AUTOMATIC_SHADER_DISABLING = 311;
    private static final int ID_UPDATE_CHECKER = 312;
    private static final int ID_BLOOM_SLIDER = 320;
    private static final int ID_DYNAMIC_LIGHTS = 330;
    private static final int ID_LIGHT_MULTIPLIER_SLIDER = 331;
    private static final int ID_RELOAD_DYNAMIC_LIGHTS = 333;
    private static final int ID_OPEN_DYNAMIC_LIGHTS = 334;
    private static final int ID_RELOAD_CLIENT_SETTINGS = 340;
    private static final int ID_OPEN_CLIENT_SETTINGS = 341;
    private static final int ID_SHADERED_LOD_1_SLIDER = 350;
    private static final int ID_SHADERED_LOD_2_SLIDER = 351;
    private static final int ID_SHADERED_LOD_3_SLIDER = 352;
    private static final int ID_SHADERED_LOD_4_SLIDER = 353;

    private final GuiScreen parent;
    private int selectedCategory = ID_CATEGORY_RENDERING;
    private GuiAusmValueSlider bloomSlider;
    private GuiAusmValueSlider lightMultiplierSlider;

    public GuiAusmSettings(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    protected boolean ausm$doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void ausm$initGui() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.buttonList.clear();
        int sidebarWidth = sidebarWidth();
        addCategory(ID_CATEGORY_RENDERING, 12, 62, sidebarWidth - 24, "Rendering");
        addCategory(ID_CATEGORY_SHADERLESS, 12, 86, sidebarWidth - 24, "Shaderless");
        addCategory(ID_CATEGORY_SHADERED, 12, 110, sidebarWidth - 24, "Shadered");
        addCategory(ID_CATEGORY_DYNAMIC_LIGHTS, 12, 134, sidebarWidth - 24, "Dynamic Lights");

        int contentLeft = sidebarWidth + 22;
        int contentWidth = Math.max(180, this.width - contentLeft - 18);
        int buttonWidth = Math.clamp(contentWidth, 180, 300);
        int x = contentLeft + (contentWidth - buttonWidth) / 2;
        if (selectedCategory == ID_CATEGORY_RENDERING) {
            this.buttonList.add(new GuiFlatButton(ID_PORTAL_SHADERS, x, 74, buttonWidth, 20, portalShaderLabel()));
            this.buttonList.add(new GuiFlatButton(ID_AUTOMATIC_SHADER_DISABLING, x, 100, buttonWidth, 20,
                    automaticShaderDisablingLabel()));
            this.buttonList.add(new GuiFlatButton(ID_UPDATE_CHECKER, x, 126, buttonWidth, 20, updateCheckerLabel()));
            this.buttonList.add(new GuiFlatButton(ID_RELOAD_CLIENT_SETTINGS, x, 168, (buttonWidth - 4) / 2, 20, "Reload Settings"));
            this.buttonList.add(new GuiFlatButton(ID_OPEN_CLIENT_SETTINGS, x + (buttonWidth + 4) / 2, 168,
                    (buttonWidth - 4) / 2, 20, "Open Settings"));
        } else if (selectedCategory == ID_CATEGORY_SHADERLESS) {
            this.bloomSlider = new GuiAusmValueSlider(ID_BLOOM_SLIDER, x, 92, buttonWidth,
                    "Emissive bloom", 0.0D, 3.0D, 13,
                    () -> MainMod.getClientSettingsConfig() == null ? 0.0D : MainMod.getClientSettingsConfig().shaderlessBloomIntensity(),
                    value -> {
                        ClientSettingsConfig config = MainMod.getClientSettingsConfig();
                        if (config != null) config.setShaderlessBloomIntensity((float) value);
                    },
                    value -> String.format(Locale.ROOT, "%.2fx", value));
            this.buttonList.add(bloomSlider);
        } else if (selectedCategory == ID_CATEGORY_SHADERED) {
            addShaderedLodSlider(ID_SHADERED_LOD_1_SLIDER, x, 76, buttonWidth, "LOD 1 radius",
                    ClientSettingsConfig::shaderedLod1RadiusBlocks,
                    ClientSettingsConfig::setShaderedLod1RadiusBlocks);
            addShaderedLodSlider(ID_SHADERED_LOD_2_SLIDER, x, 102, buttonWidth, "LOD 2 radius",
                    ClientSettingsConfig::shaderedLod2RadiusBlocks,
                    ClientSettingsConfig::setShaderedLod2RadiusBlocks);
            addShaderedLodSlider(ID_SHADERED_LOD_3_SLIDER, x, 128, buttonWidth, "LOD 3 radius",
                    ClientSettingsConfig::shaderedLod3RadiusBlocks,
                    ClientSettingsConfig::setShaderedLod3RadiusBlocks);
            addShaderedLodSlider(ID_SHADERED_LOD_4_SLIDER, x, 154, buttonWidth, "LOD 4 radius",
                    ClientSettingsConfig::shaderedLod4RadiusBlocks,
                    ClientSettingsConfig::setShaderedLod4RadiusBlocks);
        } else {
            this.buttonList.add(new GuiFlatButton(ID_DYNAMIC_LIGHTS, x, 74, buttonWidth, 20, dynamicLightsLabel()));
            this.lightMultiplierSlider = new GuiAusmValueSlider(ID_LIGHT_MULTIPLIER_SLIDER, x, 118, buttonWidth,
                    "Dynamic light intensity", 0.0D, 4.0D, 17,
                    () -> MainMod.getDynamicLightConfig() == null ? 0.0D : MainMod.getDynamicLightConfig().lightMultiplier(),
                    value -> {
                        DynamicLightConfig config = MainMod.getDynamicLightConfig();
                        if (config != null) {
                            config.setLightMultiplier(value);
                            DynamicLightManager.refreshAfterConfigChange();
                        }
                    },
                    value -> String.format(Locale.ROOT, "%.2fx", value));
            this.buttonList.add(lightMultiplierSlider);
            this.buttonList.add(new GuiFlatButton(ID_RELOAD_DYNAMIC_LIGHTS, x, 164, (buttonWidth - 4) / 2, 20, "Reload Config"));
            this.buttonList.add(new GuiFlatButton(ID_OPEN_DYNAMIC_LIGHTS, x + (buttonWidth + 4) / 2, 164,
                    (buttonWidth - 4) / 2, 20, "Edit Custom Items"));
        }

        this.buttonList.add(new GuiFlatButton(ID_DONE, this.width / 2 - 50, this.height - 28, 100, 20,
                MinecraftReflectionCompat.i18nFormat("gui.done")));
        updateButtonState();
    }

    private void addCategory(int id, int x, int y, int width, String label) {
        GuiButton button = new GuiFlatButton(id, x, y, width, 20, label);
        MinecraftReflectionCompat.setGuiButtonEnabled(button, id != selectedCategory);
        this.buttonList.add(button);
    }

    private void addShaderedLodSlider(int id, int x, int y, int width, String label,
                                      ToIntFunction<ClientSettingsConfig> getter,
                                      ObjIntConsumer<ClientSettingsConfig> setter) {
        GuiAusmValueSlider slider = new GuiAusmValueSlider(id, x, y, width, label,
                16.0D, 2048.0D, 129,
                () -> {
                    ClientSettingsConfig config = MainMod.getClientSettingsConfig();
                    return config == null ? 16.0D : getter.applyAsInt(config);
                },
                value -> {
                    ClientSettingsConfig config = MainMod.getClientSettingsConfig();
                    if (config != null) {
                        setter.accept(config, (int) Math.round(value));
                    }
                },
                value -> String.format(Locale.ROOT, "%d blocks", Math.round(value)));
        this.buttonList.add(slider);
    }

    @Override
    protected void ausm$actionPerformed(GuiButton button) throws IOException {
        int id = MinecraftReflectionCompat.guiButtonId(button);
        if (id == ID_DONE) {
            MinecraftReflectionCompat.displayGuiScreen(this.mc, parent);
            return;
        }
        if (id >= ID_CATEGORY_RENDERING && id <= ID_CATEGORY_SHADERED) {
            selectedCategory = id;
            rebuildButtons();
            return;
        }

        ClientSettingsConfig client = MainMod.getClientSettingsConfig();
        DynamicLightConfig lights = MainMod.getDynamicLightConfig();
        switch (id) {
            case ID_PORTAL_SHADERS:
                if (client != null) client.setPortalShadersEnabled(!client.portalShadersEnabled());
                break;
            case ID_AUTOMATIC_SHADER_DISABLING:
                if (client != null) client.setAutomaticShaderDisablingEnabled(!client.automaticShaderDisablingEnabled());
                break;
            case ID_UPDATE_CHECKER:
                if (client != null) client.setUpdateCheckerEnabled(!client.updateCheckerEnabled());
                break;
            case ID_DYNAMIC_LIGHTS:
                if (lights != null && lights.available()) {
                    lights.setEnabled(!lights.enabled());
                    DynamicLightManager.refreshAfterConfigChange();
                }
                break;
            case ID_RELOAD_DYNAMIC_LIGHTS:
                if (lights != null) {
                    lights.load();
                    DynamicLightManager.refreshAfterConfigChange();
                }
                break;
            case ID_OPEN_DYNAMIC_LIGHTS:
                MinecraftReflectionCompat.displayGuiScreen(this.mc, new GuiDynamicLightItems(this));
                break;
            case ID_RELOAD_CLIENT_SETTINGS:
                if (client != null) client.load();
                break;
            case ID_OPEN_CLIENT_SETTINGS:
                openConfig(client == null ? null : client.configFile());
                break;
            default:
                return;
        }
        rebuildButtons();
    }

    @Override
    protected void ausm$keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            MinecraftReflectionCompat.displayGuiScreen(this.mc, parent);
            return;
        }
        super.ausm$keyTyped(typedChar, keyCode);
    }

    @Override
    protected void ausm$drawScreen(int mouseX, int mouseY, float partialTicks) {
        int sidebarWidth = sidebarWidth();
        int contentLeft = sidebarWidth + 12;
        int topBandBottom = 46;
        int bottomBandTop = Math.max(topBandBottom, this.height - 40);
        MinecraftReflectionCompat.bindTexture(MinecraftReflectionCompat.textureManager(this.mc), MinecraftReflectionCompat.optionsBackgroundTexture());
        Tessellator tessellator = MinecraftReflectionCompat.tessellator();
        BufferBuilder buffer = MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        MinecraftReflectionCompat.bufferBegin(buffer, 7, MinecraftReflectionCompat.positionTexColorFormat());
        MinecraftReflectionCompat.bufferPosTexColorEnd(buffer, 0, topBandBottom, 0, 0, topBandBottom / 32.0F, 64, 64, 64, 255);
        MinecraftReflectionCompat.bufferPosTexColorEnd(buffer, this.width, topBandBottom, 0, this.width / 32.0F, topBandBottom / 32.0F, 64, 64, 64, 255);
        MinecraftReflectionCompat.bufferPosTexColorEnd(buffer, this.width, 0, 0, this.width / 32.0F, 0, 64, 64, 64, 255);
        MinecraftReflectionCompat.bufferPosTexColorEnd(buffer, 0, 0, 0, 0, 0, 64, 64, 64, 255);
        MinecraftReflectionCompat.tessellatorDraw(tessellator);
        drawRect(0, topBandBottom, this.width, topBandBottom + 2, 0x66000000);
        MinecraftReflectionCompat.bufferBegin(buffer, 7, MinecraftReflectionCompat.positionTexColorFormat());
        MinecraftReflectionCompat.bufferPosTexColorEnd(buffer, 0, this.height, 0, 0, this.height / 32.0F, 64, 64, 64, 255);
        MinecraftReflectionCompat.bufferPosTexColorEnd(buffer, this.width, this.height, 0, this.width / 32.0F, this.height / 32.0F, 64, 64, 64, 255);
        MinecraftReflectionCompat.bufferPosTexColorEnd(buffer, this.width, bottomBandTop, 0, this.width / 32.0F, bottomBandTop / 32.0F, 64, 64, 64, 255);
        MinecraftReflectionCompat.bufferPosTexColorEnd(buffer, 0, bottomBandTop, 0, 0, bottomBandTop / 32.0F, 64, 64, 64, 255);
        MinecraftReflectionCompat.tessellatorDraw(tessellator);
        drawRect(0, bottomBandTop - 2, this.width, bottomBandTop, 0x66000000);
        drawRect(8, 50, sidebarWidth, bottomBandTop - 4, 0x660E0E0E);
        drawRect(contentLeft, 50, this.width - 8, bottomBandTop - 4, 0x660E0E0E);
        this.drawCenteredString(this.fontRenderer, "AUSM Settings", this.width / 2, 18, 0xFFFFFF);
        this.drawString(this.fontRenderer, "Categories", 18, 54, 0xDCE5F0);
        this.drawString(this.fontRenderer, categoryTitle(), contentLeft + 12, 54, 0xDCE5F0);
        drawCategoryContent(contentLeft + 12);
        super.ausm$drawScreen(mouseX, mouseY, partialTicks);
        drawSettingTooltip(mouseX, mouseY);
        GuiControlHints.drawEscapeHintLabel(this.fontRenderer, this.width);
    }

    private void drawCategoryContent(int x) {
        if (selectedCategory == ID_CATEGORY_RENDERING) {
            this.drawString(this.fontRenderer, "Renderer ownership", x, 66, 0x9DA7B3);
        } else if (selectedCategory == ID_CATEGORY_SHADERLESS) {
            this.drawString(this.fontRenderer, "Shaderless renderer", x, 66, 0x9DA7B3);
        } else if (selectedCategory == ID_CATEGORY_SHADERED) {
            this.drawString(this.fontRenderer, "Shadered quality LOD (block radius)", x, 66, 0x9DA7B3);
            this.drawString(this.fontRenderer, "Foliage waving is disabled from LOD 2 onward.", x, 180, 0x9DA7B3);
        } else {
            this.drawString(this.fontRenderer, "Dynamic lights", x, 66, 0x9DA7B3);
            DynamicLightConfig config = MainMod.getDynamicLightConfig();
            int customItems = config == null ? 0 : config.customItemCount();
            this.drawString(this.fontRenderer, "Custom item lights: " + customItems, x, 150, 0x9DA7B3);
        }
    }

    private void updateButtonState() {
        ClientSettingsConfig client = MainMod.getClientSettingsConfig();
        DynamicLightConfig lights = MainMod.getDynamicLightConfig();
        for (GuiButton button : this.buttonList) {
            int id = MinecraftReflectionCompat.guiButtonId(button);
            if (id == ID_PORTAL_SHADERS || id == ID_AUTOMATIC_SHADER_DISABLING || id == ID_UPDATE_CHECKER || id == ID_BLOOM_SLIDER
                    || id == ID_RELOAD_CLIENT_SETTINGS || id == ID_OPEN_CLIENT_SETTINGS
                    || id >= ID_SHADERED_LOD_1_SLIDER && id <= ID_SHADERED_LOD_4_SLIDER) {
                MinecraftReflectionCompat.setGuiButtonEnabled(button, client != null);
            }
            if (id == ID_DYNAMIC_LIGHTS || id == ID_LIGHT_MULTIPLIER_SLIDER || id == ID_RELOAD_DYNAMIC_LIGHTS || id == ID_OPEN_DYNAMIC_LIGHTS) {
                boolean requiresAvailableLights = id == ID_DYNAMIC_LIGHTS || id == ID_LIGHT_MULTIPLIER_SLIDER;
                MinecraftReflectionCompat.setGuiButtonEnabled(button, lights != null && (!requiresAvailableLights || lights.available()));
            }
        }
    }

    private int sidebarWidth() {
        return Math.clamp(this.width / 4, 150, 230);
    }

    private String categoryTitle() {
        return selectedCategory == ID_CATEGORY_RENDERING ? "Rendering"
                : selectedCategory == ID_CATEGORY_SHADERLESS ? "Shaderless"
                : selectedCategory == ID_CATEGORY_SHADERED ? "Shadered" : "Dynamic Lights";
    }

    private String portalShaderLabel() {
        ClientSettingsConfig config = MainMod.getClientSettingsConfig();
        return "Portal Shaders: " + (config != null && config.portalShadersEnabled() ? "ON" : "OFF");
    }

    private String automaticShaderDisablingLabel() {
        ClientSettingsConfig config = MainMod.getClientSettingsConfig();
        return "Automatic Shader Disabling: " + (config != null && config.automaticShaderDisablingEnabled() ? "ON" : "OFF");
    }

    private String updateCheckerLabel() {
        ClientSettingsConfig config = MainMod.getClientSettingsConfig();
        return "Update Checker: " + (config != null && config.updateCheckerEnabled() ? "ON" : "OFF");
    }

    private String dynamicLightsLabel() {
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        if (config == null || !config.available()) return "Dynamic Lights: Unavailable";
        return "Dynamic Lights: " + (config.enabled() ? "ON" : "OFF");
    }

    private void drawSettingTooltip(int mouseX, int mouseY) {
        for (GuiButton button : this.buttonList) {
            if (!GuiControlHints.isMouseOverButton(button, mouseX, mouseY)) {
                continue;
            }
            if (button instanceof GuiAusmValueSlider slider && slider.dragging()) {
                drawHoveringText(List.of(slider.valueLabel()), mouseX + 12, mouseY - 12);
                return;
            }
            int id = MinecraftReflectionCompat.guiButtonId(button);
            List<String> tooltip = switch (id) {
                case ID_PORTAL_SHADERS -> List.of("Use AUSM shaders for Better Portals child views when available.");
                case ID_AUTOMATIC_SHADER_DISABLING -> List.of("Disable a restored shader pack at startup to keep the first world load stable.");
                case ID_UPDATE_CHECKER -> List.of("Check for a newer AUSM release after loading a world. Failures only appear in latest.log.");
                case ID_BLOOM_SLIDER -> List.of("Strength of emissive bloom in the vanilla, shaderless renderer.");
                case ID_SHADERED_LOD_1_SLIDER -> List.of("Distance in blocks where the shadered LOD changes from full to half resolution.");
                case ID_SHADERED_LOD_2_SLIDER -> List.of("Distance in blocks where foliage waving is disabled and shadered LOD becomes quarter resolution.");
                case ID_SHADERED_LOD_3_SLIDER -> List.of("Distance in blocks where shadered LOD becomes eighth resolution.");
                case ID_SHADERED_LOD_4_SLIDER -> List.of("Distance in blocks where shadered LOD features begin fading out.");
                case ID_DYNAMIC_LIGHTS -> List.of("Enable shaderless item and held-item dynamic lights.");
                case ID_LIGHT_MULTIPLIER_SLIDER -> List.of("Scale light emitted by block items; explicit custom items keep their chosen level.");
                case ID_RELOAD_DYNAMIC_LIGHTS -> List.of("Reload dynamic-light settings from disk.");
                case ID_OPEN_DYNAMIC_LIGHTS -> List.of("Edit registry-backed custom item lights, metadata, and color.");
                case ID_RELOAD_CLIENT_SETTINGS -> List.of("Reload general AUSM settings from disk.");
                case ID_OPEN_CLIENT_SETTINGS -> List.of("Open general AUSM settings in the system editor.");
                default -> List.of();
            };
            if (!tooltip.isEmpty()) {
                drawHoveringText(tooltip, mouseX, mouseY);
            }
            return;
        }
    }

    private void openConfig(Path file) {
        if (file == null || !Desktop.isDesktopSupported()) return;
        try {
            Files.createDirectories(file.getParent());
            Desktop.getDesktop().open(file.toFile());
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("Failed to open AUSM config {}", file, e);
        }
    }
}
