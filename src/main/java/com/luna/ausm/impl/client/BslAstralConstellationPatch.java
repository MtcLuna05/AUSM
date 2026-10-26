package com.luna.ausm.impl.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Routes AUSM sky-detail phases through BSL's textured-sky program. */
final class BslAstralConstellationPatch {
    private static final String INCLUDE = "#include \"/lib/ausm/bslAstralConstellations.glsl\"";
    private static final String RESOURCE = "/assets/ausm/shaderpatches/bslAstralConstellations.glsl";

    private BslAstralConstellationPatch() {
    }

    static void inject(Path pack) throws IOException {
        Path program = pack.resolve("shaders/program/gbuffers_skytextured.glsl");
        if (!Files.isRegularFile(program)) return;
        String source = Files.readString(program).replace("\r\n", "\n");
        if (source.contains("AUSM_BSL_ASTRAL_CONSTELLATIONS")) return;
        if (!source.contains("#ifdef FSH") || !source.contains("#ifdef VSH")
                || !source.contains("vec4 albedo = texture2D(texture, texCoord) * color;")
                || !source.contains("gl_Position = ftransform();")
                || !source.contains("#include \"/lib/settings.glsl\"")) {
            throw new IOException("Unsupported BSL Astral skytextured layout: " + program);
        }
        source = source.replace("#include \"/lib/settings.glsl\"",
                "#include \"/lib/settings.glsl\"\n" + INCLUDE);
        source = source.replace("void main() {\n\tvec4 albedo = texture2D(texture, texCoord) * color;", """
                void main() {
	// AUSM_BSL_ASTRAL_CONSTELLATIONS
	if (ausmSkyDetailKind >= 1 && ausmSkyDetailKind <= 3) {
		vec4 detail = texture2D(texture, texCoord) * color;
		if (detail.a <= 0.001) discard;
		gl_FragData[0] = detail;
		return;
	}
	if (ausmSkyDetailKind == 5) {
		gl_FragData[0] = AusmBslAstralConstellationColor(texture2D(texture, texCoord) * color, texCoord);
		return;
	}
	vec4 albedo = texture2D(texture, texCoord) * color;
                """);
        source = source.replace("gl_Position = ftransform();",
                "gl_Position = AusmBslAstralConstellationPosition(gbufferModelView);");
        Path helper = pack.resolve("shaders/lib/ausm/bslAstralConstellations.glsl");
        Files.createDirectories(helper.getParent());
        try (InputStream stream = BslAstralConstellationPatch.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("Missing bundled BSL Astral shader patch");
            Files.write(helper, stream.readAllBytes());
        }
        Files.writeString(program, source);
    }
}
