package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class ShaderItemIdMap {
    private static final ColorAlias[] THAUMCRAFT_COLORS = {
            new ColorAlias("black", 44024),
            new ColorAlias("blue", 44077),
            new ColorAlias("brown", 44071),
            new ColorAlias("cyan", 44075),
            new ColorAlias("gray", 44024),
            new ColorAlias("green", 44074),
            new ColorAlias("lightblue", 44076),
            new ColorAlias("lime", 44073),
            new ColorAlias("magenta", 44079),
            new ColorAlias("orange", 44071),
            new ColorAlias("pink", 44080),
            new ColorAlias("purple", 44078),
            new ColorAlias("red", 44070),
            new ColorAlias("silver", 44024),
            new ColorAlias("white", 44024),
            new ColorAlias("yellow", 44072)
    };

    private ShaderItemIdMap() {
    }

    public static ItemIdRules load(ShaderPack pack, ShaderPackLayout layout) {
        Map<Integer, Integer> itemIds = new LinkedHashMap<>();
        Map<ItemMetadataKey, Integer> metadataIds = new LinkedHashMap<>();
        String itemPropertiesPath = layout.rootPath("item.properties");
        loadFile(pack, itemPropertiesPath, itemIds, metadataIds);
        if (pack.hasResource(itemPropertiesPath)) {
            addCompatibilityAliases(itemIds, metadataIds);
        }
        return new ItemIdRules(Map.copyOf(itemIds), Map.copyOf(metadataIds));
    }

    private static void addCompatibilityAliases(Map<Integer, Integer> itemIds, Map<ItemMetadataKey, Integer> metadataIds) {
        addProjectRedIlluminationAliases(metadataIds);
        addThaumcraftLightAliases(itemIds, metadataIds);
    }

    private static void addProjectRedIlluminationAliases(Map<ItemMetadataKey, Integer> metadataIds) {
        addDyeMetadataAliases(metadataIds, "projectred-illumination", "lamp", 0, 15);
        addDyeMetadataAliases(metadataIds, "projectred-illumination", "lamp", 16, 31);
        addDyeMetadataAliases(metadataIds, "projectred-illumination", "fixture_light", 0, 15);
        addDyeMetadataAliases(metadataIds, "projectred-illumination", "inverted_fixture_light", 0, 15);
        addDyeMetadataAliases(metadataIds, "projectred-illumination", "lantern", 0, 15);
        addDyeMetadataAliases(metadataIds, "projectred-illumination", "inverted_lantern", 0, 15);
        addDyeMetadataAliases(metadataIds, "projectred-illumination", "cage_lamp", 0, 15);
        addDyeMetadataAliases(metadataIds, "projectred-illumination", "inverted_cage_lamp", 0, 15);
        addDyeMetadataAliases(metadataIds, "projectred-illumination", "fallout_lamp", 0, 15);
        addDyeMetadataAliases(metadataIds, "projectred-illumination", "inverted_fallout_lamp", 0, 15);
    }

    private static void addThaumcraftLightAliases(Map<Integer, Integer> itemIds, Map<ItemMetadataKey, Integer> metadataIds) {
        addThaumcraftColorAliases(itemIds, "candle");
        addThaumcraftColorAliases(itemIds, "nitor");
        addDyeMetadataAliases(metadataIds, "thaumcraft", "candle", 0, 15);
        addDyeMetadataAliases(metadataIds, "thaumcraft", "nitor", 0, 15);
    }

    private static void addThaumcraftColorAliases(Map<Integer, Integer> itemIds, String prefix) {
        for (ColorAlias color : THAUMCRAFT_COLORS) {
            addItemAlias(itemIds, "thaumcraft", prefix + "_" + color.name(), color.itemId());
        }
    }

    private static void addDyeMetadataAliases(Map<ItemMetadataKey, Integer> metadataIds, String namespace, String path, int firstMetadata, int lastMetadata) {
        Item item = registryItem(new ResourceLocation(namespace, path));
        if (item == null) {
            return;
        }

        int itemId = MinecraftReflectionCompat.itemId(item);
        for (int metadata = firstMetadata; metadata <= lastMetadata; metadata++) {
            metadataIds.putIfAbsent(new ItemMetadataKey(itemId, metadata), compatItemIdForDye(metadata));
        }
    }

    private static void addItemAlias(Map<Integer, Integer> itemIds, String namespace, String path, int aliasId) {
        Item item = registryItem(new ResourceLocation(namespace, path));
        if (item != null) {
            itemIds.putIfAbsent(MinecraftReflectionCompat.itemId(item), aliasId);
        }
    }

    private static int compatItemIdForDye(int metadata) {
        return switch (metadata & 15) {
            case 1, 12 -> 44071;
            case 2 -> 44079;
            case 3 -> 44076;
            case 4 -> 44072;
            case 5 -> 44073;
            case 6 -> 44080;
            case 9 -> 44075;
            case 10 -> 44078;
            case 11 -> 44077;
            case 13 -> 44074;
            case 14 -> 44070;
            default -> 44024;
        };
    }

    private static void loadFile(ShaderPack pack, String path, Map<Integer, Integer> itemIds, Map<ItemMetadataKey, Integer> metadataIds) {
        if (!pack.hasResource(path)) {
            return;
        }

        try (InputStream stream = pack.getResourceAsStream(path)) {
            if (stream == null) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLine(stripComment(line).trim(), itemIds, metadataIds);
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderItemIds] Failed to read {}", path, e);
        }
    }

    private static void parseLine(String line, Map<Integer, Integer> itemIds, Map<ItemMetadataKey, Integer> metadataIds) {
        if (line.isEmpty()) {
            return;
        }

        int equals = line.indexOf('=');
        if (equals <= 0) {
            return;
        }

        String key = line.substring(0, equals).trim();
        if (!key.startsWith("item.")) {
            return;
        }

        int aliasId;
        try {
            aliasId = Integer.parseInt(key.substring("item.".length()));
        } catch (NumberFormatException e) {
            return;
        }

        String values = line.substring(equals + 1);
        for (String token : values.split("\\s+")) {
            ParsedItemToken parsed = parseItem(token);
            if (parsed != null) {
                int itemId = MinecraftReflectionCompat.itemId(parsed.item());
                if (parsed.hasMetadata()) {
                    metadataIds.put(new ItemMetadataKey(itemId, parsed.metadata()), aliasId);
                } else {
                    itemIds.put(itemId, aliasId);
                }
            }
        }
    }

    private static ParsedItemToken parseItem(String token) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        ParsedResource parsed = parseResource(trimmed);
        if (parsed == null) {
            return null;
        }

        ResourceLocation resource = parsed.resource();
        Item item = registryItem(resource);
        return item != null ? new ParsedItemToken(item, parsed.metadata()) : null;
    }

    private static Item registryItem(ResourceLocation resource) {
        Object value = MinecraftReflectionCompat.invoke(MinecraftReflectionCompat.field(Item.class, Object.class, null, "field_150901_e", "REGISTRY"), new String[]{"func_82594_a", "getObject", "getValue"}, new Class<?>[]{ResourceLocation.class}, resource);
        return value instanceof Item ? (Item) value : null;
    }

    private static ParsedResource parseResource(String token) {
        String[] parts = token.split(":");
        if (parts.length == 1) {
            return new ParsedResource(new ResourceLocation("minecraft", parts[0]), null);
        }

        if (parts.length == 2) {
            Integer metadata = parseMetadataSuffix(parts[1]);
            if (metadata != null) {
                return new ParsedResource(new ResourceLocation("minecraft", parts[0]), metadata);
            }
            return new ParsedResource(new ResourceLocation(parts[0], parts[1]), null);
        }

        if (parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }

        Integer metadata = parseMetadataSuffix(parts[2]);
        if (metadata == null) {
            return null;
        }
        return new ParsedResource(new ResourceLocation(parts[0], parts[1]), metadata);
    }

    private static Integer parseMetadataSuffix(String suffix) {
        String value = suffix;
        int equals = suffix.indexOf('=');
        if (equals >= 0) {
            String key = suffix.substring(0, equals).trim();
            if (!key.equalsIgnoreCase("metadata") && !key.equalsIgnoreCase("meta") && !key.equalsIgnoreCase("data")) {
                return null;
            }
            value = suffix.substring(equals + 1).trim();
        }

        if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    public record ItemIdRules(Map<Integer, Integer> itemIds, Map<ItemMetadataKey, Integer> metadataIds) {
        public int idFor(ItemStack stack) {
            if (MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
                return -1;
            }

            int itemId = MinecraftReflectionCompat.itemId(MinecraftReflectionCompat.itemStackItem(stack));
            Integer metadataId = metadataIds.get(new ItemMetadataKey(itemId, MinecraftReflectionCompat.itemStackMetadata(stack)));
            if (metadataId != null) {
                return metadataId;
            }
            return itemIds.getOrDefault(itemId, 0);
        }

        public Integer explicitIdFor(ItemStack stack) {
            if (MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
                return null;
            }

            int itemId = MinecraftReflectionCompat.itemId(MinecraftReflectionCompat.itemStackItem(stack));
            Integer metadataId = metadataIds.get(new ItemMetadataKey(itemId, MinecraftReflectionCompat.itemStackMetadata(stack)));
            if (metadataId != null) {
                return metadataId;
            }
            return itemIds.get(itemId);
        }
    }

    public record ItemMetadataKey(int itemId, int metadata) {
    }

    private record ParsedResource(ResourceLocation resource, Integer metadata) {
    }

    private record ParsedItemToken(Item item, Integer metadata) {
        private boolean hasMetadata() {
            return metadata != null;
        }
    }

    private record ColorAlias(String name, int itemId) {
    }
}
