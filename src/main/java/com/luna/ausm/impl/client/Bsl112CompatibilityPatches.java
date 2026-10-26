package com.luna.ausm.impl.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** BSL-specific syntax and palette bindings for the reusable 1.12.2 features. */
final class Bsl112CompatibilityPatches {
    private static final String SETTINGS_MARKER = "AUSM_BSL_112_ADAPTER";
    private static final Map<Integer, String> LEGACY_BLOCK_ALIASES = legacyBlockAliases();

    private Bsl112CompatibilityPatches() {
    }

    static void inject(Path pack) throws IOException {
        patchSettings(pack.resolve("shaders/lib/settings.glsl"));
        patchColoredLightPropagation(pack.resolve("shaders/program/shadowcomp.glsl"));
        patchBlockMappings(pack.resolve("shaders/block.properties"));
        patchItemMappings(pack.resolve("shaders/item.properties"));
        exposeOptions(pack.resolve("shaders/shaders.properties"), pack.resolve("shaders/lang/en_US.lang"));
    }

    static void injectWavingDistanceLimit(Path pack) throws IOException {
        Path waving = pack.resolve("shaders/lib/vertex/waving.glsl");
        if (!Files.isRegularFile(waving)) return;
        String source = normalize(Files.readString(waving));
        if (source.contains("AUSM_BSL_WAVING_DISTANCE")) return;
        String anchor = "vec3 WavingBlocks(vec3 position, int blockID, float istopv) {";
        if (!source.contains(anchor)) throw new IOException("Unsupported BSL waving layout: " + waving);
        source = source.replace(anchor, anchor + "\n"
                + "    // AUSM_BSL_WAVING_DISTANCE: skip procedural noise beyond its useful range.\n"
                + "    if (dot(position.xz, position.xz) > AUSM_WAVING_DISTANCE * AUSM_WAVING_DISTANCE) return position;");
        Files.writeString(waving, source);
    }

    private static void patchSettings(Path settings) throws IOException {
        if (!Files.isRegularFile(settings)) return;
        String source = normalize(Files.readString(settings));
        if (source.contains(SETTINGS_MARKER)) return;
        String anchor = "//Shader Options//";
        if (!source.contains(anchor)) throw new IOException("Unsupported BSL settings layout: " + settings);
        source = source.replace(anchor, anchor + "\n"
                + "#define " + SETTINGS_MARKER + " 1 //[0 1]\n"
                + "#define AUSM_ASTRAL_NATIVE_CONSTELLATIONS 1 //[0 1]\n"
                + "#define ASTRAL_CONSTELLATION_STYLE 2 //[0 1 2]\n"
                + "#define AUSM_WAVING_DISTANCE 128.0 //[64.0 96.0 128.0 192.0 256.0]\n");
        Files.writeString(settings, source);
    }

    private static void patchColoredLightPropagation(Path shadowCompute) throws IOException {
        if (!Files.isRegularFile(shadowCompute)) return;
        String source = Files.readString(shadowCompute);
        String patched = IsotropicLightDiffusionPatch.patchBsl(source);
        if (patched.equals(normalize(source)) && !patched.contains("AUSM_ISOTROPIC_ACT_DIFFUSION")) {
            throw new IOException("Unsupported BSL colored-light compute layout: " + shadowCompute);
        }
        if (!patched.equals(source)) Files.writeString(shadowCompute, patched);
    }

    private static void patchBlockMappings(Path properties) throws IOException {
        if (!Files.isRegularFile(properties)) return;
        String source = normalize(Files.readString(properties));
        if (source.contains("# AUSM_BSL_112_BLOCK_ALIASES")) return;
        int branch = source.indexOf("#1.8-1.12 Mapping");
        int branchEnd = source.indexOf("\n#else", branch);
        if (branch < 0 || branchEnd < 0) throw new IOException("Unsupported BSL legacy block mapping layout: " + properties);
        StringBuilder aliases = new StringBuilder("\n# AUSM_BSL_112_BLOCK_ALIASES\n");
        LEGACY_BLOCK_ALIASES.forEach((id, values) -> aliases.append("block.").append(id).append("= ").append(values).append('\n'));
        source = source.substring(0, branchEnd) + aliases + source.substring(branchEnd);
        Files.writeString(properties, source);
    }

    private static void patchItemMappings(Path properties) throws IOException {
        if (!Files.isRegularFile(properties)) return;
        String source = normalize(Files.readString(properties));
        if (source.contains("# AUSM_BSL_112_ITEM_ALIASES")) return;
        source += """

#if MC_VERSION >= 10800 && MC_VERSION < 11300
# AUSM_BSL_112_ITEM_ALIASES
item.125= minecraft:end_rod randomthings:luminousblock:0 randomthings:luminousblock:8 thaumcraft:nitor_black thaumcraft:nitor_gray thaumcraft:nitor_silver thaumcraft:nitor_white
item.126= minecraft:redstone_torch
item.139= minecraft:torch
item.114= minecraft:lit_pumpkin thaumcraft:nitor_brown thaumcraft:nitor_orange
item.214= minecraft:glowstone
item.219= minecraft:sea_lantern
item.225= minecraft:beacon
item.300= minecraft:magma
item.120= astralsorcery:itembucketliquidstarlight thaumcraft:nitor_lightblue
item.102= randomthings:luminousblock:1 randomthings:luminousblock:12
item.111= randomthings:luminousblock:2 randomthings:luminousblock:6
item.108= randomthings:luminousblock:3
item.150= randomthings:luminousblock:7 randomthings:luminousblock:15
item.107= randomthings:luminousblock:9 thaumcraft:nitor_cyan
item.110= randomthings:luminousblock:10 thaumcraft:nitor_purple
item.109= randomthings:luminousblock:11 thaumcraft:nitor_blue
item.105= randomthings:luminousblock:13 thaumcraft:nitor_green
item.101= randomthings:luminousblock:14 thaumcraft:nitor_red
item.123= thaumcraft:nitor_magenta thaumcraft:nitor_pink
item.104= randomthings:luminousblock:5 thaumcraft:nitor_lime
item.103= randomthings:luminousblock:4 thaumcraft:nitor_yellow
#endif
""";
        Files.writeString(properties, source);
    }

    private static void exposeOptions(Path properties, Path language) throws IOException {
        if (Files.isRegularFile(properties)) {
            String source = normalize(Files.readString(properties));
            if (!source.contains("screen.AUSM_SETTINGS=")) {
                source = source.replaceFirst("(?m)^screen=([^\\r\\n]*)$", "screen=$1 [AUSM_SETTINGS]");
                source += "\nscreen.AUSM_SETTINGS=<empty> <empty> AUSM_ASTRAL_NATIVE_CONSTELLATIONS ASTRAL_CONSTELLATION_STYLE AUSM_WAVING_DISTANCE\n";
                source = source.replaceFirst("(?m)^sliders=", "sliders=AUSM_WAVING_DISTANCE ");
                Files.writeString(properties, source);
            }
        }
        Files.createDirectories(language.getParent());
        String labels = Files.isRegularFile(language) ? normalize(Files.readString(language)) : "";
        if (!labels.contains("screen.AUSM_SETTINGS=")) {
            labels += "\nscreen.AUSM_SETTINGS=AUSM 1.12.2\n"
                    + "option.AUSM_ASTRAL_NATIVE_CONSTELLATIONS=Native Astral Constellations\n"
                    + "option.AUSM_ASTRAL_NATIVE_CONSTELLATIONS.comment=Render Astral Sorcery constellations through BSL's textured-sky pass.\n"
                    + "option.ASTRAL_CONSTELLATION_STYLE=Constellation Style\n"
                    + "value.ASTRAL_CONSTELLATION_STYLE.0=Original\n"
                    + "value.ASTRAL_CONSTELLATION_STYLE.1=Tier Color\n"
                    + "value.ASTRAL_CONSTELLATION_STYLE.2=Constellation Color\n"
                    + "option.AUSM_WAVING_DISTANCE=Waving Distance\n"
                    + "option.AUSM_WAVING_DISTANCE.comment=Maximum distance for BSL's procedural block animation.\n";
            Files.writeString(language, labels);
        }
    }

    private static Map<Integer, String> legacyBlockAliases() {
        Map<Integer, String> aliases = new LinkedHashMap<>();
        aliases.put(15025, "randomthings:luminousblock:0 randomthings:luminousblock:8 randomthings:luminousstainedbrick:0 randomthings:luminousstainedbrick:8");
        aliases.put(15002, "randomthings:luminousblock:1 randomthings:luminousblock:12 randomthings:luminousstainedbrick:1 randomthings:luminousstainedbrick:12");
        aliases.put(15011, "randomthings:luminousblock:2 randomthings:luminousblock:6 randomthings:luminousstainedbrick:2 randomthings:luminousstainedbrick:6");
        aliases.put(15008, "randomthings:luminousblock:3 randomthings:luminousstainedbrick:3");
        aliases.put(15003, "randomthings:luminousblock:4 randomthings:luminousstainedbrick:4 thaumcraft:candle_yellow thaumcraft:nitor_yellow");
        aliases.put(15004, "randomthings:luminousblock:5 randomthings:luminousstainedbrick:5 thaumcraft:candle_lime thaumcraft:nitor_lime");
        aliases.put(15050, "randomthings:luminousblock:7 randomthings:luminousblock:15 randomthings:luminousstainedbrick:7 randomthings:luminousstainedbrick:15");
        aliases.put(15007, "randomthings:luminousblock:9 randomthings:luminousstainedbrick:9 thaumcraft:candle_cyan thaumcraft:nitor_cyan");
        aliases.put(15010, "randomthings:luminousblock:10 randomthings:luminousstainedbrick:10 thaumcraft:candle_purple thaumcraft:nitor_purple");
        aliases.put(15009, "randomthings:luminousblock:11 randomthings:luminousstainedbrick:11 thaumcraft:candle_blue thaumcraft:nitor_blue");
        aliases.put(15005, "randomthings:luminousblock:13 randomthings:luminousstainedbrick:13 thaumcraft:candle_green thaumcraft:nitor_green");
        aliases.put(15001, "randomthings:luminousblock:14 randomthings:luminousstainedbrick:14 thaumcraft:candle_red thaumcraft:nitor_red");
        aliases.put(15020, "thaumcraft:candle_lightblue thaumcraft:nitor_lightblue");
        aliases.put(15023, "thaumcraft:candle_magenta thaumcraft:nitor_magenta thaumcraft:candle_pink thaumcraft:nitor_pink");
        aliases.put(15014, "thaumcraft:candle_brown thaumcraft:nitor_brown thaumcraft:candle_orange thaumcraft:nitor_orange");
        aliases.put(15025, aliases.get(15025) + " thaumcraft:candle_black thaumcraft:nitor_black thaumcraft:candle_gray thaumcraft:nitor_gray thaumcraft:candle_silver thaumcraft:nitor_silver thaumcraft:candle_white thaumcraft:nitor_white");
        aliases.put(15020, aliases.get(15020) + " astralsorcery:blockcelestialcrystals astralsorcery:blockgemcrystals:stage=stage_2_sky astralsorcery:blockgemcrystals:stage=stage_1 astralsorcery:blockgemcrystals:stage=stage_0");
        aliases.put(15014, aliases.get(15014) + " astralsorcery:blockgemcrystals:stage=stage_2_day");
        aliases.put(15021, "astralsorcery:blockgemcrystals:stage=stage_2_night");
        aliases.put(20500, "astralsorcery:fluidliquidstarlight astralsorcery:blockliquidstarlight");
        return aliases;
    }

    private static String normalize(String source) {
        return source.replace("\r\n", "\n");
    }
}
