package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShaderItemIdMap {

    private ShaderItemIdMap() {
    }

    public static Map<Integer, Integer> load(ShaderPack pack, ShaderPackLayout layout) {
        Map<Integer, Integer> itemIds = new LinkedHashMap<>();
        loadFile(pack, layout.rootPath("item.properties"), itemIds);
        return Map.copyOf(itemIds);
    }

    private static void loadFile(ShaderPack pack, String path, Map<Integer, Integer> itemIds) {
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
                    parseLine(stripComment(line).trim(), itemIds);
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderItemIds] Failed to read {}", path, e);
        }
    }

    private static void parseLine(String line, Map<Integer, Integer> itemIds) {
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
            Item item = parseItem(token);
            if (item != null) {
                itemIds.put(Item.getIdFromItem(item), aliasId);
            }
        }
    }

    private static Item parseItem(String token) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        ResourceLocation resource = trimmed.contains(":")
                ? new ResourceLocation(trimmed)
                : new ResourceLocation("minecraft", trimmed);
        Item item = Item.REGISTRY.getObject(resource);
        return item != null ? item : null;
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment >= 0 ? line.substring(0, comment) : line;
    }
}
