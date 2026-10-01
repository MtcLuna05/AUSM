package com.luna.ausm.impl.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Matches Complementary's glint geometry to its base item passes. */
final class ComplementaryGlintPatch {
    private static final String MARKER = "// AUSM item glint depth";
    private static final String POSITION = "    gl_Position = ftransform();";
    private static final String HAND_CHECK = "if (gl_ProjectionMatrix[2][2] > -0.5)";
    private static final String WORLD_BIAS = """
                #ifdef FLICKERING_FIX
                    gl_Position.z -= 0.000002;
                #endif
            """;

    private ComplementaryGlintPatch() {
    }

    static void inject(Path pack, String name) throws IOException {
        if (!name.startsWith("ComplementaryReimagined_") && !name.startsWith("ComplementaryUnbound_")) {
            return;
        }
        Path programs = pack.resolve("shaders/program");
        Path glint = programs.resolve("gbuffers_armor_glint.glsl");
        Path textured = programs.resolve("gbuffers_textured.glsl");
        Path hand = programs.resolve("gbuffers_hand.glsl");
        if (!Files.isRegularFile(glint) || !Files.isRegularFile(textured) || !Files.isRegularFile(hand)) {
            return;
        }
        String source = Files.readString(glint);
        String patched = patch(source, Files.readString(textured), Files.readString(hand));
        if (!patched.equals(source)) {
            Files.writeString(glint, patched);
        }
    }

    static String patch(String source, String textured, String hand) {
        String glint = source.replace("\r\n", "\n");
        // Only edit the known base/glint layout; a different depth scheme needs review.
        if (glint.contains(MARKER)
                || !glint.contains("#ifdef VERTEX_SHADER\n")
                || !glint.contains(POSITION) || !glint.contains(HAND_CHECK)
                || glint.contains("ausmItemGlintMask") || glint.contains("gl_Position.z")
                || !textured.replace("\r\n", "\n").contains(WORLD_BIAS)
                || !hand.contains(POSITION) || hand.contains("gl_Position.z")) {
            return source;
        }
        return glint.replace("#ifdef VERTEX_SHADER\n", "#ifdef VERTEX_SHADER\nuniform int ausmItemGlintMask;\n")
                .replace(POSITION, POSITION + "\n\n" + "    " + MARKER + "\n" + """
                            // AUSM compresses hand depth with glDepthRange, not the projection matrix.
                            #ifdef FLICKERING_FIX
                                if (ausmItemGlintMask == 1 && gl_DepthRange.diff > 0.5) {
                                    gl_Position.z -= 0.000002;
                                }
                            #endif
                        """)
                .replace(HAND_CHECK, "if (gl_DepthRange.diff < 0.5)");
    }
}
