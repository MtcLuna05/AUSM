package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

import java.util.Properties;

public record ShaderRenderSettings(
        boolean oldHandLight,
        boolean oldLighting,
        boolean separateAo,
        float ambientOcclusionLevel,
        boolean rainDepth,
        boolean beaconBeamDepth,
        boolean weather,
        boolean weatherParticles,
        String clouds,
        String particlesOrdering,
        boolean occlusionCulling,
        boolean underwaterOverlay,
        boolean vignette,
        boolean sun,
        boolean moon,
        boolean stars,
        boolean sky,
        boolean backFaceSolid,
        boolean backFaceCutout,
        boolean backFaceCutoutMipped,
        boolean backFaceTranslucent,
        boolean shadowTerrain,
        boolean shadowTranslucent,
        boolean shadowEntities,
        boolean shadowPlayer,
        boolean shadowBlockEntities,
        boolean shadowLightBlockEntities,
        int fallbackTex
) {

    public static ShaderRenderSettings defaults() {
        return new ShaderRenderSettings(
                true,
                false,
                false,
                1.0f,
                true,
                true,
                true,
                true,
                "default",
                "mixed",
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                false,
                true,
                false,
                0
        );
    }

    public static ShaderRenderSettings parse(Properties properties) {
        ShaderRenderSettings defaults = defaults();
        return new ShaderRenderSettings(
                booleanProperty(properties, "oldHandLight", defaults.oldHandLight),
                booleanProperty(properties, "oldLighting", defaults.oldLighting),
                booleanProperty(properties, "separateAo", defaults.separateAo),
                floatProperty(properties, "ambientOcclusionLevel", defaults.ambientOcclusionLevel, 0.0f, 1.0f),
                booleanProperty(properties, "rain.depth", defaults.rainDepth),
                booleanProperty(properties, "beacon.beam.depth", defaults.beaconBeamDepth),
                booleanProperty(properties, "weather", defaults.weather),
                booleanProperty(properties, "weatherParticles", defaults.weatherParticles),
                properties.getProperty("clouds", defaults.clouds).trim().toLowerCase(java.util.Locale.ROOT),
                properties.getProperty("particles.ordering", defaults.particlesOrdering).trim(),
                booleanProperty(properties, "occlusion.culling", defaults.occlusionCulling),
                booleanProperty(properties, "underwaterOverlay", defaults.underwaterOverlay),
                booleanProperty(properties, "vignette", defaults.vignette),
                booleanProperty(properties, "sun", defaults.sun),
                booleanProperty(properties, "moon", defaults.moon),
                booleanProperty(properties, "stars", defaults.stars),
                booleanProperty(properties, "sky", defaults.sky),
                booleanProperty(properties, "backFace.solid", defaults.backFaceSolid),
                booleanProperty(properties, "backFace.cutout", defaults.backFaceCutout),
                booleanProperty(properties, "backFace.cutoutMipped", defaults.backFaceCutoutMipped),
                booleanProperty(properties, "backFace.translucent", defaults.backFaceTranslucent),
                booleanProperty(properties, "shadowTerrain", defaults.shadowTerrain),
                booleanProperty(properties, "shadowTranslucent", defaults.shadowTranslucent),
                booleanProperty(properties, "shadowEntities", defaults.shadowEntities),
                booleanProperty(properties, "shadowPlayer", defaults.shadowPlayer),
                booleanProperty(properties, "shadowBlockEntities", defaults.shadowBlockEntities),
                booleanProperty(properties, "shadowLightBlockEntities", defaults.shadowLightBlockEntities),
                intProperty(properties, "fallbackTex", defaults.fallbackTex)
        );
    }

    private static boolean booleanProperty(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static float floatProperty(Properties properties, String key, float fallback, float min, float max) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            float parsed = Float.parseFloat(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int intProperty(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
