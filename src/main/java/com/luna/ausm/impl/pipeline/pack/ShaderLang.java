package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class ShaderLang {

    private ShaderLang() {
    }

    public static Map<String, String> load(ShaderPack pack, ShaderPackLayout layout) {
        Map<String, String> translations = new LinkedHashMap<>();
        for (String path : candidatePaths(layout)) {
            loadFile(pack, path, translations);
        }
        return Map.copyOf(translations);
    }

    private static List<String> candidatePaths(ShaderPackLayout layout) {
        String language = currentLanguage();
        if (language.equals("en_US")) {
            return List.of(
                    layout.langPath("en_US.lang"),
                    layout.langPath("en_us.lang"),
                    "lang/en_US.lang",
                    "lang/en_us.lang"
            );
        }

        return List.of(
                layout.langPath("en_US.lang"),
                layout.langPath("en_us.lang"),
                "lang/en_US.lang",
                "lang/en_us.lang",
                layout.langPath(language + ".lang"),
                layout.langPath(language.toLowerCase(Locale.ROOT) + ".lang"),
                "lang/" + language + ".lang",
                "lang/" + language.toLowerCase(Locale.ROOT) + ".lang"
        );
    }

    private static String currentLanguage() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        String language = mc != null
                ? MinecraftReflectionCompat.field(MinecraftReflectionCompat.gameSettings(mc), String.class, "", "field_74363_ab", "language")
                : "";
        if (language == null || language.isBlank()) {
            return "en_US";
        }
        return language;
    }

    private static void loadFile(ShaderPack pack, String path, Map<String, String> translations) {
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
                    parseLine(line, translations);
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderLang] Failed to read {}", path, e);
        }
    }

    private static void parseLine(String line, Map<String, String> translations) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }

        int equals = trimmed.indexOf('=');
        if (equals <= 0) {
            return;
        }

        String key = trimmed.substring(0, equals).trim();
        String value = trimmed.substring(equals + 1).trim();
        if (!key.isEmpty()) {
            translations.put(key, value);
        }
    }
}
