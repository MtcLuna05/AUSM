package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.ResourceLocation;

public final class ShaderEntityIdMap {

    private ShaderEntityIdMap() {
    }

    public static Map<ResourceLocation, Integer> load(ShaderPack pack, ShaderPackLayout layout) {
        Map<ResourceLocation, Integer> entityIds = new LinkedHashMap<>();
        loadFile(pack, layout.rootPath("entity.properties"), entityIds);
        return Map.copyOf(entityIds);
    }

    private static void loadFile(ShaderPack pack, String path, Map<ResourceLocation, Integer> entityIds) {
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
                    parseLine(stripComment(line).trim(), entityIds);
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderEntityIds] Failed to read {}", path, e);
        }
    }

    private static void parseLine(String line, Map<ResourceLocation, Integer> entityIds) {
        if (line.isEmpty()) {
            return;
        }

        int equals = line.indexOf('=');
        if (equals <= 0) {
            return;
        }

        String key = line.substring(0, equals).trim();
        if (!key.startsWith("entity.")) {
            return;
        }

        int id;
        try {
            id = Integer.parseInt(key.substring("entity.".length()));
        } catch (NumberFormatException e) {
            return;
        }

        String values = line.substring(equals + 1);
        for (String token : values.split("\\s+")) {
            ResourceLocation entity = parseResource(token);
            if (entity != null) {
                entityIds.put(entity, id);
            }
        }
    }

    private static ResourceLocation parseResource(String token) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.contains(":") ? new ResourceLocation(trimmed) : new ResourceLocation("minecraft", trimmed);
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment >= 0 ? line.substring(0, comment) : line;
    }
}
