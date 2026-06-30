package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

import java.util.Properties;

public record ShaderRenderSettings(
        boolean oldHandLight,
        boolean dynamicHandLight,
        boolean oldLighting,
        boolean separateAo,
        boolean breaksAnisotropy,
        boolean voxelizeLightBlocks,
        boolean separateEntityDraws,
        float ambientOcclusionLevel,
        boolean supportsColorCorrection,
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
        boolean frustumCulling,
        boolean supportsEndFlash,
        boolean allowConcurrentCompute,
        boolean prepareBeforeShadow,
        boolean shadowCulling,
        boolean shadowCullingReversed,
        Boolean shadowEnabled,
        boolean shadowTerrain,
        boolean shadowTranslucent,
        boolean shadowEntities,
        boolean shadowPlayer,
        boolean shadowBlockEntities,
        boolean shadowLightBlockEntities,
        boolean skipAllRendering,
        int fallbackTex
) {

    public static ShaderRenderSettings defaults() {
        return new ShaderRenderSettings(
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                1.0f,
                false,
                false,
                false,
                true,
                true,
                "default",
                "auto",
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
                false,
                false,
                false,
                true,
                false,
                null,
                true,
                true,
                true,
                false,
                true,
                false,
                false,
                0
        );
    }

    public static ShaderRenderSettings parse(Properties properties) {
        ShaderRenderSettings defaults = defaults();
        return new ShaderRenderSettings(
                booleanProperty(properties, "oldHandLight", defaults.oldHandLight),
                booleanProperty(properties, "dynamicHandLight", defaults.dynamicHandLight),
                booleanProperty(properties, "oldLighting", defaults.oldLighting),
                booleanProperty(properties, "separateAo", defaults.separateAo),
                booleanProperty(properties, "breaksAnisotropy", defaults.breaksAnisotropy),
                booleanProperty(properties, "voxelizeLightBlocks", defaults.voxelizeLightBlocks),
                booleanProperty(properties, "separateEntityDraws", defaults.separateEntityDraws),
                floatProperty(properties, "ambientOcclusionLevel", defaults.ambientOcclusionLevel, 0.0f, 1.0f),
                booleanProperty(properties, "supportsColorCorrection", defaults.supportsColorCorrection),
                booleanProperty(properties, "rain.depth", defaults.rainDepth),
                booleanProperty(properties, "beacon.beam.depth", defaults.beaconBeamDepth),
                weatherGeometry(properties, defaults.weather),
                weatherParticles(properties, defaults.weatherParticles),
                properties.getProperty("clouds", defaults.clouds).trim().toLowerCase(java.util.Locale.ROOT),
                particlesOrdering(properties, defaults.particlesOrdering),
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
                booleanProperty(properties, "frustum.culling", defaults.frustumCulling),
                booleanProperty(properties, "endFlashShadows", defaults.supportsEndFlash),
                booleanProperty(properties, "allowConcurrentCompute", defaults.allowConcurrentCompute),
                booleanProperty(properties, "prepareBeforeShadow", defaults.prepareBeforeShadow),
                shadowCulling(properties, defaults.shadowCulling),
                shadowCullingReversed(properties, defaults.shadowCullingReversed),
                optionalBooleanProperty(properties, "shadow.enabled"),
                booleanProperty(properties, "shadowTerrain", defaults.shadowTerrain),
                booleanProperty(properties, "shadowTranslucent", defaults.shadowTranslucent),
                booleanProperty(properties, "shadowEntities", defaults.shadowEntities),
                booleanProperty(properties, "shadowPlayer", defaults.shadowPlayer),
                booleanProperty(properties, "shadowBlockEntities", defaults.shadowBlockEntities),
                booleanProperty(properties, "shadowLightBlockEntities", defaults.shadowLightBlockEntities),
                booleanProperty(properties, "skipAllRendering", defaults.skipAllRendering),
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

    private static boolean shadowCulling(Properties properties, boolean fallback) {
        String value = properties.getProperty("shadow.culling");
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("reversed".equals(normalized)) {
            return true;
        }
        return Boolean.parseBoolean(normalized);
    }

    private static boolean shadowCullingReversed(Properties properties, boolean fallback) {
        String value = properties.getProperty("shadow.culling");
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return "reversed".equalsIgnoreCase(value.trim());
    }

    private static Boolean optionalBooleanProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static boolean weatherGeometry(Properties properties, boolean fallback) {
        String value = properties.getProperty("weather");
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String[] tokens = value.trim().split("\\s+");
        return Boolean.parseBoolean(tokens[0]);
    }

    private static boolean weatherParticles(Properties properties, boolean fallback) {
        String value = properties.getProperty("weather");
        if (value != null && !value.isBlank()) {
            String[] tokens = value.trim().split("\\s+");
            if (tokens.length > 1) {
                return Boolean.parseBoolean(tokens[1]);
            }
            return Boolean.parseBoolean(tokens[0]);
        }
        return booleanProperty(properties, "weatherParticles", fallback);
    }

    private static String particlesOrdering(Properties properties, String fallback) {
        String ordering = properties.getProperty("particles.ordering");
        if (ordering != null && !ordering.isBlank()) {
            return ordering.trim().toLowerCase(java.util.Locale.ROOT);
        }

        String beforeDeferred = properties.getProperty("particles.before.deferred");
        if (beforeDeferred != null && !beforeDeferred.isBlank()) {
            return Boolean.parseBoolean(beforeDeferred.trim()) ? "before" : "after";
        }

        return fallback;
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
