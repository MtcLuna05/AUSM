package com.l.ausm.impl;

import com.l.ausm.api.shader.ShaderPackController;
import com.l.ausm.impl.client.ClientSettingsConfig;
import com.l.ausm.impl.client.dynamic.DynamicLightConfig;
import com.l.ausm.impl.pipeline.pack.ShaderPackManager;
import com.l.ausm.impl.pipeline.compat.CeleritasCompat;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.proxy.IProxy;
import com.l.ausm.impl.util.NoOpLogger;
import net.minecraft.client.Minecraft;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = Reference.MODID, name = Reference.MOD_NAME, version = Reference.VERSION, acceptableRemoteVersions = "*")
public class MainMod {

    public static final NoOpLogger LOGGER = NoOpLogger.INSTANCE;

    @SidedProxy(modId = Reference.MODID, clientSide = "com.l.ausm.impl.proxy.ClientProxy", serverSide = "com.l.ausm.impl.proxy.CommonProxy")
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

        Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        java.nio.file.Path gameDir = com.l.ausm.impl.util.MinecraftReflectionCompat.gameDir(minecraft).toPath();

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
