package com.luna.ausm.impl;

import com.luna.ausm.api.shader.ShaderPackController;
import com.luna.ausm.impl.client.ClientSettingsConfig;
import com.luna.ausm.impl.client.dynamic.DynamicLightConfig;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.CeleritasCompat;
import com.luna.ausm.impl.pipeline.pack.ShaderPackManager;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.proxy.IProxy;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import com.luna.ausm.impl.util.NoOpLogger;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = Reference.MODID, name = Reference.MOD_NAME, version = Reference.VERSION, acceptableRemoteVersions = "*")
public class MainMod {

    public static final NoOpLogger LOGGER = NoOpLogger.INSTANCE;

    @SidedProxy(modId = Reference.MODID, clientSide = "com.luna.ausm.impl.proxy.ClientProxy", serverSide = "com.luna.ausm.impl.proxy.CommonProxy")
    public static IProxy proxy;

    private static ShaderPackManager shaderPackManager;
    private static DynamicLightConfig dynamicLightConfig;
    private static ClientSettingsConfig clientSettingsConfig;

    public static ShaderPackManager getShaderPackManager() {
        return shaderPackManager;
    }

    public static DynamicLightConfig getDynamicLightConfig() {
        return dynamicLightConfig;
    }

    public static ClientSettingsConfig getClientSettingsConfig() {
        return clientSettingsConfig;
    }

    public static ShaderPackController getShaderApi() {
        return shaderPackManager;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Initializing Ausm Pipeline...");

        AusmBloomLayer.initialize();

        // Initialize custom vertex formats for the shaders
        ExtendedVertexFormats.initialize();

        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        Path gameDir = MinecraftReflectionCompat.gameDir(minecraft).toPath();

        dynamicLightConfig = new DynamicLightConfig(gameDir);
        dynamicLightConfig.load();

        clientSettingsConfig = new ClientSettingsConfig(gameDir);
        clientSettingsConfig.load();

        shaderPackManager = new ShaderPackManager(gameDir);

        shaderPackManager.loadSavedConfiguration();

        CeleritasCompat.logDiagnostics();

        LOGGER.info("Available Shader Packs: {}", shaderPackManager.getAvailablePacks());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

}
