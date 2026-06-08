package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.shader.ShaderPackController;
import com.l.ausm.api.shader.ShaderPackInfo;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public class ShaderPackManager implements ShaderPackController {
    private final Path shaderpacksDir;
    private final Path optionOverridesDir;
    private final Path shaderConfigFile;
    private ShaderPack currentPack = NoneShaderPack.INSTANCE;
    private Map<String, String> currentOptionOverrides = Map.of();
    private final Map<ShaderPropertiesCacheKey, ShaderProperties> shaderPropertiesCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ShaderPropertiesCacheKey, ShaderProperties> eldest) {
            return size() > 24;
        }
    };
    private String selectedPackName = "OFF";
    private boolean shadersEnabled = false;
    private boolean pendingPipelineReload = false;
    private int compiledDimensionId = Integer.MIN_VALUE;
    private String compiledPackName = "OFF";

    public ShaderPackManager(Path minecraftRunDir) {
        this.shaderpacksDir = minecraftRunDir.resolve("shaderpacks");
        Path configDir = minecraftRunDir.resolve("config").resolve("ausm");
        this.optionOverridesDir = configDir.resolve("shader-options");
        this.shaderConfigFile = configDir.resolve("shaders.properties");
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        if (!Files.exists(shaderpacksDir)) {
            try {
                MainMod.LOGGER.info("Shaderpacks directory not found, creating at: {}", shaderpacksDir.toAbsolutePath());
                Files.createDirectories(shaderpacksDir);
                Files.createDirectories(optionOverridesDir);
            } catch (IOException e) {
                MainMod.LOGGER.error("Failed to create shaderpacks directory!", e);
            }
        }
        try {
            Files.createDirectories(optionOverridesDir);
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to create shader option config directory!", e);
        }
    }

    /**
     * Loads a shader pack by its folder/zip name.
     */
    public boolean loadPack(String packName) {
        if (packName == null || packName.isEmpty() || packName.equalsIgnoreCase("OFF")) {
            MainMod.LOGGER.info("Disabling shaderpack.");
            selectedPackName = "OFF";
            shadersEnabled = false;
            saveShaderConfig();
            setPack(NoneShaderPack.INSTANCE);
            return true;
        }

        ShaderPack newPack = openPack(packName);
        if (newPack == null) {
            fallbackToOff("Selected shaderpack '{}' is no longer available; disabling shaders.", packName);
            return false;
        }

        selectedPackName = packName;
        shadersEnabled = true;
        saveShaderConfig();
        setPack(newPack);
        MainMod.LOGGER.info("Successfully loaded shaderpack: {}", newPack.getName());
        return true;
    }

    public void loadSavedConfiguration() {
        Properties properties = new Properties();
        if (Files.isRegularFile(shaderConfigFile)) {
            try (InputStream stream = Files.newInputStream(shaderConfigFile)) {
                properties.load(stream);
            } catch (IOException e) {
                MainMod.LOGGER.error("Failed to read shader configuration", e);
            }
        }

        selectedPackName = properties.getProperty("selectedPack", "OFF");
        shadersEnabled = Boolean.parseBoolean(properties.getProperty("enabled", !selectedPackName.equals("OFF") ? "true" : "false"));

        if (selectedPackName.equalsIgnoreCase("OFF")) {
            setPack(NoneShaderPack.INSTANCE);
            return;
        }

        ShaderPack newPack = openPack(selectedPackName);
        if (newPack == null) {
            fallbackToOff("Saved shaderpack '{}' is no longer available; disabling shaders.", selectedPackName);
            return;
        }

        setPack(newPack);
        PipelineContext.getInstance().setActive(shadersEnabled);
    }

    private ShaderPack openPack(String packName) {
        Path packPath = shaderpacksDir.resolve(packName);
        if (!Files.exists(packPath)) {
            MainMod.LOGGER.warn("Attempted to load shaderpack '{}', but it does not exist at '{}'", packName, packPath.toAbsolutePath());
            return null;
        }

        try {
            if (Files.isDirectory(packPath)) {
                MainMod.LOGGER.info("Loading folder shaderpack: {}", packName);
                return new FolderShaderPack(packPath);
            }
            if (packName.endsWith(".zip")) {
                MainMod.LOGGER.info("Loading zip shaderpack: {}", packName);
                return new ZipShaderPack(packPath);
            }

            MainMod.LOGGER.warn("Cannot load shaderpack '{}' because it is neither a folder nor a zip file.", packName);
            return null;
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to load shaderpack '{}'", packName, e);
            return null;
        }
    }

    private void setPack(ShaderPack newPack) {
        if (newPack == null) {
            newPack = NoneShaderPack.INSTANCE;
        }

        try {
            if (this.currentPack != null) {
                this.currentPack.close();
            }
        } catch (IOException e) {
            String previousName = this.currentPack != null ? this.currentPack.getName() : "<none>";
            MainMod.LOGGER.error("Failed to close previous shaderpack '{}'", previousName, e);
        }
        this.currentPack = newPack;
        this.currentOptionOverrides = loadOptionOverrides(newPack.getName());
        clearShaderPropertiesCacheExcept(newPack.getName());

        if (!shadersEnabled && !newPack.getName().equals("(internal)")) {
            PipelineContext.getInstance().cleanup();
            this.compiledDimensionId = Integer.MIN_VALUE;
            this.compiledPackName = "OFF";
            this.pendingPipelineReload = true;
            rebuildInactiveVanillaRenderers();
            return;
        }
        
        // Notify the pipeline to reload and compile shaders
        ShaderProperties properties = getShaderProperties(newPack.getName(), currentOptionOverrides);
        PipelineContext.getInstance().initialize(this.currentPack, this.currentOptionOverrides, properties);
        this.compiledDimensionId = getClientDimensionId();
        this.compiledPackName = newPack.getName();
        this.pendingPipelineReload = false;
        PipelineContext.getInstance().setActive(shadersEnabled && !selectedPackName.equals("OFF"));
    }

    public ShaderPack getCurrentPack() {
        return currentPack;
    }

    public Path getShaderpacksDir() {
        return shaderpacksDir;
    }

    public String importShaderPack(Path source) throws IOException {
        if (source == null || !isValidPackPath(source)) {
            return null;
        }

        Files.createDirectories(shaderpacksDir);
        String name = source.getFileName().toString();
        Path target = shaderpacksDir.resolve(name);
        if (Files.exists(target)) {
            if (Files.isSameFile(source, target)) {
                return name;
            }
            throw new FileAlreadyExistsException(target.toString());
        }

        if (Files.isDirectory(source)) {
            copyDirectory(source, target);
        } else {
            Files.copy(source, target);
        }
        return name;
    }

    @Override
    public ShaderPackInfo getCurrentShaderPack() {
        String name = selectedPackName == null ? "OFF" : selectedPackName;
        return new ShaderPackInfo(name, areShadersEnabled(), name.equalsIgnoreCase("OFF") || isPackAvailable(name));
    }

    public String getSelectedPackName() {
        return selectedPackName;
    }

    public boolean areShadersEnabled() {
        return shadersEnabled && !selectedPackName.equals("OFF");
    }

    /**
     * Lists available packs in the shaderpacks folder.
     */
    public List<String> getAvailablePacks() {
        List<String> packs = new ArrayList<>();
        packs.add("OFF");

        if (!Files.exists(shaderpacksDir)) {
            return packs;
        }

        try (Stream<Path> stream = Files.list(shaderpacksDir)) {
            stream.forEach(path -> {
                if (isValidPackPath(path)) {
                    packs.add(path.getFileName().toString());
                }
            });
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to list available shaderpacks!", e);
        }
        packs.subList(1, packs.size()).sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        return packs;
    }

    public void reloadPack() {
        if (selectedPackName == null || selectedPackName.equals("OFF")) {
            setPack(NoneShaderPack.INSTANCE);
            return;
        }

        boolean wasEnabled = shadersEnabled;
        ShaderPack newPack = openPack(selectedPackName);
        if (newPack == null) {
            fallbackToOff("Selected shaderpack '{}' disappeared during reload; disabling shaders.", selectedPackName);
            return;
        }
        setPack(newPack);
        shadersEnabled = wasEnabled;
        pendingPipelineReload = false;
        PipelineContext.getInstance().setActive(shadersEnabled);
    }

    public void reloadIfDimensionChanged() {
        if (!areShadersEnabled() || selectedPackName == null || selectedPackName.equals("OFF")) {
            return;
        }

        if (!isPackAvailable(selectedPackName)) {
            fallbackToOff("Selected shaderpack '{}' disappeared before world rendering; disabling shaders.", selectedPackName);
            return;
        }

        int currentDimensionId = getClientDimensionId();
        if (currentDimensionId == Integer.MIN_VALUE || currentDimensionId == compiledDimensionId) {
            return;
        }

        if (!hasDimensionSpecificResources(currentDimensionId)) {
            MainMod.LOGGER.debug(
                    "Shader dimension changed from {} to {}, but shaderpack '{}' has no dimension-specific resources; keeping compiled pipeline.",
                    compiledDimensionId,
                    currentDimensionId,
                    selectedPackName
            );
            compiledDimensionId = currentDimensionId;
            return;
        }

        MainMod.LOGGER.info("Shader dimension changed from {} to {}; recompiling shaderpack '{}'",
                compiledDimensionId, currentDimensionId, selectedPackName);
        reloadPack();
    }

    public void setShadersEnabled(boolean enabled) {
        if (selectedPackName == null || selectedPackName.equals("OFF")) {
            shadersEnabled = false;
        } else if (enabled && !isPackAvailable(selectedPackName)) {
            fallbackToOff("Selected shaderpack '{}' is no longer available; disabling shaders.", selectedPackName);
            return;
        } else {
            shadersEnabled = enabled;
        }
        saveShaderConfig();
        if (shadersEnabled && pendingPipelineReload) {
            ShaderProperties properties = getShaderProperties(currentPack.getName(), currentOptionOverrides);
            PipelineContext.getInstance().initialize(currentPack, currentOptionOverrides, properties);
            compiledDimensionId = getClientDimensionId();
            compiledPackName = currentPack.getName();
            pendingPipelineReload = false;
        }
        if (shadersEnabled) {
            PipelineContext.getInstance().setActive(true);
        } else {
            PipelineContext.getInstance().cleanup();
            compiledDimensionId = Integer.MIN_VALUE;
            compiledPackName = "OFF";
            pendingPipelineReload = currentPack != null && !currentPack.getName().equals("(internal)");
            rebuildInactiveVanillaRenderers();
        }
    }

    public Map<String, String> getCurrentOptionOverrides() {
        return currentOptionOverrides;
    }

    public Map<String, String> getOptionOverrides(String packName) {
        return loadOptionOverrides(packName);
    }

    public ShaderProperties getShaderProperties(String packName) {
        return getShaderProperties(packName, getOptionOverrides(packName));
    }

    public ShaderProperties getShaderProperties(String packName, Map<String, String> overrides) {
        if (packName == null || packName.isBlank() || packName.equalsIgnoreCase("OFF")) {
            return ShaderProperties.load(NoneShaderPack.INSTANCE, Map.of());
        }

        ShaderPropertiesCacheKey cacheKey = new ShaderPropertiesCacheKey(packName, Map.copyOf(overrides));
        ShaderProperties cached = shaderPropertiesCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean useCurrentPack = isCurrentPack(packName) && currentPack != null && !currentPack.getName().equals("(internal)");
        ShaderPack pack = useCurrentPack ? currentPack : openPack(packName);
        if (pack == null) {
            return ShaderProperties.load(NoneShaderPack.INSTANCE, Map.of());
        }

        try {
            ShaderProperties properties = ShaderProperties.load(pack, overrides);
            shaderPropertiesCache.put(cacheKey, properties);
            return properties;
        } finally {
            if (!useCurrentPack) {
                try {
                    pack.close();
                } catch (IOException e) {
                    MainMod.LOGGER.error("Failed to close inspected shaderpack '{}'", packName, e);
                }
            }
        }
    }

    public void setShaderOption(String name, String value) {
        Map<String, String> values = new LinkedHashMap<>(currentOptionOverrides);
        values.put(name, value);
        setShaderOptions(values);
    }

    public void setShaderOptions(Map<String, String> values) {
        if (currentPack == null || currentPack.getName().equals("(internal)")) {
            return;
        }

        setShaderOptions(currentPack.getName(), values);
    }

    public void setShaderOptions(String packName, Map<String, String> values) {
        if (packName == null || packName.isBlank() || packName.equalsIgnoreCase("OFF")) {
            return;
        }

        Map<String, String> copy = new LinkedHashMap<>(values);
        boolean currentPackTarget = isCurrentPack(packName);
        if (currentPackTarget && currentOptionOverrides.equals(copy)) {
            return;
        }

        ShaderProperties properties = getShaderProperties(packName, copy);
        saveOptionOverrides(packName, copy);
        if (currentPackTarget) {
            currentOptionOverrides = copy;
            if (!shadersEnabled) {
                pendingPipelineReload = true;
                PipelineContext.getInstance().cleanup();
                rebuildInactiveVanillaRenderers();
                return;
            }
            PipelineContext.getInstance().initialize(currentPack, currentOptionOverrides, properties);
            compiledDimensionId = getClientDimensionId();
            compiledPackName = currentPack.getName();
            pendingPipelineReload = false;
            PipelineContext.getInstance().setActive(true);
        }
    }

    public void resetShaderOptions() {
        if (currentPack == null || currentPack.getName().equals("(internal)")) {
            return;
        }

        resetShaderOptions(currentPack.getName());
    }

    public void resetShaderOptions(String packName) {
        if (packName == null || packName.isBlank() || packName.equalsIgnoreCase("OFF")) {
            return;
        }

        try {
            Files.deleteIfExists(optionFile(packName));
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to reset shader options for '{}'", packName, e);
        }
        if (isCurrentPack(packName)) {
            if (currentOptionOverrides.isEmpty()) {
                return;
            }
            currentOptionOverrides = Map.of();
            if (!shadersEnabled) {
                pendingPipelineReload = true;
                PipelineContext.getInstance().cleanup();
                rebuildInactiveVanillaRenderers();
                return;
            }
            ShaderProperties properties = getShaderProperties(currentPack.getName(), currentOptionOverrides);
            PipelineContext.getInstance().initialize(currentPack, currentOptionOverrides, properties);
            compiledDimensionId = getClientDimensionId();
            compiledPackName = currentPack.getName();
            pendingPipelineReload = false;
            PipelineContext.getInstance().setActive(true);
        }
    }

    private void rebuildInactiveVanillaRenderers() {
        PipelineContext.getInstance().setActive(false);
    }

    private Map<String, String> loadOptionOverrides(String packName) {
        if (packName == null || packName.equals("(internal)")) {
            return Map.of();
        }

        Path file = optionFile(packName);
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }

        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(file)) {
            properties.load(stream);
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to read shader options for '{}'", packName, e);
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            values.put(key, properties.getProperty(key));
        }
        return values;
    }

    private void saveOptionOverrides(String packName, Map<String, String> values) {
        Properties properties = new Properties();
        values.forEach(properties::setProperty);

        try {
            Files.createDirectories(optionOverridesDir);
            try (OutputStream stream = Files.newOutputStream(optionFile(packName))) {
                properties.store(stream, "AUSM shader option overrides");
            }
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to save shader options for '{}'", packName, e);
        }
    }

    private Path optionFile(String packName) {
        String safeName = packName.replaceAll("[^A-Za-z0-9._-]", "_");
        return optionOverridesDir.resolve(safeName + ".properties");
    }

    private void clearShaderPropertiesCache() {
        shaderPropertiesCache.clear();
    }

    private void clearShaderPropertiesCacheExcept(String packName) {
        if (packName == null || packName.isBlank()) {
            clearShaderPropertiesCache();
            return;
        }
        shaderPropertiesCache.keySet().removeIf(key -> !packName.equals(key.packName()));
    }

    private void saveShaderConfig() {
        Properties properties = new Properties();
        properties.setProperty("selectedPack", selectedPackName == null ? "OFF" : selectedPackName);
        properties.setProperty("enabled", Boolean.toString(shadersEnabled));

        try {
            Files.createDirectories(shaderConfigFile.getParent());
            try (OutputStream stream = Files.newOutputStream(shaderConfigFile)) {
                properties.store(stream, "AUSM shader configuration");
            }
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to save shader configuration", e);
        }
    }

    private boolean isCurrentPack(String packName) {
        if (packName == null) {
            return false;
        }
        return selectedPackName.equals(packName);
    }

    private boolean isPackAvailable(String packName) {
        if (packName == null || packName.isBlank() || packName.equalsIgnoreCase("OFF")) {
            return false;
        }

        Path packPath = shaderpacksDir.resolve(packName);
        return Files.isDirectory(packPath) || Files.isRegularFile(packPath);
    }

    private boolean hasDimensionSpecificResources(int dimensionId) {
        if (currentPack == null || currentPack.getName().equals("(internal)") || !currentPack.getName().equals(compiledPackName)) {
            return true;
        }
        String prefix = ShaderPackLayout.detect(currentPack).rootPath("world" + dimensionId + "/");
        return currentPack.hasResource(prefix + "shaders.properties")
                || currentPack.hasResource(prefix + "shader.h")
                || currentPack.hasResource(prefix + "final.fsh")
                || currentPack.hasResource(prefix + "composite.fsh")
                || currentPack.hasResource(prefix + "gbuffers_terrain.vsh")
                || currentPack.hasResource(prefix + "gbuffers_terrain.fsh")
                || currentPack.hasResource(prefix + "shadow.vsh")
                || currentPack.hasResource(prefix + "shadow.fsh")
                || currentPack.hasResource(prefix + "shadowcomp.csh");
    }

    private boolean isValidPackPath(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        if (Files.isDirectory(path)) {
            return true;
        }
        String fileName = path.getFileName().toString().toLowerCase();
        return Files.isRegularFile(path) && fileName.endsWith(".zip");
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.sorted().toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }

    private void fallbackToOff(String message, String packName) {
        MainMod.LOGGER.warn(message, packName);
        selectedPackName = "OFF";
        shadersEnabled = false;
        saveShaderConfig();
        setPack(NoneShaderPack.INSTANCE);
    }

    private int getClientDimensionId() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.world.provider == null) {
            return Integer.MIN_VALUE;
        }
        return mc.world.provider.getDimension();
    }

    private record ShaderPropertiesCacheKey(String packName, Map<String, String> overrides) {
    }
}
