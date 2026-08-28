package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ClientSettingsConfig {
    private static final String PORTAL_SHADERS_KEY = "portalShaders";
    private static final String AUTOMATIC_SHADER_DISABLING_KEY = "automaticShaderDisabling";
    private static final String UPDATE_CHECKER_KEY = "updateChecker";
    private static final String SHADERLESS_BLOOM_INTENSITY_KEY = "shaderlessBloomIntensity";
    private static final String SHADERED_LOD_1_RADIUS_BLOCKS_KEY = "shaderedLod1RadiusBlocks";
    private static final String SHADERED_LOD_2_RADIUS_BLOCKS_KEY = "shaderedLod2RadiusBlocks";
    private static final String SHADERED_LOD_3_RADIUS_BLOCKS_KEY = "shaderedLod3RadiusBlocks";
    private static final String SHADERED_LOD_4_RADIUS_BLOCKS_KEY = "shaderedLod4RadiusBlocks";
    private static final float DEFAULT_SHADERLESS_BLOOM_INTENSITY = 0.85F;
    private static final int LOD_RADIUS_STEP_BLOCKS = 16;
    private static final int MIN_LOD_RADIUS_BLOCKS = 16;
    private static final int MAX_LOD_RADIUS_BLOCKS = 2048;
    private static final int DEFAULT_LOD_1_RADIUS_BLOCKS = 96;
    private static final int DEFAULT_LOD_2_RADIUS_BLOCKS = 144;
    private static final int DEFAULT_LOD_3_RADIUS_BLOCKS = 192;
    private static final int DEFAULT_LOD_4_RADIUS_BLOCKS = 240;

    private final Path configFile;
    private volatile boolean portalShaders = true;
    private volatile boolean automaticShaderDisabling = true;
    private volatile boolean updateChecker = true;
    private volatile float shaderlessBloomIntensity = DEFAULT_SHADERLESS_BLOOM_INTENSITY;
    private volatile int shaderedLod1RadiusBlocks = DEFAULT_LOD_1_RADIUS_BLOCKS;
    private volatile int shaderedLod2RadiusBlocks = DEFAULT_LOD_2_RADIUS_BLOCKS;
    private volatile int shaderedLod3RadiusBlocks = DEFAULT_LOD_3_RADIUS_BLOCKS;
    private volatile int shaderedLod4RadiusBlocks = DEFAULT_LOD_4_RADIUS_BLOCKS;

    public ClientSettingsConfig(Path minecraftRunDir) {
        this.configFile = minecraftRunDir.resolve("config").resolve("ausm").resolve("client-settings.properties");
    }

    public void load() {
        if (!Files.isRegularFile(configFile)) {
            writeDefaultConfig();
        }

        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(configFile)) {
            properties.load(stream);
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[ClientSettings] Failed to read {}; using defaults", configFile.toAbsolutePath(), e);
        }

        boolean shouldWriteMissingDefaults = !properties.containsKey(PORTAL_SHADERS_KEY)
                || !properties.containsKey(AUTOMATIC_SHADER_DISABLING_KEY)
                || !properties.containsKey(UPDATE_CHECKER_KEY)
                || !properties.containsKey(SHADERLESS_BLOOM_INTENSITY_KEY)
                || !properties.containsKey(SHADERED_LOD_1_RADIUS_BLOCKS_KEY)
                || !properties.containsKey(SHADERED_LOD_2_RADIUS_BLOCKS_KEY)
                || !properties.containsKey(SHADERED_LOD_3_RADIUS_BLOCKS_KEY)
                || !properties.containsKey(SHADERED_LOD_4_RADIUS_BLOCKS_KEY);

        portalShaders = Boolean.parseBoolean(properties.getProperty(PORTAL_SHADERS_KEY, "true").trim());
        automaticShaderDisabling = Boolean.parseBoolean(
                properties.getProperty(AUTOMATIC_SHADER_DISABLING_KEY, "true").trim()
        );
        updateChecker = Boolean.parseBoolean(properties.getProperty(UPDATE_CHECKER_KEY, "true").trim());
        shaderlessBloomIntensity = parseFloat(
                properties,
                SHADERLESS_BLOOM_INTENSITY_KEY,
                DEFAULT_SHADERLESS_BLOOM_INTENSITY,
                0.0F,
                3.0F
        );
        shaderedLod1RadiusBlocks = parseLodRadius(properties, SHADERED_LOD_1_RADIUS_BLOCKS_KEY, DEFAULT_LOD_1_RADIUS_BLOCKS);
        shaderedLod2RadiusBlocks = parseLodRadius(properties, SHADERED_LOD_2_RADIUS_BLOCKS_KEY, DEFAULT_LOD_2_RADIUS_BLOCKS);
        shaderedLod3RadiusBlocks = parseLodRadius(properties, SHADERED_LOD_3_RADIUS_BLOCKS_KEY, DEFAULT_LOD_3_RADIUS_BLOCKS);
        shaderedLod4RadiusBlocks = parseLodRadius(properties, SHADERED_LOD_4_RADIUS_BLOCKS_KEY, DEFAULT_LOD_4_RADIUS_BLOCKS);
        normalizeLodRadii();
        MainMod.LOGGER.info(
                "[ClientSettings] Loaded config: portalShaders={} automaticShaderDisabling={} updateChecker={} shaderlessBloomIntensity={} shaderedLodRadii=[{}, {}, {}, {}]",
                portalShaders,
                automaticShaderDisabling,
                updateChecker,
                shaderlessBloomIntensity,
                shaderedLod1RadiusBlocks,
                shaderedLod2RadiusBlocks,
                shaderedLod3RadiusBlocks,
                shaderedLod4RadiusBlocks
        );
        if (shouldWriteMissingDefaults) {
            save();
        }
    }

    public boolean portalShadersEnabled() {
        return portalShaders;
    }

    public void setPortalShadersEnabled(boolean enabled) {
        if (portalShaders == enabled) {
            return;
        }
        portalShaders = enabled;
        save();
    }

    public boolean automaticShaderDisablingEnabled() {
        return automaticShaderDisabling;
    }

    public void setAutomaticShaderDisablingEnabled(boolean enabled) {
        if (automaticShaderDisabling == enabled) {
            return;
        }
        automaticShaderDisabling = enabled;
        save();
    }

    public boolean updateCheckerEnabled() {
        return updateChecker;
    }

    public void setUpdateCheckerEnabled(boolean enabled) {
        if (updateChecker == enabled) {
            return;
        }
        updateChecker = enabled;
        save();
    }

    public float shaderlessBloomIntensity() {
        return shaderlessBloomIntensity;
    }

    public void setShaderlessBloomIntensity(float value) {
        float clamped = clamp(value, 0.0F, 3.0F);
        if (Float.compare(shaderlessBloomIntensity, clamped) == 0) {
            return;
        }
        shaderlessBloomIntensity = clamped;
        save();
    }

    public Path configFile() {
        return configFile;
    }

    public int shaderedLod1RadiusBlocks() {
        return shaderedLod1RadiusBlocks;
    }

    public int shaderedLod2RadiusBlocks() {
        return shaderedLod2RadiusBlocks;
    }

    public int shaderedLod3RadiusBlocks() {
        return shaderedLod3RadiusBlocks;
    }

    public int shaderedLod4RadiusBlocks() {
        return shaderedLod4RadiusBlocks;
    }

    public void setShaderedLod1RadiusBlocks(int radiusBlocks) {
        setShaderedLodRadiusBlocks(0, radiusBlocks);
    }

    public void setShaderedLod2RadiusBlocks(int radiusBlocks) {
        setShaderedLodRadiusBlocks(1, radiusBlocks);
    }

    public void setShaderedLod3RadiusBlocks(int radiusBlocks) {
        setShaderedLodRadiusBlocks(2, radiusBlocks);
    }

    public void setShaderedLod4RadiusBlocks(int radiusBlocks) {
        setShaderedLodRadiusBlocks(3, radiusBlocks);
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty(PORTAL_SHADERS_KEY, Boolean.toString(portalShaders));
        properties.setProperty(AUTOMATIC_SHADER_DISABLING_KEY, Boolean.toString(automaticShaderDisabling));
        properties.setProperty(UPDATE_CHECKER_KEY, Boolean.toString(updateChecker));
        properties.setProperty(SHADERLESS_BLOOM_INTENSITY_KEY, Float.toString(shaderlessBloomIntensity));
        properties.setProperty(SHADERED_LOD_1_RADIUS_BLOCKS_KEY, Integer.toString(shaderedLod1RadiusBlocks));
        properties.setProperty(SHADERED_LOD_2_RADIUS_BLOCKS_KEY, Integer.toString(shaderedLod2RadiusBlocks));
        properties.setProperty(SHADERED_LOD_3_RADIUS_BLOCKS_KEY, Integer.toString(shaderedLod3RadiusBlocks));
        properties.setProperty(SHADERED_LOD_4_RADIUS_BLOCKS_KEY, Integer.toString(shaderedLod4RadiusBlocks));

        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream stream = Files.newOutputStream(configFile)) {
                properties.store(stream, "AUSM client settings");
            }
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[ClientSettings] Failed to save {}", configFile.toAbsolutePath(), e);
        }
    }

    private void writeDefaultConfig() {
        try {
            Files.createDirectories(configFile.getParent());
            String text = """
                    # AUSM client settings.
                    # If false, Better Portals child views render with the vanilla/shaderless renderer.
                    portalShaders=true
                    # If false, a shaderpack that was enabled on shutdown is restored on the next world load.
                    automaticShaderDisabling=true
                    # Check GitHub releases after loading a world and notify only when a newer AUSM version exists.
                    updateChecker=true
                    # Shaderless emissive bloom multiplier.
                    shaderlessBloomIntensity=0.85
                    # Shadered quality LOD boundaries in blocks. Foliage waving stops at LOD 2.
                    shaderedLod1RadiusBlocks=96
                    shaderedLod2RadiusBlocks=144
                    shaderedLod3RadiusBlocks=192
                    shaderedLod4RadiusBlocks=240
                    """;
            Files.writeString(configFile, text);
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[ClientSettings] Failed to create default config {}", configFile.toAbsolutePath(), e);
        }
    }

    private float parseFloat(Properties properties, String key, float fallback, float min, float max) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return clamp(Float.parseFloat(raw.trim()), min, max);
        } catch (NumberFormatException e) {
            MainMod.LOGGER.warn("[ClientSettings] Invalid float for {}='{}'; using {}", key, raw, fallback);
            return fallback;
        }
    }

    private int parseLodRadius(Properties properties, String key, int fallback) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return snapLodRadius(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            MainMod.LOGGER.warn("[ClientSettings] Invalid LOD radius for {}='{}'; using {}", key, raw, fallback);
            return fallback;
        }
    }

    private void setShaderedLodRadiusBlocks(int lodIndex, int radiusBlocks) {
        int[] radii = lodRadii();
        int minimum = lodIndex == 0 ? MIN_LOD_RADIUS_BLOCKS : radii[lodIndex - 1] + LOD_RADIUS_STEP_BLOCKS;
        int maximum = lodIndex == radii.length - 1
                ? MAX_LOD_RADIUS_BLOCKS
                : radii[lodIndex + 1] - LOD_RADIUS_STEP_BLOCKS;
        int clamped = Math.clamp(snapLodRadius(radiusBlocks), minimum, maximum);
        if (radii[lodIndex] == clamped) {
            return;
        }
        radii[lodIndex] = clamped;
        assignLodRadii(radii);
        save();
    }

    private void normalizeLodRadii() {
        int[] radii = lodRadii();
        for (int index = 0; index < radii.length; index++) {
            int minimum = MIN_LOD_RADIUS_BLOCKS + index * LOD_RADIUS_STEP_BLOCKS;
            int maximum = MAX_LOD_RADIUS_BLOCKS - (radii.length - index - 1) * LOD_RADIUS_STEP_BLOCKS;
            radii[index] = Math.clamp(snapLodRadius(radii[index]), minimum, maximum);
            if (index > 0) {
                radii[index] = Math.max(radii[index], radii[index - 1] + LOD_RADIUS_STEP_BLOCKS);
            }
        }
        assignLodRadii(radii);
    }

    private int[] lodRadii() {
        return new int[]{
                shaderedLod1RadiusBlocks,
                shaderedLod2RadiusBlocks,
                shaderedLod3RadiusBlocks,
                shaderedLod4RadiusBlocks
        };
    }

    private void assignLodRadii(int[] radii) {
        shaderedLod1RadiusBlocks = radii[0];
        shaderedLod2RadiusBlocks = radii[1];
        shaderedLod3RadiusBlocks = radii[2];
        shaderedLod4RadiusBlocks = radii[3];
    }

    private static int snapLodRadius(int value) {
        int clamped = Math.clamp(value, MIN_LOD_RADIUS_BLOCKS, MAX_LOD_RADIUS_BLOCKS);
        return Math.round(clamped / (float) LOD_RADIUS_STEP_BLOCKS) * LOD_RADIUS_STEP_BLOCKS;
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.clamp(value, min, max);
    }
}
