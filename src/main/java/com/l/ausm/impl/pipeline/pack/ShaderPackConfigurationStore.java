package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

final class ShaderPackConfigurationStore {
    private final Path optionOverridesDirectory;
    private final Path shaderConfigFile;

    ShaderPackConfigurationStore(Path configDirectory) {
        optionOverridesDirectory = configDirectory.resolve("shader-options");
        shaderConfigFile = configDirectory.resolve("shaders.properties");
        try {
            Files.createDirectories(optionOverridesDirectory);
        } catch (IOException exception) {
            MainMod.LOGGER.error("Failed to create shader option config directory!", exception);
        }
    }

    SavedShaderConfiguration load(String offPackName) {
        Properties properties = new Properties();
        if (Files.isRegularFile(shaderConfigFile)) {
            try (InputStream stream = Files.newInputStream(shaderConfigFile)) {
                properties.load(stream);
            } catch (IOException exception) {
                MainMod.LOGGER.error("Failed to read shader configuration", exception);
            }
        }
        return new SavedShaderConfiguration(
                properties.getProperty("selectedPack", offPackName),
                Boolean.parseBoolean(properties.getProperty("enabled", "false").trim()));
    }

    void save(String selectedPackName, boolean enabled) {
        Properties properties = new Properties();
        properties.setProperty("selectedPack", selectedPackName);
        properties.setProperty("enabled", Boolean.toString(enabled));
        try {
            Files.createDirectories(shaderConfigFile.getParent());
            try (OutputStream stream = Files.newOutputStream(shaderConfigFile)) {
                properties.store(stream, "AUSM shader configuration");
            }
        } catch (IOException exception) {
            MainMod.LOGGER.error("Failed to save shader configuration", exception);
        }
    }

    Map<String, String> loadOptions(String packName, String internalPackName) {
        if (packName == null || internalPackName.equals(packName)) {
            return Map.of();
        }
        Path file = optionFile(packName);
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }

        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(file)) {
            properties.load(stream);
        } catch (IOException exception) {
            MainMod.LOGGER.error("Failed to read shader options for '{}'", packName, exception);
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            values.put(key, properties.getProperty(key));
        }
        return values;
    }

    void saveOptions(String packName, Map<String, String> values) {
        Properties properties = new Properties();
        values.forEach(properties::setProperty);
        try {
            Files.createDirectories(optionOverridesDirectory);
            try (OutputStream stream = Files.newOutputStream(optionFile(packName))) {
                properties.store(stream, "AUSM shader option overrides");
            }
        } catch (IOException exception) {
            MainMod.LOGGER.error("Failed to save shader options for '{}'", packName, exception);
        }
    }

    void resetOptions(String packName) {
        try {
            Files.deleteIfExists(optionFile(packName));
        } catch (IOException exception) {
            MainMod.LOGGER.error("Failed to reset shader options for '{}'", packName, exception);
        }
    }

    private Path optionFile(String packName) {
        String safeName = packName.replaceAll("[^A-Za-z0-9._-]", "_");
        return optionOverridesDirectory.resolve(safeName + ".properties");
    }
}

record SavedShaderConfiguration(String selectedPackName, boolean enabled) {
}
