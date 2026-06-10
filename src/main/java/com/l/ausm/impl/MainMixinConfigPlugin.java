package com.l.ausm.impl;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

public class MainMixinConfigPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);
    private static final Set<String> LOGGED_OPTIONAL_MIXINS = ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad(String s) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".NothiriumRenderChunkTaskCompileMixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/RenderChunkTaskCompile.class", false);
        }
        if (mixinClassName.endsWith(".NothiriumSectionRenderCacheDynamicLightMixin")) {
            return optionalTargetPresent(mixinClassName, "meldexun/nothirium/mc/renderer/chunk/SectionRenderCache.class", false);
        }
        if (mixinClassName.endsWith(".ProjectRedRenderHaloMixin")) {
            return optionalTargetPresent(mixinClassName, "mrtjp/projectred/core/RenderHalo$.class", false);
        }
        if (mixinClassName.endsWith(".ProjectRedLampRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "mrtjp/projectred/illumination/LampRenderer$.class", false);
        }
        if (mixinClassName.endsWith(".ProjectRedLightFactoryItemRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "mrtjp/projectred/illumination/LightFactory$$anon$1.class", false);
        }
        if (mixinClassName.endsWith(".ProjectRedButtonItemRendererMixin")) {
            return optionalTargetPresent(mixinClassName, "mrtjp/projectred/illumination/ButtonItemRenderer$.class", false);
        }
        if (mixinClassName.endsWith(".CodeChickenRenderItemMixin")) {
            return optionalTargetPresent(mixinClassName, "codechicken/lib/render/item/CCRenderItem.class", false);
        }
        if (mixinClassName.endsWith(".LumenizedRegionRenderCacheBuilderMixin")
                || mixinClassName.endsWith(".LumenizedBloomTargetMixin")) {
            return optionalTargetPresent(mixinClassName, "gregtech/client/utils/BloomEffectUtil.class", false);
        }
        if (mixinClassName.contains(".hei.")) {
            return optionalTargetPresent(mixinClassName, "mezz/jei/JEIInternalPlugin.class", false);
        }
        return true;
    }

    private static boolean optionalTargetPresent(String mixinClassName, String resourcePath, boolean allowJarFallback) {
        boolean present = classResourcePresent(resourcePath, allowJarFallback);
        if (LOGGED_OPTIONAL_MIXINS.add(mixinClassName)) {
            LOGGER.info("[MixinCompat] {} target={} present={} jarFallback={}",
                    mixinClassName, resourcePath, present, allowJarFallback);
        }
        return present;
    }

    private static boolean classResourcePresent(String resourcePath, boolean allowJarFallback) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return false;
        }

        if (resourcePresent(MainMixinConfigPlugin.class.getClassLoader(), resourcePath)) {
            return true;
        }
        if (resourcePresent(Thread.currentThread().getContextClassLoader(), resourcePath)) {
            return true;
        }
        if (resourcePresent(Launch.classLoader, resourcePath)) {
            return true;
        }
        if (ClassLoader.getSystemResource(resourcePath) != null) {
            return true;
        }
        return allowJarFallback && resourcePresentInModsDirectory(resourcePath);
    }

    private static boolean resourcePresent(ClassLoader loader, String resourcePath) {
        try {
            return loader != null && loader.getResource(resourcePath) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean resourcePresentInModsDirectory(String resourcePath) {
        File modsDirectory = new File(System.getProperty("user.dir", "."), "mods");
        if (!modsDirectory.isDirectory()) {
            return false;
        }

        File[] files = modsDirectory.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jar") || lower.endsWith(".zip");
        });
        if (files == null) {
            return false;
        }

        for (File file : files) {
            try (JarFile jar = new JarFile(file)) {
                if (jar.getEntry(resourcePath) != null) {
                    return true;
                }
            } catch (IOException | RuntimeException ignored) {
                // Broken jars should not make optional compat mixins fatal.
            }
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }
}
