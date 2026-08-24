package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

/**
 * Classifies emissive block states without coupling that policy to render lifecycle state.
 */
final class PipelineBlockEmission {

    static int intrinsicEmission(IBlockState state) {
        try {
            return clampLightValue(MinecraftReflectionCompat.stateLightValue(state));
        } catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    static int astralCrystalEmission(IBlockState state) {
        if (!isAstralCrystalCluster(state)) {
            return 0;
        }
        String path = MinecraftReflectionCompat.resourcePath(registryName(state));
        if ("blockcelestialcrystals".equalsIgnoreCase(path)) {
            return clampLightValue(6 + Math.clamp(parseIntProperty(state, "stage", 2), 0, 4));
        }
        if ("blockgemcrystals".equalsIgnoreCase(path)) {
            String stage = propertyValue(state, "stage");
            if ("stage_2_day".equalsIgnoreCase(stage) || "stage_2_night".equalsIgnoreCase(stage)
                    || "stage_2_sky".equalsIgnoreCase(stage)) {
                return 10;
            }
            return "stage_1".equalsIgnoreCase(stage) ? 8 : 6;
        }
        return 0;
    }

    static boolean isAstralCrystalCluster(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null || !"astralsorcery".equals(MinecraftReflectionCompat.resourceNamespace(name))) {
            return false;
        }
        String path = MinecraftReflectionCompat.resourcePath(name);
        return "blockcelestialcrystals".equalsIgnoreCase(path) || "blockgemcrystals".equalsIgnoreCase(path);
    }

    static int astralCrystalMaterialId(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null || !"astralsorcery".equals(MinecraftReflectionCompat.resourceNamespace(name))) {
            return 0;
        }
        String path = MinecraftReflectionCompat.resourcePath(name);
        if ("blockcelestialcrystals".equalsIgnoreCase(path)) {
            return 10914;
        }
        if (!"blockgemcrystals".equalsIgnoreCase(path)) {
            return 0;
        }
        String stage = propertyValue(state, "stage");
        if ("stage_2_day".equalsIgnoreCase(stage)) {
            return 10904;
        }
        return "stage_2_night".equalsIgnoreCase(stage) ? 10916 : 10912;
    }

    static boolean containsIgnoreCase(String value, String needle) {
        if (value == null || needle == null) {
            return false;
        }
        int max = value.length() - needle.length();
        for (int i = 0; i <= max; i++) {
            if (value.regionMatches(true, i, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation registryName(IBlockState state) {
        Block block = state != null ? MinecraftReflectionCompat.blockFromState(state) : null;
        return block != null ? MinecraftReflectionCompat.blockRegistryName(block) : null;
    }

    private static int parseIntProperty(IBlockState state, String propertyName, int fallback) {
        String value = propertyValue(state, propertyName);
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValue(IBlockState state, String propertyName) {
        if (state == null || propertyName == null) {
            return null;
        }
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : MinecraftReflectionCompat.stateProperties(state).entrySet()) {
            IProperty property = entry.getKey();
            if (property != null && propertyName.equals(MinecraftReflectionCompat.propertyName(property))) {
                return MinecraftReflectionCompat.propertyValueName(property, entry.getValue());
            }
        }
        return null;
    }

    private static int clampLightValue(int value) {
        return Math.clamp(value, 0, 15);
    }
}
