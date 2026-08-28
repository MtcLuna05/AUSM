package com.luna.ausm.impl;

import com.luna.ausm.api.shader.ShaderPackController;
import com.luna.ausm.impl.client.ClientSettingsConfig;
import com.luna.ausm.impl.client.dynamic.DynamicLightConfig;
import com.luna.ausm.impl.pipeline.pack.ShaderPackManager;
import com.luna.ausm.impl.proxy.IProxy;
import com.luna.ausm.impl.util.NoOpLogger;
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

    public static void setShaderPackManager(ShaderPackManager shaderPackManager) {
        MainMod.shaderPackManager = shaderPackManager;
    }

    public static DynamicLightConfig getDynamicLightConfig() {
        return dynamicLightConfig;
    }

    public static void setDynamicLightConfig(DynamicLightConfig dynamicLightConfig) {
        MainMod.dynamicLightConfig = dynamicLightConfig;
    }

    public static ClientSettingsConfig getClientSettingsConfig() {
        return clientSettingsConfig;
    }

    public static void setClientSettingsConfig(ClientSettingsConfig clientSettingsConfig) {
        MainMod.clientSettingsConfig = clientSettingsConfig;
    }

    public static ShaderPackController getShaderApi() {
        return shaderPackManager;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Initializing Ausm Pipeline...");
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

}
