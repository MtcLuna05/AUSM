package com.luna.ausm.impl.client.dynamic;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class DynamicLightConfig {
    private static final String ENABLED_KEY = "enabled";
    private static final String LIGHT_MULTIPLIER_KEY = "lightMultiplier";
    private static final String ITEMS_KEY = "items";
    private static final String ITEM_PREFIX = "item.";
    private static final String COLOR_PREFIX = "color.";
    private static final double DEFAULT_LIGHT_MULTIPLIER = 0.5D;
    private static final int DEFAULT_CUSTOM_ITEM_COLOR = 0xFFFFFF;

    private final Path configFile;
    private volatile boolean enabled;
    private volatile double lightMultiplier = DEFAULT_LIGHT_MULTIPLIER;
    private volatile Map<String, Integer> itemLights = Map.of();
    private volatile Map<String, Integer> itemColors = Map.of();

    public DynamicLightConfig(Path minecraftRunDir) {
        this.configFile = minecraftRunDir.resolve("config").resolve("ausm").resolve("dynamic-lights.properties");
    }

    public void load() {
        if (!Files.isRegularFile(configFile)) {
            writeDefaultConfig();
        }

        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(configFile)) {
            properties.load(stream);
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[DynamicLights] Failed to read {}; using defaults", configFile.toAbsolutePath(), e);
        }

        enabled = Boolean.parseBoolean(properties.getProperty(ENABLED_KEY, "false").trim());
        lightMultiplier = parseLightMultiplier(properties.getProperty(LIGHT_MULTIPLIER_KEY, Double.toString(DEFAULT_LIGHT_MULTIPLIER)));
        itemLights = parseItemLights(properties);
        itemColors = parseItemColors(properties, itemLights);
        if (enabled && !available()) {
            enabled = false;
            CeleritasDynamicLightsCompat.logLockout();
            save();
        }
        MainMod.LOGGER.info("[DynamicLights] Loaded config: enabled={} lightMultiplier={} customItems={}", enabled, lightMultiplier, itemLights.size());
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean available() {
        return !CeleritasDynamicLightsCompat.installed();
    }

    public String unavailableReason() {
        return available() ? "" : CeleritasDynamicLightsCompat.lockoutMessage();
    }

    public double lightMultiplier() {
        return lightMultiplier;
    }

    public int customItemCount() {
        return itemLights.size();
    }

    public Map<String, Integer> customItemLights() {
        return itemLights;
    }

    public int customItemColor(String key) {
        return itemColors.getOrDefault(key, DEFAULT_CUSTOM_ITEM_COLOR);
    }

    public void upsertCustomItem(String rawKey, int light, int color) {
        String key = normalizeKey(rawKey);
        if (key == null) {
            throw new IllegalArgumentException("Invalid item key: " + rawKey);
        }
        int clampedLight = Math.clamp(light, 0, 15);
        if (clampedLight == 0) {
            removeCustomItem(key);
            return;
        }
        Map<String, Integer> updatedLights = new LinkedHashMap<>(itemLights);
        Map<String, Integer> updatedColors = new LinkedHashMap<>(itemColors);
        updatedLights.put(key, clampedLight);
        updatedColors.put(key, color & 0xFFFFFF);
        itemLights = Map.copyOf(updatedLights);
        itemColors = Map.copyOf(updatedColors);
        save();
    }

    public void removeCustomItem(String rawKey) {
        String key = normalizeKey(rawKey);
        if (key == null || !itemLights.containsKey(key)) {
            return;
        }
        Map<String, Integer> updatedLights = new LinkedHashMap<>(itemLights);
        Map<String, Integer> updatedColors = new LinkedHashMap<>(itemColors);
        updatedLights.remove(key);
        updatedColors.remove(key);
        itemLights = Map.copyOf(updatedLights);
        itemColors = Map.copyOf(updatedColors);
        save();
    }

    public static String itemKey(ResourceLocation itemId, Integer metadata) {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId");
        }
        return normalizeId(itemId) + (metadata == null ? "" : "@" + Math.max(0, metadata));
    }

    public void setEnabled(boolean enabled) {
        if (enabled && !available()) {
            CeleritasDynamicLightsCompat.logLockout();
            if (this.enabled) {
                this.enabled = false;
                save();
            }
            return;
        }
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        save();
    }

    public void setLightMultiplier(double lightMultiplier) {
        double clamped = clampLightMultiplier(lightMultiplier);
        if (Double.compare(this.lightMultiplier, clamped) == 0) {
            return;
        }
        this.lightMultiplier = clamped;
        save();
    }

    public int configuredLight(ItemStack stack) {
        Item item = MinecraftReflectionCompat.itemStackItem(stack);
        if (MinecraftReflectionCompat.itemStackIsEmpty(stack) || item == null) {
            return 0;
        }

        ResourceLocation name = MinecraftReflectionCompat.call(item, ResourceLocation.class, null, new String[]{"getRegistryName"}, MinecraftReflectionCompat.NO_PARAMETERS);
        if (name == null) {
            return 0;
        }

        String id = normalizeId(name.toString());
        Integer exact = itemLights.get(id + "@" + MinecraftReflectionCompat.itemStackMetadata(stack));
        if (exact != null) {
            return exact;
        }
        Integer wildcard = itemLights.get(id);
        return wildcard != null ? wildcard : 0;
    }

    public Path configFile() {
        return configFile;
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty(ENABLED_KEY, Boolean.toString(enabled));
        properties.setProperty(LIGHT_MULTIPLIER_KEY, Double.toString(lightMultiplier));
        properties.setProperty(ITEMS_KEY, formatItems(itemLights));
        for (Map.Entry<String, Integer> entry : itemColors.entrySet()) {
            if (itemLights.containsKey(entry.getKey())) {
                properties.setProperty(COLOR_PREFIX + entry.getKey(), String.format(Locale.ROOT, "%06X", entry.getValue() & 0xFFFFFF));
            }
        }

        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream stream = Files.newOutputStream(configFile)) {
                properties.store(stream, "AUSM shaderless dynamic lights. Format: items=modid:item=15, modid:item@metadata=12");
            }
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[DynamicLights] Failed to save {}", configFile.toAbsolutePath(), e);
        }
    }

    private void writeDefaultConfig() {
        try {
            Files.createDirectories(configFile.getParent());
            String text = """
                    # AUSM shaderless dynamic lights.
                    # This only affects the vanilla/non-shader renderer.
                    enabled=false
                    # Multiplier for automatic block-item lights. Explicit items= entries are not scaled.
                    lightMultiplier=0.5
                    # Extra item light entries. Omit metadata to match all metadata.
                    # Examples:
                    # items=minecraft:stick=14, minecraft:skull@1=10
                    items=
                    # Optional editor color for a custom item; shaderless block light stays monochrome.
                    # color.minecraft:stick=FFE080
                    """;
            Files.writeString(configFile, text);
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[DynamicLights] Failed to create default config {}", configFile.toAbsolutePath(), e);
        }
    }

    private static Map<String, Integer> parseItemLights(Properties properties) {
        Map<String, Integer> values = new LinkedHashMap<>();
        parseItemsProperty(properties.getProperty(ITEMS_KEY, ""), values);

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(ITEM_PREFIX)) {
                continue;
            }
            parseEntry(key.substring(ITEM_PREFIX.length()) + "=" + properties.getProperty(key), values);
        }

        return Map.copyOf(values);
    }

    private static Map<String, Integer> parseItemColors(Properties properties, Map<String, Integer> lights) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String property : properties.stringPropertyNames()) {
            if (!property.startsWith(COLOR_PREFIX)) {
                continue;
            }
            String key = normalizeKey(property.substring(COLOR_PREFIX.length()));
            if (key == null || !lights.containsKey(key)) {
                continue;
            }
            Integer color = parseColor(properties.getProperty(property));
            if (color != null) {
                values.put(key, color);
            }
        }
        return Map.copyOf(values);
    }

    private static Integer parseColor(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replace("#", "");
        if (value.length() == 8 && value.regionMatches(true, 0, "FF", 0, 2)) {
            value = value.substring(2);
        }
        if (value.length() != 6 || !value.matches("[0-9A-Fa-f]{6}")) {
            MainMod.LOGGER.warn("[DynamicLights] Ignoring invalid custom item color '{}'", raw);
            return null;
        }
        return Integer.parseInt(value, 16);
    }

    private static double parseLightMultiplier(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value)) {
                throw new NumberFormatException(raw);
            }
            if (value < 0.0D || value > 4.0D) {
                MainMod.LOGGER.warn("[DynamicLights] Clamping lightMultiplier '{}' to 0..4", raw);
            }
            return clampLightMultiplier(value);
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[DynamicLights] Invalid lightMultiplier '{}'; using {}", raw, DEFAULT_LIGHT_MULTIPLIER);
            return DEFAULT_LIGHT_MULTIPLIER;
        }
    }

    private static double clampLightMultiplier(double value) {
        return Math.clamp(value, 0.0D, 4.0D);
    }

    private static void parseItemsProperty(String raw, Map<String, Integer> values) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String entry : raw.split("[,;\\n\\r]+")) {
            parseEntry(entry, values);
        }
    }

    private static void parseEntry(String raw, Map<String, Integer> values) {
        if (raw == null) {
            return;
        }
        String entry = raw.trim();
        if (entry.isEmpty() || entry.startsWith("#")) {
            return;
        }

        int equals = entry.lastIndexOf('=');
        if (equals <= 0 || equals >= entry.length() - 1) {
            MainMod.LOGGER.warn("[DynamicLights] Ignoring malformed item light entry '{}'", entry);
            return;
        }

        String key = normalizeKey(entry.substring(0, equals));
        int light = parseLight(entry.substring(equals + 1), entry);
        if (key != null && light > 0) {
            values.put(key, light);
        }
    }

    private static String normalizeKey(String raw) {
        String key = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return null;
        }

        int metadataSeparator = key.lastIndexOf('@');
        String id = metadataSeparator >= 0 ? key.substring(0, metadataSeparator) : key;
        ResourceLocation resource = parseResource(id);
        if (resource == null) {
            MainMod.LOGGER.warn("[DynamicLights] Ignoring item light entry with invalid item id '{}'", raw);
            return null;
        }
        if (metadataSeparator < 0) {
            return normalizeId(resource);
        }

        String metadata = key.substring(metadataSeparator + 1);
        try {
            int parsedMetadata = Integer.parseInt(metadata);
            if (parsedMetadata < 0) {
                throw new NumberFormatException(metadata);
            }
            return normalizeId(resource) + "@" + parsedMetadata;
        } catch (NumberFormatException e) {
            MainMod.LOGGER.warn("[DynamicLights] Ignoring item light entry with invalid metadata '{}'", raw);
            return null;
        }
    }

    private static int parseLight(String raw, String entry) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0 || value > 15) {
                MainMod.LOGGER.warn("[DynamicLights] Clamping light value for '{}' to 0..15", entry);
            }
            return Math.clamp(value, 0, 15);
        } catch (NumberFormatException e) {
            MainMod.LOGGER.warn("[DynamicLights] Ignoring item light entry with invalid light '{}'", entry);
            return 0;
        }
    }

    private static String normalizeId(String id) {
        ResourceLocation resource = parseResource(id);
        return resource == null ? id.toLowerCase(Locale.ROOT) : normalizeId(resource);
    }

    private static String normalizeId(ResourceLocation resource) {
        return resource.toString().toLowerCase(Locale.ROOT);
    }

    private static ResourceLocation parseResource(String id) {
        try {
            return new ResourceLocation(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String formatItems(Map<String, Integer> values) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
