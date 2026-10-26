package com.luna.ausm.impl.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ports Entree features while preserving each upstream pack's shader API. */
final class EntreeCompatibilityPatches {
    private static final String OPTIONS = "#include \"/lib/ausm/entreeOptions.glsl\"";
    private static final String SKY = "#include \"/lib/ausm/entreeSky.glsl\"";
    private static final String MATERIALS = "#include \"/lib/materials/materialHandling/terrainMaterials.glsl\"";
    private static final String TRANSLUCENTS = "#include \"/lib/materials/materialHandling/translucentMaterials.glsl\"";
    private static final Pattern COATING = Pattern.compile("(?m)^(\\h*)(CoatTextures\\([^;]+;)[^\\r\\n]*");

    private EntreeCompatibilityPatches() {
    }

    static void inject(Path pack) throws IOException {
        copyResource("entreeOptions.glsl", pack.resolve("shaders/lib/ausm/entreeOptions.glsl"));
        Path common = pack.resolve("shaders/lib/common.glsl");
        if (Files.isRegularFile(common)) {
            String source = Files.readString(common);
            if (!source.contains(OPTIONS)) Files.writeString(common, OPTIONS + "\n" + source);
        }
        patchEntities(pack.resolve("shaders/program/gbuffers_entities.glsl"));
        patchMaterials(pack);
        patchLightPalette(pack);
        patchColoredLightPropagation(pack);
        patchSky(pack);
        exposeOptions(pack);
    }

    /** Removes the directional lobes produced by Euphoria's six-axis ACT diffusion. */
    private static void patchColoredLightPropagation(Path pack) throws IOException {
        Path shadowCompute = pack.resolve("shaders/program/shadowcomp.glsl");
        if (!Files.isRegularFile(shadowCompute)) return;
        String source = read(shadowCompute);
        String patched = IsotropicLightDiffusionPatch.patchEuphoria(source);
        if (!patched.equals(source)) Files.writeString(shadowCompute, patched);
    }

    private static void patchLightPalette(Path pack) throws IOException {
        Path voxels = pack.resolve("shaders/lib/voxelization/lightVoxelization.glsl");
        if (Files.isRegularFile(voxels)) {
            String source = read(voxels);
            if (!source.contains("AUSM_ENTREE_LIGHT_IDS")) {
                source = source.replace("int GetVoxelIDs(int mat) {", """
                        int GetVoxelIDs(int mat) {
                            // AUSM_ENTREE_LIGHT_IDS: same palette as the CPU fallback.
                            if (mat == 12003 || mat == 12283) return 3;
                            if (mat >= 12070 && mat <= 12080) return mat - 12000;
                            if (mat >= 12270 && mat <= 12280) return mat - 12160;
                        """);
                Files.writeString(voxels, source);
            }
        }
        Path colors = pack.resolve("shaders/lib/colors/blocklightColorsACT.glsl");
        if (Files.isRegularFile(colors)) {
            String source = read(colors);
            if (!source.contains("AUSM_ENTREE_LIGHT_COLORS")) {
                source = source.replace("vec4 GetSpecialBlocklightColor(int mat) {", """
                        vec4 GetSpecialBlocklightColor(int mat) {
                            // AUSM_ENTREE_LIGHT_COLORS: legacy Entree emitter palette.
                            if (mat == 110) return vec4(vec3(1.0, 0.18, 0.14) * 4.0, 0.0);
                            if (mat == 111) return vec4(vec3(1.0, 0.48, 0.16) * 4.0, 0.0);
                            if (mat == 112) return vec4(vec3(1.0, 0.88, 0.18) * 4.0, 0.0);
                            if (mat == 113) return vec4(vec3(0.46, 1.0, 0.18) * 4.0, 0.0);
                            if (mat == 114) return vec4(vec3(0.18, 0.95, 0.28) * 4.0, 0.0);
                            if (mat == 115) return vec4(vec3(0.12, 0.90, 1.0) * 4.0, 0.0);
                            if (mat == 116) return vec4(vec3(0.42, 0.70, 1.0) * 4.0, 0.0);
                            if (mat == 117) return vec4(vec3(0.18, 0.30, 1.0) * 4.0, 0.0);
                            if (mat == 118) return vec4(vec3(0.62, 0.24, 1.0) * 4.0, 0.0);
                            if (mat == 119) return vec4(vec3(1.0, 0.20, 0.92) * 4.0, 0.0);
                            if (mat == 120) return vec4(vec3(1.0, 0.52, 0.78) * 4.0, 0.0);
                        """);
                Files.writeString(colors, source);
            }
        }
    }

    private static void patchEntities(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        String source = read(file);
        if (source.contains("ausmUnlitAstralOverlay")) return;
        String original = source;
        source = source.replace("#ifdef FRAGMENT_SHADER", "#ifdef FRAGMENT_SHADER\nuniform int ausmAstralEffectOverlay;");
        source = source.replace("float alphaCheck = color.a;", "bool ausmUnlitAstralOverlay = ausmAstralEffectOverlay == 1;\n    float alphaCheck = color.a;")
                .replace("if (alphaCheck > 0.001)", "if (alphaCheck > 0.001 && !ausmUnlitAstralOverlay)");
        // Keep fogAlpha in its original scope; other Euphoria features use it.
        source = source.replaceAll("(?m)^(\\h*)(DoFog\\(color,[^;]+;)",
                "$1if (!ausmUnlitAstralOverlay) $2");
        if (!source.contains("bool ausmUnlitAstralOverlay")) {
            throw new IOException("Unsupported Astral effect shader layout: " + file);
        }
        if (!source.equals(original)) Files.writeString(file, source);
    }

    private static void patchMaterials(Path pack) throws IOException {
        Path programs = pack.resolve("shaders/program");
        Path terrain = programs.resolve("gbuffers_terrain.glsl");
        if (Files.isRegularFile(terrain)) {
            String source = read(terrain);
            if (!source.contains("AUSM_ENTREE_EMISSION")) {
                if (!source.contains(MATERIALS)) throw new IOException("Unsupported terrain material layout: " + terrain);
                source = source.replace(MATERIALS, MATERIALS + "\n" + """
                            // AUSM_ENTREE_EMISSION: legacy Entree emissive materials.
                            if (mat >= 12270 && mat <= 12283) {
                                noSmoothLighting = true;
                                noDirectionalShading = true;
                                emission = max(emission, GetLuminance(color.rgb) * 2.5);
                            }
                        """);
                source = guardCoating(source, "emission <= 0.00001");
                Files.writeString(terrain, source);
            }
        }
        Path block = programs.resolve("gbuffers_block.glsl");
        if (Files.isRegularFile(block)) {
            String source = read(block);
            if (!source.contains("AUSM_ENTREE_COATING")) {
                Files.writeString(block, guardCoating(source, "blockEntityId != 10028 && emission <= 0.00001"));
            }
        }
        Path water = programs.resolve("gbuffers_water.glsl");
        if (Files.isRegularFile(water)) {
            String source = read(water);
            if (!source.contains("AUSM_ENTREE_FLUIDS")) {
                if (!source.contains(TRANSLUCENTS)) throw new IOException("Unsupported translucent material layout: " + water);
                // This wrapper covers both IPBR and custom-PBR routes. IDs are
                // supplied by AUSM's fluid registry fallback, including Starlight.
                source = source.replace(TRANSLUCENTS, """
                            // AUSM_ENTREE_FLUIDS: preserve the mod's sprite and tint.
                            if (mat >= 32620 && mat <= 32645) {
                                #include "/lib/ausm/coloredFluid.glsl"
                            } else {
                                """ + TRANSLUCENTS + "\n    }\n");
                copyOverlay("shaders/lib/materials/specificMaterials/translucents/coloredFluid.glsl",
                        pack.resolve("shaders/lib/ausm/coloredFluid.glsl"));
                Files.writeString(water, source);
            }
        }
    }

    private static String guardCoating(String source, String condition) {
        if (source.contains("AUSM_ENTREE_COATING")) return source;
        Matcher matcher = COATING.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1)
                    + "// AUSM_ENTREE_COATING\n" + matcher.group(1)
                    + "if (" + condition + ") { " + matcher.group(2) + " }"));
        }
        return matcher.appendTail(result).toString();
    }

    private static void patchSky(Path pack) throws IOException {
        Path sky = pack.resolve("shaders/program/gbuffers_skybasic.glsl");
        if (!Files.isRegularFile(sky)) return;
        String source = read(sky);
        if (!source.contains("AUSM_ENTREE_SKY")) {
            // Retained Entree outputs already own their void branch; ordinary
            // derivatives need the same implementation as an isolated include.
            if (!source.contains("AusmOwnedSkyColor")) {
                copyOverlay("shaders/lib/atmospherics/ausmOwnedSky.glsl",
                        pack.resolve("shaders/lib/atmospherics/ausmOwnedSky.glsl"));
                copyResource("entreeSky.glsl", pack.resolve("shaders/lib/ausm/entreeSky.glsl"));
                source = source.replaceFirst(Pattern.quote("//Program//"), Matcher.quoteReplacement(SKY + "\n\n//Program//"));
                String main = "void main() {";
                int at = source.indexOf(main);
                if (!source.contains(SKY) || at < 0) throw new IOException("Unsupported sky shader layout: " + sky);
                source = source.substring(0, at + main.length()) + "\n" + """
                            // AUSM_ENTREE_SKY
                            if (ausmSimpleVoidWorld > 0) {
                                vec3 ray = AusmEntreeViewRay();
                                vec3 starlessSky;
                                vec3 skyUp = normalize(gbufferModelView[1].xyz);
                                vec3 skySun = AusmOwnedSkySunVector();
                                vec3 sky = AusmOwnedSkyColor(ray, skyUp, skySun, starlessSky);
                                vec3 withCelestials = AusmApplyComplementaryCelestials(sky, starlessSky, ray, skySun, skyUp);
                                sky = mix(sky, withCelestials, AUSM_VOID_CELESTIAL_BRIGHTNESS * 0.01);
                                gl_FragData[0] = vec4(sky, 1.0);
                                return;
                            }
                            #if AUSM_ABYSSAL_SKYBOX == 1 && defined AUSM_DREADLANDS
                                float height = smoothstep(-0.34, 0.78,
                                        dot(AusmEntreeViewRay(), normalize(gbufferModelView[1].xyz)));
                                vec3 sky = mix(vec3(0.17, 0.010, 0.012), vec3(0.52, 0.040, 0.018), height);
                                sky *= (1.0 - rainFactor2 * 0.45) * (1.0 - maxBlindnessDarkness);
                                gl_FragData[0] = vec4(sky, 1.0);
                                return;
                            #endif
                        """ + source.substring(at + main.length());
            }
            Files.writeString(sky, source);
        }
    }

    private static void exposeOptions(Path pack) throws IOException {
        Path file = pack.resolve("shaders/shaders.properties");
        if (!Files.isRegularFile(file)) return;
        String source = Files.readString(file);
        if (!source.contains("screen.AUSM_MODDED_DIMENSIONS=")) {
            source = source.replaceFirst("(?m)^(\\h*screen\\.AUSM_SETTINGS=[^\\r\\n]*)", "$1 [AUSM_MODDED_DIMENSIONS]");
            source += "\nscreen.AUSM_MODDED_DIMENSIONS=[AUSM_SIMPLE_VOID_SETTINGS] [AUSM_ABYSSALCRAFT_SETTINGS]\n"
                    + "screen.AUSM_SIMPLE_VOID_SETTINGS=AUSM_VOID_CELESTIALS AUSM_VOID_STARS AUSM_VOID_ASTRAL_STARS AUSM_VOID_NEBULA AUSM_VOID_CELESTIAL_BRIGHTNESS AUSM_VOID_STAR_BRIGHTNESS AUSM_VOID_ASTRAL_STAR_BRIGHTNESS AUSM_VOID_NEBULA_BRIGHTNESS\n"
                    + "screen.AUSM_ABYSSALCRAFT_SETTINGS=AUSM_ABYSSAL_SKYBOX AUSM_ABYSSAL_SUNLIGHT\n";
        }
        Files.writeString(file, source);
        Path language = pack.resolve("shaders/lang/en_US.lang");
        Files.createDirectories(language.getParent());
        String labels = Files.isRegularFile(language) ? Files.readString(language) : "";
        String bundled;
        try (InputStream stream = required("/assets/ausm/euphoria_entree_overlay/shaders/lang/en_US.lang")) {
            bundled = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String line : bundled.split("\\R")) {
            if ((line.startsWith("screen.AUSM_") || line.startsWith("option.AUSM_")) && line.contains("=")) {
                String key = line.substring(0, line.indexOf('=') + 1);
                if (!labels.contains(key)) labels += "\n" + line;
            }
        }
        Files.writeString(language, labels);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file).replace("\r\n", "\n");
    }

    private static void copyResource(String name, Path target) throws IOException {
        copy("/assets/ausm/shaderpatches/" + name, target);
    }

    private static void copyOverlay(String name, Path target) throws IOException {
        copy("/assets/ausm/euphoria_entree_overlay/" + name, target);
    }

    private static void copy(String resource, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream stream = required(resource)) {
            Files.write(target, stream.readAllBytes());
        }
    }

    private static InputStream required(String resource) throws IOException {
        InputStream stream = EntreeCompatibilityPatches.class.getResourceAsStream(resource);
        if (stream == null) throw new IOException("Missing bundled shader patch: " + resource);
        return stream;
    }
}
