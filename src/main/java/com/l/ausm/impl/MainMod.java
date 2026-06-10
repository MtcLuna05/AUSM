package com.l.ausm.impl;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.shader.ShaderPackController;
import com.l.ausm.impl.client.dynamic.DynamicLightConfig;
import com.l.ausm.impl.pipeline.pack.ShaderPackManager;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.proxy.IProxy;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Reference.MODID, name = Reference.MOD_NAME, version = Reference.VERSION)
public class MainMod {

    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    @SidedProxy(modId = Reference.MODID, clientSide = "com.l.ausm.impl.proxy.ClientProxy", serverSide = "com.l.ausm.impl.proxy.CommonProxy")
    public static IProxy proxy;

    private static ShaderPackManager shaderPackManager;
    private static DynamicLightConfig dynamicLightConfig;

    public static ShaderPackManager getShaderPackManager() {
        return shaderPackManager;
    }

    public static DynamicLightConfig getDynamicLightConfig() {
        return dynamicLightConfig;
    }

    public static ShaderPackController getShaderApi() {
        return shaderPackManager;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Initializing Ausm Pipeline...");

        // Initialize custom vertex formats for the shaders
        ExtendedVertexFormats.initialize();

        dynamicLightConfig = new DynamicLightConfig(Minecraft.getMinecraft().gameDir.toPath());
        dynamicLightConfig.load();

        shaderPackManager = new ShaderPackManager(Minecraft.getMinecraft().gameDir.toPath());

        shaderPackManager.loadSavedConfiguration();

        LOGGER.info("Available Shader Packs: {}", shaderPackManager.getAvailablePacks());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

}
