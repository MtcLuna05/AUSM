package com.luna.ausm.impl.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MakeUp-specific syntax and material bindings for the reusable 1.12.2 features. */
final class MakeupUltraFast112CompatibilityPatches {
    private static final String SETTINGS_MARKER = "AUSM_MAKEUP_112_ADAPTER";
    private static final String THAUMCRAFT_LIGHTS = "thaumcraft:nitor_black thaumcraft:nitor_red "
            + "thaumcraft:nitor_green thaumcraft:nitor_brown thaumcraft:nitor_blue thaumcraft:nitor_purple "
            + "thaumcraft:nitor_cyan thaumcraft:nitor_silver thaumcraft:nitor_gray thaumcraft:nitor_pink "
            + "thaumcraft:nitor_lime thaumcraft:nitor_yellow thaumcraft:nitor_lightblue thaumcraft:nitor_magenta "
            + "thaumcraft:nitor_orange thaumcraft:nitor_white thaumcraft:candle_black thaumcraft:candle_red "
            + "thaumcraft:candle_green thaumcraft:candle_brown thaumcraft:candle_blue thaumcraft:candle_purple "
            + "thaumcraft:candle_cyan thaumcraft:candle_silver thaumcraft:candle_gray thaumcraft:candle_pink "
            + "thaumcraft:candle_lime thaumcraft:candle_yellow thaumcraft:candle_lightblue thaumcraft:candle_magenta "
            + "thaumcraft:candle_orange thaumcraft:candle_white";

    private MakeupUltraFast112CompatibilityPatches() {
    }

    static void inject(Path pack) throws IOException {
        patchSettings(pack.resolve("shaders/lib/config.glsl"));
        patchLegacyVertexAttributes(pack.resolve("shaders/common/solid_blocks_vertex.glsl"));
        patchBlockMappings(pack.resolve("shaders/block.properties"));
        patchItemMappings(pack.resolve("shaders/item.properties"));
        exposeOptions(pack.resolve("shaders/shaders.properties"), pack.resolve("shaders/lang/en_US.lang"));
    }

    private static void patchLegacyVertexAttributes(Path vertex) throws IOException {
        if (!Files.isRegularFile(vertex)) return;
        String source = normalize(Files.readString(vertex));
        if (source.contains("attribute int blockEntityId;")) {
            // GLSL 1.20 has no integer generic vertex attributes. OptiFine/Iris
            // normally adapt this MakeUp declaration; AUSM consumes the same
            // numeric material channel as a legacy floating-point attribute.
            source = source.replace("attribute int blockEntityId;",
                    "// AUSM_MAKEUP_GLSL120_ATTRIBUTE\nattribute float blockEntityId;");
            Files.writeString(vertex, source);
        }
    }

    static void injectWavingDistanceLimit(Path pack) throws IOException {
        Path position = pack.resolve("shaders/src/position_vertex.glsl");
        if (!Files.isRegularFile(position)) return;
        String source = normalize(Files.readString(position));
        if (source.contains("AUSM_MAKEUP_WAVING_DISTANCE")) return;
        String anchor = "vec3 waveOffsetWorld = wave_move(worldpos.xzy) * weight * (0.03 + (rainStrength * .05));";
        if (!source.contains(anchor)) throw new IOException("Unsupported MakeUp waving layout: " + position);
        source = source.replace(anchor,
                "// AUSM_MAKEUP_WAVING_DISTANCE: avoid procedural noise outside the useful near field.\n"
                        + "                vec3 waveOffsetWorld = dot(position.xz, position.xz) <= AUSM_WAVING_DISTANCE * AUSM_WAVING_DISTANCE\n"
                        + "                        ? wave_move(worldpos.xzy) * weight * (0.03 + (rainStrength * .05))\n"
                        + "                        : vec3(0.0);");
        Files.writeString(position, source);
    }

    private static void patchSettings(Path settings) throws IOException {
        if (!Files.isRegularFile(settings)) return;
        String source = normalize(Files.readString(settings));
        if (source.contains(SETTINGS_MARKER)) return;
        String anchor = "// Options";
        if (!source.contains(anchor)) throw new IOException("Unsupported MakeUp settings layout: " + settings);
        source = source.replace(anchor, anchor + "\n"
                + "#define " + SETTINGS_MARKER + " 1 //[0 1]\n"
                + "#define AUSM_ASTRAL_NATIVE_CONSTELLATIONS 1 //[0 1]\n"
                + "#define ASTRAL_CONSTELLATION_STYLE 2 //[0 1 2]\n"
                + "#define AUSM_WAVING_DISTANCE 128.0 //[64.0 96.0 128.0 192.0 256.0]\n");
        Files.writeString(settings, source);
    }

    private static void patchBlockMappings(Path properties) throws IOException {
        if (!Files.isRegularFile(properties)) return;
        String source = normalize(Files.readString(properties));
        if (source.contains("# AUSM_MAKEUP_112_BLOCK_ALIASES")) return;
        source = appendMapping(source, "block", 10089,
                "randomthings:luminousblock randomthings:luminousstainedbrick "
                        + THAUMCRAFT_LIGHTS + " astralsorcery:blockcelestialcrystals "
                        + "astralsorcery:blockgemcrystals");
        source = appendMapping(source, "block", 10008,
                "astralsorcery:fluidliquidstarlight astralsorcery:blockliquidstarlight");
        source += "\n# AUSM_MAKEUP_112_BLOCK_ALIASES\n";
        Files.writeString(properties, source);
    }

    private static void patchItemMappings(Path properties) throws IOException {
        if (!Files.isRegularFile(properties)) return;
        String source = normalize(Files.readString(properties));
        if (source.contains("# AUSM_MAKEUP_112_ITEM_ALIASES")) return;
        source = appendMapping(source, "item", 11001,
                "randomthings:luminousblock " + THAUMCRAFT_LIGHTS + " "
                        + "astralsorcery:itembucketliquidstarlight");
        source += "\n# AUSM_MAKEUP_112_ITEM_ALIASES\n";
        Files.writeString(properties, source);
    }

    private static String appendMapping(String source, String type, int id, String values) throws IOException {
        Pattern pattern = Pattern.compile("(?m)^(\\s*" + type + "\\." + id + "\\s*=[^\\r\\n]*)$");
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) throw new IOException("MakeUp mapping " + type + "." + id + " is missing");
        StringBuffer patched = new StringBuffer(source.length() + values.length() * 2);
        do {
            matcher.appendReplacement(patched, Matcher.quoteReplacement(matcher.group(1) + " " + values));
        } while (matcher.find());
        matcher.appendTail(patched);
        return patched.toString();
    }

    private static void exposeOptions(Path properties, Path language) throws IOException {
        if (Files.isRegularFile(properties)) {
            String source = normalize(Files.readString(properties));
            if (!source.contains("screen.AUSM_SETTINGS=")) {
                source = source.replaceFirst("(?m)^(screen\\s*=.*)$", "$1 [AUSM_SETTINGS]");
                source += "\nscreen.AUSM_SETTINGS=<empty> <empty> AUSM_ASTRAL_NATIVE_CONSTELLATIONS ASTRAL_CONSTELLATION_STYLE AUSM_WAVING_DISTANCE\n";
                source = source.replaceFirst("(?m)^(sliders\\s*=)", "$1 AUSM_WAVING_DISTANCE");
                Files.writeString(properties, source);
            }
        }
        Files.createDirectories(language.getParent());
        String labels = Files.isRegularFile(language) ? normalize(Files.readString(language)) : "";
        if (!labels.contains("screen.AUSM_SETTINGS=")) {
            labels += "\nscreen.AUSM_SETTINGS=AUSM 1.12.2\n"
                    + "option.AUSM_ASTRAL_NATIVE_CONSTELLATIONS=Native Astral Constellations\n"
                    + "option.AUSM_ASTRAL_NATIVE_CONSTELLATIONS.comment=Render Astral Sorcery constellations through MakeUp's textured-sky pass.\n"
                    + "option.ASTRAL_CONSTELLATION_STYLE=Constellation Style\n"
                    + "value.ASTRAL_CONSTELLATION_STYLE.0=Original\n"
                    + "value.ASTRAL_CONSTELLATION_STYLE.1=Tier Color\n"
                    + "value.ASTRAL_CONSTELLATION_STYLE.2=Constellation Color\n"
                    + "option.AUSM_WAVING_DISTANCE=Waving Distance\n"
                    + "option.AUSM_WAVING_DISTANCE.comment=Maximum distance for MakeUp's procedural foliage animation.\n";
            Files.writeString(language, labels);
        }
    }

    private static String normalize(String source) {
        return source.replace("\r\n", "\n");
    }
}
