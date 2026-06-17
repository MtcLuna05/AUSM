package com.l.ausm.impl.client;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.Reference;
import com.l.ausm.impl.client.dynamic.DynamicLightConfig;
import com.l.ausm.impl.client.dynamic.DynamicLightManager;
import com.l.ausm.impl.client.gui.GuiShaders;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.pack.ShaderPackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

// Automatically registers the @SubscribeEvent methods to the Forge Event Bus on the Client side
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = Reference.MODID)
public class KeybindManager {

    private static final String CATEGORY = "key.categories.ausm";

    public static KeyBinding openConfig;
    public static KeyBinding reloadShader;
    public static KeyBinding toggleShader;
    public static KeyBinding forceLightRecalculation;
    public static KeyBinding toggleDynamicLights;

    /**
     * Initializes and registers the keybinds.
     * Must be called during the FMLInitializationEvent on the Client side.
     */
    public static void init() {
        openConfig = new KeyBinding("key.ausm.config", Keyboard.KEY_O, CATEGORY);
        reloadShader = new KeyBinding("key.ausm.reload", Keyboard.KEY_R, CATEGORY);
        toggleShader = new KeyBinding("key.ausm.toggle", Keyboard.KEY_K, CATEGORY);
        forceLightRecalculation = new KeyBinding("key.ausm.force_light_recalculation", Keyboard.KEY_F8, CATEGORY);
        toggleDynamicLights = new KeyBinding("key.ausm.toggle_dynamic_lights", Keyboard.KEY_F9, CATEGORY);

        ClientRegistry.registerKeyBinding(openConfig);
        ClientRegistry.registerKeyBinding(reloadShader);
        ClientRegistry.registerKeyBinding(toggleShader);
        ClientRegistry.registerKeyBinding(forceLightRecalculation);
        ClientRegistry.registerKeyBinding(toggleDynamicLights);
    }

    /**
     * Listens for key presses every tick.
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null
                || openConfig == null
                || reloadShader == null
                || toggleShader == null
                || forceLightRecalculation == null
                || toggleDynamicLights == null) {
            return;
        }

        while (openConfig.isPressed()) {
            minecraft.displayGuiScreen(new GuiShaders(null));
        }

        while (reloadShader.isPressed()) {
            MainMod.LOGGER.info("Reloading Shaders...");
            ShaderPackManager manager = MainMod.getShaderPackManager();
            manager.reloadPack();
            sendActionBar("Shaders reloaded: " + displayPackName(manager.getSelectedPackName()));
        }

        while (toggleShader.isPressed()) {
            ShaderPackManager manager = MainMod.getShaderPackManager();
            boolean currentState = manager.areShadersEnabled();
            MainMod.LOGGER.info("Toggling Pipeline Active state to: {}", !currentState);
            manager.setShadersEnabled(!currentState);

            String state = manager.areShadersEnabled() ? "Enabled" : "Disabled";
            sendActionBar(state + " shaders: " + displayPackName(manager.getSelectedPackName()));
        }

        while (forceLightRecalculation.isPressed()) {
            MainMod.LOGGER.info("Forcing nearby light recalculation...");
            int[] result = PipelineContext.getInstance().forceLightRecalculation();
            int chunks = result.length > 1 ? result[1] : 0;
            int blockChecks = result.length > 2 ? result[2] : 0;
            if (chunks > 0 || blockChecks > 0) {
                sendActionBar("Forced light recalculation: " + blockChecks + " light checks, " + chunks + " chunks");
            } else {
                sendActionBar("No loaded world light data to recalculate");
            }
        }

        while (toggleDynamicLights.isPressed()) {
            toggleDynamicLights();
        }
    }

    private static void toggleDynamicLights() {
        DynamicLightConfig config = MainMod.getDynamicLightConfig();
        if (config == null) {
            sendActionBar("Dynamic lights config unavailable");
            return;
        }
        if (!config.available()) {
            String reason = config.unavailableReason();
            sendActionBar(reason.isEmpty() ? "Dynamic lights unavailable" : reason);
            return;
        }

        boolean enabled = !config.enabled();
        config.setEnabled(enabled);
        DynamicLightManager.refreshAfterConfigChange();
        MainMod.LOGGER.info("Dynamic lights toggled via keybind: {}", enabled);
        sendActionBar(enabled ? "Dynamic lights enabled" : "Dynamic lights disabled");
    }

    private static void sendActionBar(String message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft != null ? minecraft.player : null;
        if (player != null) {
            player.sendStatusMessage(new TextComponentString(message), true);
        }
    }

    private static String displayPackName(String packName) {
        return packName == null || packName.equalsIgnoreCase("OFF") ? "OFF" : packName;
    }
}
