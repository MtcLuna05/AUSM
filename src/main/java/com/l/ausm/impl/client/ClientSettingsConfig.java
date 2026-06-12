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

    private final Path configFile;
    private volatile boolean portalShaders = true;

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

        portalShaders = Boolean.parseBoolean(properties.getProperty(PORTAL_SHADERS_KEY, "true").trim());
        MainMod.LOGGER.info("[ClientSettings] Loaded config: portalShaders={}", portalShaders);
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

    public Path configFile() {
        return configFile;
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty(PORTAL_SHADERS_KEY, Boolean.toString(portalShaders));

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
                    """;
            Files.writeString(configFile, text);
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[ClientSettings] Failed to create default config {}", configFile.toAbsolutePath(), e);
        }
    }
}
