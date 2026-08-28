package com.luna.ausm.impl.proxy;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.client.ClientSettingsConfig;
import com.luna.ausm.impl.client.dynamic.DynamicLightConfig;
import com.luna.ausm.impl.client.EuphoriaEntreePackGenerator;
import com.luna.ausm.impl.client.KeybindManager;
import com.luna.ausm.impl.client.ThaumcraftParticleBridge;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.CeleritasCompat;
import com.luna.ausm.impl.pipeline.pack.ShaderPackManager;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        AusmBloomLayer.initialize();
        ExtendedVertexFormats.initialize();

        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        Path gameDir = MinecraftReflectionCompat.gameDir(minecraft).toPath();

        DynamicLightConfig dynamicLights = new DynamicLightConfig(gameDir);
        dynamicLights.load();
        MainMod.setDynamicLightConfig(dynamicLights);

        ClientSettingsConfig settings = new ClientSettingsConfig(gameDir);
        settings.load();
        MainMod.setClientSettingsConfig(settings);

        ShaderPackManager shaderPacks = new ShaderPackManager(gameDir);
        MainMod.setShaderPackManager(shaderPacks);
        shaderPacks.loadSavedConfiguration();

        CeleritasCompat.logDiagnostics();
        MainMod.LOGGER.info("Available Shader Packs: {}", shaderPacks.getAvailablePacks());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        KeybindManager.init();
        EuphoriaEntreePackGenerator.init();
        ThaumcraftParticleBridge.init();
    }
}
