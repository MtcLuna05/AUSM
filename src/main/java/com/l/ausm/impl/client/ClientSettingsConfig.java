package com.l.ausm.impl.client;

import com.l.ausm.impl.MainMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ClientSettingsConfig {
    private static final String PORTAL_SHADERS_KEY = "portalShaders";
    private static final String SHADERLESS_BLOOM_CHROMA_KEY = "shaderlessBloomChroma";
    private static final String SHADERLESS_BLOOM_INTENSITY_KEY = "shaderlessBloomIntensity";
    private static final float DEFAULT_SHADERLESS_BLOOM_CHROMA = 1.0F;
    private static final float DEFAULT_SHADERLESS_BLOOM_INTENSITY = 0.85F;

    private final Path configFile;
    private volatile boolean portalShaders = true;
    private volatile float shaderlessBloomChroma = DEFAULT_SHADERLESS_BLOOM_CHROMA;
    private volatile float shaderlessBloomIntensity = DEFAULT_SHADERLESS_BLOOM_INTENSITY;

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
                || !properties.containsKey(SHADERLESS_BLOOM_CHROMA_KEY)
                || !properties.containsKey(SHADERLESS_BLOOM_INTENSITY_KEY);

        portalShaders = Boolean.parseBoolean(properties.getProperty(PORTAL_SHADERS_KEY, "true").trim());
        shaderlessBloomChroma = parseFloat(
                properties,
                SHADERLESS_BLOOM_CHROMA_KEY,
                DEFAULT_SHADERLESS_BLOOM_CHROMA,
                0.0F,
                2.0F
        );
        shaderlessBloomIntensity = parseFloat(
                properties,
                SHADERLESS_BLOOM_INTENSITY_KEY,
                DEFAULT_SHADERLESS_BLOOM_INTENSITY,
                0.0F,
                3.0F
        );
        MainMod.LOGGER.info(
                "[ClientSettings] Loaded config: portalShaders={} shaderlessBloomChroma={} shaderlessBloomIntensity={}",
                portalShaders,
                shaderlessBloomChroma,
                shaderlessBloomIntensity
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

    public float shaderlessBloomChroma() {
        return shaderlessBloomChroma;
    }

    public void setShaderlessBloomChroma(float value) {
        float clamped = clamp(value, 0.0F, 2.0F);
        if (Float.compare(shaderlessBloomChroma, clamped) == 0) {
            return;
        }
        shaderlessBloomChroma = clamped;
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

    private void save() {
        Properties properties = new Properties();
        properties.setProperty(PORTAL_SHADERS_KEY, Boolean.toString(portalShaders));
        properties.setProperty(SHADERLESS_BLOOM_CHROMA_KEY, Float.toString(shaderlessBloomChroma));
        properties.setProperty(SHADERLESS_BLOOM_INTENSITY_KEY, Float.toString(shaderlessBloomIntensity));

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
                    # Shaderless emissive bloom color saturation. 1.0 keeps the dye color; 0.0 is grayscale; values above 1.0 oversaturate.
                    shaderlessBloomChroma=1.0
                    # Shaderless emissive bloom multiplier. 0.85 keeps pale dyes from clamping to white; increase for brighter bloom.
                    shaderlessBloomIntensity=0.85
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

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
