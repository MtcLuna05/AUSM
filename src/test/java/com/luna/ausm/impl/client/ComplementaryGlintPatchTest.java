package com.luna.ausm.impl.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ComplementaryGlintPatchTest {
    private static final String GLINT = """
            #ifdef VERTEX_SHADER
            void main() {
                gl_Position = ftransform();
                texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                #if HAND_SWAYING > 0
                    if (gl_ProjectionMatrix[2][2] > -0.5) {
                        #include "/lib/misc/handSway.glsl"
                    }
                #endif
            }
            #endif
            """;
    private static final String HAND = "    gl_Position = ftransform();\n";
    private static final String TEXTURED = HAND + """
                #ifdef FLICKERING_FIX
                    gl_Position.z -= 0.000002;
                #endif
            """;

    @Test
    void scopesDepthBiasToWorldItemsAndSwayToHands() {
        String patched = ComplementaryGlintPatch.patch(GLINT, TEXTURED, HAND);
        assertTrue(patched.contains("ausmItemGlintMask == 1 && gl_DepthRange.diff > 0.5"));
        assertTrue(patched.contains("if (gl_DepthRange.diff < 0.5)"));
        assertTrue(patched.contains("#ifdef FLICKERING_FIX"));
        assertTrue(patched.contains("gl_TextureMatrix[0] * gl_MultiTexCoord0"));
        assertFalse(patched.contains("gl_ProjectionMatrix[2][2]"));
        assertEquals(patched, ComplementaryGlintPatch.patch(patched, TEXTURED, HAND));
        assertEquals(patched, ComplementaryGlintPatch.patch(GLINT.replace("\n", "\r\n"),
                TEXTURED.replace("\n", "\r\n"), HAND));
    }

    @Test
    void leavesUnrecognizedDepthLayoutsUntouched() {
        assertEquals(GLINT, ComplementaryGlintPatch.patch(GLINT,
                TEXTURED.replace("0.000002", "0.000003"), HAND));
        assertEquals(GLINT, ComplementaryGlintPatch.patch(GLINT, TEXTURED,
                HAND + "gl_Position.z *= 0.125;"));
        String changed = GLINT.replace("ftransform()", "customProjection()");
        assertEquals(changed, ComplementaryGlintPatch.patch(changed, TEXTURED, HAND));
    }

    @Test
    void patchesOnlyComplementaryFilesAndPreservesBasePrograms(@TempDir Path pack) throws IOException {
        Path programs = Files.createDirectories(pack.resolve("shaders/program"));
        Path glint = programs.resolve("gbuffers_armor_glint.glsl");
        Files.writeString(glint, GLINT);
        Files.writeString(programs.resolve("gbuffers_textured.glsl"), TEXTURED);
        Files.writeString(programs.resolve("gbuffers_hand.glsl"), HAND);
        ComplementaryGlintPatch.inject(pack, "UnrelatedShader");
        assertEquals(GLINT, Files.readString(glint));
        ComplementaryGlintPatch.inject(pack, "ComplementaryReimagined_r5.9.3");
        assertEquals(ComplementaryGlintPatch.patch(GLINT, TEXTURED, HAND), Files.readString(glint));
        assertEquals(TEXTURED, Files.readString(programs.resolve("gbuffers_textured.glsl")));
        assertEquals(HAND, Files.readString(programs.resolve("gbuffers_hand.glsl")));
    }
}
