package com.luna.ausm.impl.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds Astral support without replacing Complementary/Euphoria's shader API. */
final class AstralConstellationPatch {
    private static final String INCLUDE = "#include \"/lib/ausm/astralConstellations.glsl\"";
    private static final String RESOURCE = "/assets/ausm/shaderpatches/astralConstellations.glsl";
    private static final Pattern MAIN = Pattern.compile("void\\s+main\\s*\\(\\s*\\)\\s*\\{");

    private AstralConstellationPatch() {
    }

    static void inject(Path pack) throws IOException {
        Path program = pack.resolve("shaders/program/gbuffers_skytextured.glsl");
        if (!Files.isRegularFile(program)) {
            return;
        }
        String original = Files.readString(program);
        // Entree's maintained overlay already implements this contract.
        if (!original.contains("AusmAstralConstellationColor") && !original.contains(INCLUDE)) {
            String source = original.replace("\r\n", "\n");
            int fragment = source.indexOf("#ifdef FRAGMENT_SHADER");
            int vertex = source.indexOf("#ifdef VERTEX_SHADER");
            if (fragment < 0 || vertex <= fragment || !source.contains("gl_Position = ftransform();")) {
                throw new IOException("Unsupported Astral skytextured shader layout in " + pack);
            }
            Matcher main = MAIN.matcher(source);
            if (!main.find(fragment) || main.start() >= vertex) {
                throw new IOException("Missing Astral skytextured fragment entrypoint in " + pack);
            }
            String branch = "\n    // AUSM Astral constellations bypass sun/moon classification and the 1.12 custom-sky discard.\n"
                    + "    if (ausmSkyDetailKind >= 1 && ausmSkyDetailKind <= 3) {\n"
                    + "        vec4 detail = texture2D(tex, texCoord) * glColor;\n"
                    + "        if (detail.a <= 0.001) discard;\n"
                    + "        gl_FragData[0] = detail;\n        return;\n    }\n"
                    + "    if (ausmSkyDetailKind == 5) {\n"
                    + "        gl_FragData[0] = AusmAstralConstellationColor(texture2D(tex, texCoord) * glColor, texCoord);\n"
                    + "        return;\n    }\n";
            source = source.substring(0, main.end()) + branch + source.substring(main.end());
            source = source.replace("gl_Position = ftransform();", "gl_Position = AusmAstralConstellationPosition();");
            source = source.replace("#include \"/lib/common.glsl\"", "#include \"/lib/common.glsl\"\n" + INCLUDE);
            if (!source.contains(INCLUDE)) {
                throw new IOException("Missing common shader include in " + pack);
            }
            Path helper = pack.resolve("shaders/lib/ausm/astralConstellations.glsl");
            Files.createDirectories(helper.getParent());
            try (InputStream stream = AstralConstellationPatch.class.getResourceAsStream(RESOURCE)) {
                if (stream == null) throw new IOException("Missing bundled Astral shader patch");
                Files.write(helper, stream.readAllBytes());
            }
            Files.writeString(program, source);
        }
        exposeOptions(pack);
    }

    private static void exposeOptions(Path pack) throws IOException {
        Path properties = pack.resolve("shaders/shaders.properties");
        if (!Files.isRegularFile(properties)) return;
        String source = Files.readString(properties);
        if (!source.contains("screen.AUSM_ASTRAL_SETTINGS=")) {
            source = source.replaceFirst("(?m)^(\\s*screen\\.AUSM_SETTINGS=[^\\r\\n]*)",
                    "$1 [AUSM_ASTRAL_SETTINGS]");
            source += "\nscreen.AUSM_ASTRAL_SETTINGS=<empty> <empty> AUSM_ASTRAL_NATIVE_CONSTELLATIONS ASTRAL_CONSTELLATION_STYLE\n";
            Files.writeString(properties, source);
        }
        Path language = pack.resolve("shaders/lang/en_US.lang");
        Files.createDirectories(language.getParent());
        String labels = Files.isRegularFile(language) ? Files.readString(language) : "";
        if (!labels.contains("screen.AUSM_ASTRAL_SETTINGS=")) {
            labels += "\nscreen.AUSM_ASTRAL_SETTINGS=Astral Sorcery\n"
                    + "option.AUSM_ASTRAL_NATIVE_CONSTELLATIONS=Native Astral Constellations\n"
                    + "option.AUSM_ASTRAL_NATIVE_CONSTELLATIONS.comment=Render Astral Sorcery constellations in dimensions with a native Astral sky.\n";
        }
        if (!labels.contains("option.ASTRAL_CONSTELLATION_STYLE=")) {
            labels += "option.ASTRAL_CONSTELLATION_STYLE=Constellation Style\n"
                    + "option.ASTRAL_CONSTELLATION_STYLE.comment=Use the original appearance, tier color, or the constellation's ritual color.\n"
                    + "value.ASTRAL_CONSTELLATION_STYLE.0=Original\n"
                    + "value.ASTRAL_CONSTELLATION_STYLE.1=Tier Color\n"
                    + "value.ASTRAL_CONSTELLATION_STYLE.2=Constellation Color\n";
        }
        Files.writeString(language, labels, StandardCharsets.UTF_8);
    }
}
