package com.luna.ausm.impl.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Routes native finite sky details through MakeUp's textured-sky program. */
final class MakeupUltraFastAstralConstellationPatch {
    private static final String INCLUDE = "#include \"/lib/ausm/makeupAstralConstellations.glsl\"";
    private static final String RESOURCE = "/assets/ausm/shaderpatches/makeupAstralConstellations.glsl";

    private MakeupUltraFastAstralConstellationPatch() {
    }

    static void inject(Path pack) throws IOException {
        Path vertex = pack.resolve("shaders/common/skytextured_vertex.glsl");
        Path fragment = pack.resolve("shaders/common/skytextured_fragment.glsl");
        if (!Files.isRegularFile(vertex) || !Files.isRegularFile(fragment)) return;

        String vertexSource = normalize(Files.readString(vertex));
        String fragmentSource = normalize(Files.readString(fragment));
        if (!vertexSource.contains("AUSM_MAKEUP_ASTRAL_CONSTELLATIONS")) {
            String position = "gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;";
            if (!vertexSource.contains(position) || !vertexSource.contains("#include \"/lib/config.glsl\"")) {
                throw new IOException("Unsupported MakeUp skytextured vertex layout: " + vertex);
            }
            vertexSource = vertexSource.replace("#include \"/lib/config.glsl\"",
                    "#include \"/lib/config.glsl\"\n#define AUSM_MAKEUP_VERTEX\n" + INCLUDE);
            vertexSource = vertexSource.replace(position,
                    "// AUSM_MAKEUP_ASTRAL_CONSTELLATIONS\n"
                            + "    gl_Position = AusmMakeupAstralConstellationPosition();");
            Files.writeString(vertex, vertexSource);
        }

        if (!fragmentSource.contains("AUSM_MAKEUP_ASTRAL_CONSTELLATIONS")) {
            String write = "    #include \"/src/writebuffers.glsl\"";
            if (!fragmentSource.contains(write) || !fragmentSource.contains("#include \"/lib/config.glsl\"")) {
                throw new IOException("Unsupported MakeUp skytextured fragment layout: " + fragment);
            }
            fragmentSource = fragmentSource.replace("#include \"/lib/config.glsl\"",
                    "#include \"/lib/config.glsl\"\n#define AUSM_MAKEUP_FRAGMENT\n" + INCLUDE);
            fragmentSource = fragmentSource.replace(write,
                    "    // AUSM_MAKEUP_ASTRAL_CONSTELLATIONS\n"
                            + "    if (ausmSkyDetailKind >= 1 && ausmSkyDetailKind <= 3) {\n"
                            + "        blockColor = texture2D(tex, texcoord) * tintColor;\n"
                            + "    } else if (ausmSkyDetailKind == 5) {\n"
                            + "        blockColor = AusmMakeupAstralConstellationColor(\n"
                            + "                texture2D(tex, texcoord) * tintColor, texcoord);\n"
                            + "    }\n\n"
                            + write);
            Files.writeString(fragment, fragmentSource);
        }

        Path helper = pack.resolve("shaders/lib/ausm/makeupAstralConstellations.glsl");
        Files.createDirectories(helper.getParent());
        try (InputStream stream = MakeupUltraFastAstralConstellationPatch.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("Missing bundled MakeUp Astral shader patch");
            Files.write(helper, stream.readAllBytes());
        }
    }

    private static String normalize(String source) {
        return source.replace("\r\n", "\n");
    }
}
