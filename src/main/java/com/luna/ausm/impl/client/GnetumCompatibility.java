package com.luna.ausm.impl.client;

import com.luna.ausm.impl.pipeline.PipelineContext;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Coordinates the AUSM HUD boundary with Gnetum.
 *
 * Gnetum's multi-frame HUD cache assumes that the main framebuffer remains
 * valid across its configured passes. AUSM presents a new shadered world each
 * client frame, so later Gnetum passes can composite a cache over a cleared
 * main framebuffer. Restrict the complete cache-and-presentation sequence to
 * one pass, then restore the user's configured value.
 */
public final class GnetumCompatibility {
    private static final String GNETUM_CLASS = "me.decce.gnetum.Gnetum";
    private static boolean resolved;
    private static boolean available;
    private static Object config;
    private static Field numberOfPasses;
    private static Field cacheSettingPass;
    private static Field[] cacheSettingMaps;
    private static final ThreadLocal<HudPassOverride> hudPassOverride = new ThreadLocal<>();

    private GnetumCompatibility() {
    }

    public static boolean isInstalled() {
        resolveGnetum();
        return available;
    }

    public static void beginShaderedCachePass() {
        if (!PipelineContext.getInstance().isActive()) {
            return;
        }
        resolveGnetum();
        if (!available || config == null || numberOfPasses == null) {
            return;
        }
        HudPassOverride activeOverride = hudPassOverride.get();
        if (activeOverride != null) {
            return;
        }
        try {
            int configuredPassCount = numberOfPasses.getInt(config);
            if (configuredPassCount <= 1) {
                return;
            }
            HudPassOverride override = new HudPassOverride(configuredPassCount);
            remapCachedElementsToFirstPass(override);
            numberOfPasses.setInt(config, 1);
            hudPassOverride.set(override);
        } catch (IllegalAccessException ignored) {
            available = false;
        }
    }

    public static void finishShaderedCachePass() {
        HudPassOverride activeOverride = hudPassOverride.get();
        if (activeOverride == null) {
            return;
        }
        try {
            if (available && config != null && numberOfPasses != null) {
                restoreCachedElementPasses(activeOverride);
                numberOfPasses.setInt(config, activeOverride.configuredPassCount);
            }
        } catch (IllegalAccessException ignored) {
            available = false;
        } finally {
            hudPassOverride.remove();
        }
    }

    /**
     * Gnetum is about to alpha-composite its cached HUD into the Minecraft
     * framebuffer. Re-establish AUSM's final world image first, because a
     * prior cache pass may have cleared that framebuffer.
     */
    public static void restoreShaderedWorldBeforeCacheBlit() {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isActive()) {
            context.restoreCurrentWorldForExternalHudComposite();
        }
    }

    private static void remapCachedElementsToFirstPass(HudPassOverride override) throws IllegalAccessException {
        if (cacheSettingMaps == null || cacheSettingPass == null) {
            return;
        }
        for (Field cacheSettingMap : cacheSettingMaps) {
            Object value = cacheSettingMap.get(config);
            if (!(value instanceof Map)) {
                continue;
            }
            for (Object setting : ((Map<?, ?>) value).values()) {
                if (setting == null || override.elementPasses.containsKey(setting)) {
                    continue;
                }
                int originalPass = cacheSettingPass.getInt(setting);
                override.elementPasses.put(setting, originalPass);
                if (originalPass != 1) {
                    cacheSettingPass.setInt(setting, 1);
                }
            }
        }
    }

    private static void restoreCachedElementPasses(HudPassOverride override) throws IllegalAccessException {
        if (cacheSettingPass == null) {
            return;
        }
        for (Map.Entry<Object, Integer> entry : override.elementPasses.entrySet()) {
            cacheSettingPass.setInt(entry.getKey(), entry.getValue());
        }
    }

    private static void resolveGnetum() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> gnetum = Class.forName(GNETUM_CLASS, false, GnetumCompatibility.class.getClassLoader());
            Field configField = gnetum.getDeclaredField("config");
            configField.setAccessible(true);
            config = configField.get(null);
            if (config == null) {
                available = false;
                return;
            }
            numberOfPasses = config.getClass().getDeclaredField("numberOfPasses");
            numberOfPasses.setAccessible(true);
            ClassLoader classLoader = config.getClass().getClassLoader();
            Class<?> cacheSetting = Class.forName("me.decce.gnetum.CacheSetting", false, classLoader);
            cacheSettingPass = cacheSetting.getDeclaredField("pass");
            cacheSettingPass.setAccessible(true);
            cacheSettingMaps = new Field[]{
                    config.getClass().getDeclaredField("mapVanillaElements"),
                    config.getClass().getDeclaredField("mapModdedElementsPre"),
                    config.getClass().getDeclaredField("mapModdedElementsPost")
            };
            for (Field cacheSettingMap : cacheSettingMaps) {
                cacheSettingMap.setAccessible(true);
            }
            available = true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            available = false;
            config = null;
            numberOfPasses = null;
            cacheSettingPass = null;
            cacheSettingMaps = null;
        }
    }

    private static final class HudPassOverride {
        private final int configuredPassCount;
        private final Map<Object, Integer> elementPasses = new IdentityHashMap<>();

        private HudPassOverride(int configuredPassCount) {
            this.configuredPassCount = configuredPassCount;
        }
    }
}
