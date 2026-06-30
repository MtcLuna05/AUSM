package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShaderBlockIdMap {
    private static final ColorAlias[] MINECRAFT_DYE_COLORS = {
            new ColorAlias("white", 10900),
            new ColorAlias("orange", 10904),
            new ColorAlias("magenta", 10920),
            new ColorAlias("light_blue", 10914),
            new ColorAlias("yellow", 10906),
            new ColorAlias("lime", 10908),
            new ColorAlias("pink", 10922),
            new ColorAlias("gray", 10900),
            new ColorAlias("light_gray", 10900),
            new ColorAlias("cyan", 10912),
            new ColorAlias("purple", 10918),
            new ColorAlias("blue", 10916),
            new ColorAlias("brown", 10904),
            new ColorAlias("green", 10910),
            new ColorAlias("red", 10902),
            new ColorAlias("black", 10900)
    };

    private static final ColorAlias[] THAUMCRAFT_COLORS = {
            new ColorAlias("black", 10900),
            new ColorAlias("blue", 10916),
            new ColorAlias("brown", 10904),
            new ColorAlias("cyan", 10912),
            new ColorAlias("gray", 10900),
            new ColorAlias("green", 10910),
            new ColorAlias("lightblue", 10914),
            new ColorAlias("lime", 10908),
            new ColorAlias("magenta", 10920),
            new ColorAlias("orange", 10904),
            new ColorAlias("pink", 10922),
            new ColorAlias("purple", 10918),
            new ColorAlias("red", 10902),
            new ColorAlias("silver", 10900),
            new ColorAlias("white", 10900),
            new ColorAlias("yellow", 10906)
    };

    private ShaderBlockIdMap() {
    }

    public static BlockIdRules load(ShaderPack pack, ShaderPackLayout layout) {
        Map<Block, Integer> blockIds = new LinkedHashMap<>();
        List<StateRule> stateRules = new ArrayList<>();
        Map<Block, BlockRenderLayer> layerOverrides = new LinkedHashMap<>();
        String blockPropertiesPath = layout.rootPath("block.properties");
        boolean hasBlockProperties = pack.hasResource(blockPropertiesPath);
        loadFile(pack, blockPropertiesPath, blockIds, stateRules, layerOverrides);
        if (!hasBlockProperties) {
            addLegacyDefaults(blockIds);
        } else {
            addPackCompatibilityAliases(blockIds);
            addLegacyColorStateRules(blockIds, stateRules);
            addModdedColoredLightCompatibility(blockIds, stateRules);
        }
        return new BlockIdRules(Map.copyOf(blockIds), List.copyOf(stateRules), Map.copyOf(layerOverrides));
    }

    private static void addLegacyDefaults(Map<Block, Integer> blockIds) {
        addLegacyBlockId(blockIds, 1, "stone");
        addLegacyBlockId(blockIds, 2, "grass");
        addLegacyBlockId(blockIds, 4, "cobblestone");
        addLegacyBlockId(blockIds, 50, "torch", "redstone_torch", "unlit_redstone_torch");
        addLegacyBlockId(blockIds, 89, "glowstone");
        addLegacyBlockId(blockIds, 124, "redstone_lamp", "lit_redstone_lamp");
        addLegacyBlockId(blockIds, 12, "sand");
        addLegacyBlockId(blockIds, 24, "sandstone");
        addLegacyBlockId(blockIds, 41, "gold_block");
        addLegacyBlockId(blockIds, 42, "iron_block");
        addLegacyBlockId(blockIds, 57, "diamond_block");
        addLegacyBlockId(blockIds, -123, "emerald_block");
        addLegacyBlockId(blockIds, 35, "wool");
        addLegacyBlockId(blockIds, 9, "water", "flowing_water");
        addLegacyBlockId(blockIds, 11, "lava", "flowing_lava");
        addLegacyBlockId(blockIds, 79, "ice");
        addLegacyBlockId(blockIds, 18, "leaves", "leaves2");
        addLegacyBlockId(blockIds, 95, "stained_glass");
        addLegacyBlockId(blockIds, 160, "stained_glass_pane");
        addLegacyBlockId(blockIds, 31, "tallgrass");
        addLegacyBlockId(blockIds, 59, "wheat", "carrots", "potatoes");
        addLegacyBlockId(blockIds, 37, "yellow_flower", "red_flower");
        addLegacyBlockId(blockIds, 175, "double_plant");
        addLegacyBlockId(blockIds, 51, "fire");
        addLegacyBlockId(blockIds, 111, "waterlily");
    }

    private static void addLegacyBlockId(Map<Block, Integer> blockIds, int id, String... names) {
        for (String name : names) {
            Block block = Block.REGISTRY.getObject(new ResourceLocation("minecraft", name));
            if (block != null) {
                blockIds.putIfAbsent(block, id);
            }
        }
    }

    private static void addPackCompatibilityAliases(Map<Block, Integer> blockIds) {
        Block portal = Block.REGISTRY.getObject(new ResourceLocation("minecraft", "portal"));
        if (portal == null || blockIds.containsKey(portal) || !blockIds.containsValue(10090)) {
            return;
        }

        blockIds.put(portal, 10090);
    }

    private static void addLegacyColorStateRules(Map<Block, Integer> blockIds, List<StateRule> stateRules) {
        addLegacyDyeColorRules(blockIds, stateRules, "stained_glass", 31000);
        addLegacyDyeColorRules(blockIds, stateRules, "stained_glass_pane", 31001);
    }

    private static void addModdedColoredLightCompatibility(Map<Block, Integer> blockIds, List<StateRule> stateRules) {
        addBlockAlias(blockIds, "appliedenergistics2", "cable_bus", 12120);
        addStateAlias(stateRules, "minecraft", "concrete", "color", "black", 12130);

        for (ColorAlias color : THAUMCRAFT_COLORS) {
            addBlockAlias(blockIds, "thaumcraft", "candle_" + color.name(), color.materialId());
            addBlockAlias(blockIds, "thaumcraft", "nitor_" + color.name(), color.materialId());
        }

        for (ColorAlias color : MINECRAFT_DYE_COLORS) {
            addLitStateAlias(stateRules, "bewitchment", color.name() + "_candle", color.materialId());
        }

        addAstralCrystalCompatibility(stateRules);

        // Seared furnace controllers emit colored light through AUSM's CPU voxel injection.
        // Do not alias the rendered block ID here, or shader packs treat the whole texture as emissive.
    }

    private static void addAstralCrystalCompatibility(List<StateRule> stateRules) {
        for (int stage = 0; stage <= 4; stage++) {
            addStateAlias(stateRules, "astralsorcery", "blockcelestialcrystals", "stage", Integer.toString(stage), 10914);
        }

        addStateAlias(stateRules, "astralsorcery", "blockgemcrystals", "stage", "stage_0", 10912);
        addStateAlias(stateRules, "astralsorcery", "blockgemcrystals", "stage", "stage_1", 10912);
        addStateAlias(stateRules, "astralsorcery", "blockgemcrystals", "stage", "stage_2_sky", 10912);
        addStateAlias(stateRules, "astralsorcery", "blockgemcrystals", "stage", "stage_2_day", 10904);
        addStateAlias(stateRules, "astralsorcery", "blockgemcrystals", "stage", "stage_2_night", 10916);
    }

    private static void addBlockAlias(Map<Block, Integer> blockIds, String namespace, String path, int id) {
        Block block = findBlock(namespace, path);
        if (block != null) {
            blockIds.putIfAbsent(block, id);
        }
    }

    private static void addLitStateAlias(List<StateRule> stateRules, String namespace, String path, int id) {
        addStateAlias(stateRules, namespace, path, "lit", "true", id);
    }

    private static void addStateAlias(List<StateRule> stateRules, String namespace, String path, String propertyName, String propertyValue, int id) {
        Block block = findBlock(namespace, path);
        if (block != null) {
            stateRules.add(new StateRule(block, propertyName, propertyValue, id));
        }
    }

    private static Block findBlock(String namespace, String path) {
        Block block = Block.REGISTRY.getObject(new ResourceLocation(namespace, path));
        if (block != null) {
            return block;
        }

        for (ResourceLocation key : Block.REGISTRY.getKeys()) {
            if (namespace.equalsIgnoreCase(key.getNamespace()) && path.equalsIgnoreCase(key.getPath())) {
                return Block.REGISTRY.getObject(key);
            }
        }
        return null;
    }

    private static void addLegacyDyeColorRules(Map<Block, Integer> blockIds, List<StateRule> stateRules, String blockName, int baseId) {
        Block block = Block.REGISTRY.getObject(new ResourceLocation("minecraft", blockName));
        if (block == null) {
            return;
        }

        String[] colors = {
                "white",
                "orange",
                "magenta",
                "light_blue",
                "yellow",
                "lime",
                "pink",
                "gray",
                "silver",
                "cyan",
                "purple",
                "blue",
                "brown",
                "green",
                "red",
                "black"
        };
        for (int i = 0; i < colors.length; i++) {
            stateRules.add(new StateRule(block, "color", colors[i], baseId + i * 2));
        }
    }

    private static void loadFile(ShaderPack pack, String path, Map<Block, Integer> blockIds, List<StateRule> stateRules,
                                 Map<Block, BlockRenderLayer> layerOverrides) {
        if (!pack.hasResource(path)) {
            return;
        }

        try (InputStream stream = pack.getResourceAsStream(path)) {
            if (stream == null) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                Deque<ConditionFrame> conditions = new ArrayDeque<>();
                boolean enabled = true;
                StringBuilder continuedLine = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = mergeContinuation(continuedLine, line);
                    if (trimmed == null) {
                        continue;
                    }

                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        if (trimmed.startsWith("#ifdef ")) {
                            boolean condition = ShaderEnvironmentDefines.baseDefineMap().containsKey(trimmed.substring("#ifdef ".length()).trim());
                            conditions.push(new ConditionFrame(enabled, condition));
                            enabled = enabled && condition;
                        } else if (trimmed.startsWith("#ifndef ")) {
                            boolean condition = !ShaderEnvironmentDefines.baseDefineMap().containsKey(trimmed.substring("#ifndef ".length()).trim());
                            conditions.push(new ConditionFrame(enabled, condition));
                            enabled = enabled && condition;
                        } else if (trimmed.startsWith("#if ")) {
                            boolean condition = evaluateCondition(trimmed.substring("#if ".length()).trim());
                            conditions.push(new ConditionFrame(enabled, condition));
                            enabled = enabled && condition;
                        } else if (trimmed.startsWith("#elif ")) {
                            if (!conditions.isEmpty()) {
                                ConditionFrame frame = conditions.pop();
                                boolean condition = !frame.branchTaken() && evaluateCondition(trimmed.substring("#elif ".length()).trim());
                                conditions.push(new ConditionFrame(frame.parentEnabled(), frame.branchTaken() || condition));
                                enabled = frame.parentEnabled() && condition;
                            }
                        } else if (trimmed.startsWith("#else")) {
                            if (!conditions.isEmpty()) {
                                ConditionFrame frame = conditions.pop();
                                boolean condition = !frame.branchTaken();
                                conditions.push(new ConditionFrame(frame.parentEnabled(), true));
                                enabled = frame.parentEnabled() && condition;
                            }
                        } else if (trimmed.startsWith("#endif")) {
                            if (!conditions.isEmpty()) {
                                enabled = conditions.pop().parentEnabled();
                            }
                        }
                        continue;
                    }

                    if (enabled) {
                        parseBlockPropertiesLine(trimmed, blockIds, stateRules, layerOverrides);
                    }
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderBlockIds] Failed to read {}", path, e);
        }
    }

    private static boolean evaluateCondition(String expression) {
        return ShaderExpressionEvaluator.evaluate(expression, ShaderEnvironmentDefines.baseDefineMap());
    }

    private static String mergeContinuation(StringBuilder continuedLine, String line) {
        String trimmed = line.trim();
        boolean continues = trimmed.endsWith("\\");
        if (continues) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        if (!trimmed.isEmpty()) {
            if (continuedLine.length() > 0) {
                continuedLine.append(' ');
            }
            continuedLine.append(trimmed);
        }

        if (continues) {
            return null;
        }

        String merged = continuedLine.toString().trim();
        continuedLine.setLength(0);
        return merged;
    }

    private static void parseBlockPropertiesLine(String line, Map<Block, Integer> blockIds, List<StateRule> stateRules,
                                                 Map<Block, BlockRenderLayer> layerOverrides) {
        int equals = line.indexOf('=');
        if (equals <= 0) {
            return;
        }

        String key = line.substring(0, equals).trim();
        if (key.startsWith("layer.")) {
            parseLayerLine(key, line.substring(equals + 1), layerOverrides);
            return;
        }
        if (!key.startsWith("block.")) {
            return;
        }

        int id;
        try {
            id = Integer.parseInt(key.substring("block.".length()));
        } catch (NumberFormatException e) {
            return;
        }

        String values = line.substring(equals + 1);
        for (String token : values.split("\\s+")) {
            ParsedBlockToken parsed = parseResource(token);
            if (parsed == null) {
                continue;
            }

            Block block = Block.REGISTRY.getObject(parsed.resource());
            if (block != null) {
                if (parsed.hasStatePredicate()) {
                    stateRules.add(new StateRule(block, parsed.propertyName(), parsed.propertyValue(), id));
                } else {
                    blockIds.put(block, id);
                }
            }

            for (ResourceLocation alias : legacyAliases(parsed.resource())) {
                Block aliasBlock = Block.REGISTRY.getObject(alias);
                if (aliasBlock != null) {
                    blockIds.put(aliasBlock, id);
                }
            }
        }
    }

    private static void parseLayerLine(String key, String values, Map<Block, BlockRenderLayer> layerOverrides) {
        BlockRenderLayer layer = parseLayer(key.substring("layer.".length()));
        if (layer == null) {
            MainMod.LOGGER.warn("[ShaderBlockIds] Ignoring unknown block render layer override: {}", key);
            return;
        }

        for (String token : values.split("\\s+")) {
            if (token.startsWith("%")) {
                MainMod.LOGGER.warn("[ShaderBlockIds] Ignoring tag '{}' in render layer override {}", token, key);
                continue;
            }
            ParsedBlockToken parsed = parseResource(token);
            if (parsed == null) {
                continue;
            }
            Block block = Block.REGISTRY.getObject(parsed.resource());
            if (block != null) {
                layerOverrides.put(block, layer);
            }
            for (ResourceLocation alias : legacyAliases(parsed.resource())) {
                Block aliasBlock = Block.REGISTRY.getObject(alias);
                if (aliasBlock != null) {
                    layerOverrides.put(aliasBlock, layer);
                }
            }
        }
    }

    private static BlockRenderLayer parseLayer(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT).replace("-", "_")) {
            case "solid" -> BlockRenderLayer.SOLID;
            case "cutout" -> BlockRenderLayer.CUTOUT;
            case "cutout_mipped", "cutout_mip", "cutoutmipped" -> BlockRenderLayer.CUTOUT_MIPPED;
            case "translucent" -> BlockRenderLayer.TRANSLUCENT;
            default -> null;
        };
    }

    private static List<ResourceLocation> legacyAliases(ResourceLocation resource) {
        if (!"minecraft".equals(resource.getNamespace())) {
            return List.of();
        }

        return switch (resource.getPath()) {
            case "grass", "short_grass", "tall_grass", "fern", "large_fern" -> minecraft("tallgrass", "double_plant");
            case "dead_bush" -> minecraft("deadbush");
            case "cobweb" -> minecraft("web");
            case "lily_pad" -> minecraft("waterlily");
            case "sugar_cane" -> minecraft("reeds");
            case "dandelion" -> minecraft("yellow_flower");
            case "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy" -> minecraft("red_flower");
            case "sunflower", "lilac", "rose_bush", "peony" -> minecraft("double_plant");
            case "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling" -> minecraft("sapling");
            case "carrots" -> minecraft("carrots");
            case "potatoes" -> minecraft("potatoes");
            case "beetroots" -> minecraft("beetroots");
            case "nether_wart" -> minecraft("nether_wart");
            case "chorus_flower" -> minecraft("chorus_flower");
            case "redstone_ore" -> minecraft("redstone_ore", "lit_redstone_ore");
            case "redstone_torch" -> minecraft("redstone_torch");
            case "redstone_lamp" -> minecraft("redstone_lamp", "lit_redstone_lamp");
            case "jack_o_lantern" -> minecraft("lit_pumpkin");
            case "magma_block" -> minecraft("magma");
            case "sea_lantern" -> minecraft("sea_lantern");
            case "end_rod" -> minecraft("end_rod");
            case "glowstone" -> minecraft("glowstone");
            case "torch" -> minecraft("torch", "redstone_torch", "unlit_redstone_torch");
            case "water" -> minecraft("water", "flowing_water");
            case "lava" -> minecraft("lava", "flowing_lava");
            case "fire" -> minecraft("fire");
            case "nether_portal" -> minecraft("portal");
            default -> List.of();
        };
    }

    private static List<ResourceLocation> minecraft(String... paths) {
        return java.util.Arrays.stream(paths)
                .map(path -> new ResourceLocation("minecraft", path))
                .toList();
    }

    private static ParsedBlockToken parseResource(String token) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] parts = trimmed.split(":");
        if (parts.length == 1) {
            return new ParsedBlockToken(new ResourceLocation("minecraft", parts[0]), null, null);
        }

        if (parts.length == 2) {
            if (isStateSuffix(parts[1])) {
                return parsedStateToken(new ResourceLocation("minecraft", parts[0]), parts[1]);
            }
            if (isMetadataSuffix(parts[1])) {
                return new ParsedBlockToken(new ResourceLocation("minecraft", parts[0]), null, null);
            }
            return new ParsedBlockToken(new ResourceLocation(parts[0], parts[1]), null, null);
        }

        if (parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        ResourceLocation resource = new ResourceLocation(parts[0], parts[1]);
        if (isStateSuffix(parts[2])) {
            return parsedStateToken(resource, parts[2]);
        }
        return new ParsedBlockToken(resource, null, null);
    }

    private static ParsedBlockToken parsedStateToken(ResourceLocation resource, String stateSuffix) {
        int equals = stateSuffix.indexOf('=');
        if (equals <= 0 || equals >= stateSuffix.length() - 1) {
            return new ParsedBlockToken(resource, null, null);
        }
        return new ParsedBlockToken(resource, stateSuffix.substring(0, equals), stateSuffix.substring(equals + 1));
    }

    private static boolean isStateSuffix(String value) {
        return value.indexOf('=') >= 0;
    }

    private static boolean isMetadataSuffix(String value) {
        return value.chars().allMatch(Character::isDigit);
    }

    public record BlockIdRules(Map<Block, Integer> blockIds, List<StateRule> stateRules,
                               Map<Block, BlockRenderLayer> layerOverrides) {
        public boolean isEmpty() {
            return blockIds.isEmpty() && stateRules.isEmpty();
        }

        public int idFor(IBlockState state) {
            for (StateRule rule : stateRules) {
                if (rule.matches(state)) {
                    return rule.id();
                }
            }
            return blockIds.getOrDefault(state.getBlock(), 0);
        }
    }

    public record StateRule(Block block, String propertyName, String propertyValue, int id) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public boolean matches(IBlockState state) {
            if (state == null || state.getBlock() != block) {
                return false;
            }

            for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
                IProperty property = entry.getKey();
                if (property.getName().equals(propertyName)) {
                    return property.getName(entry.getValue()).equalsIgnoreCase(propertyValue);
                }
            }
            return "false".equalsIgnoreCase(propertyValue) || "0".equals(propertyValue);
        }
    }

    private record ParsedBlockToken(ResourceLocation resource, String propertyName, String propertyValue) {
        private boolean hasStatePredicate() {
            return propertyName != null && propertyValue != null;
        }
    }

    private record ConditionFrame(boolean parentEnabled, boolean branchTaken) {
    }

    private record ColorAlias(String name, int materialId) {
    }
}
