package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GbuffersBuiltinTransformStageTest {
    private static final String SOURCE = """
            #version 130
            void main() {
                texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                lightCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
            }
            """;

    @Test
    void glintKeepsTheLiveTextureMatrixAndRawCoverageCoordinates() {
        String transformed = GbuffersBuiltinTransformStage.transformVertex(SOURCE, RenderPass.GBUFFERS_ARMOR_GLINT);
        transformed = ItemGlintCoverageTransformStage.transformVertex(transformed);

        assertTrue(transformed.contains("gl_TextureMatrix[0] * gl_MultiTexCoord0"));
        assertFalse(transformed.contains("iris_TextureMat"));
        assertTrue(transformed.contains("iris_LightmapTextureMatrix * gl_MultiTexCoord1"));
        assertTrue(transformed.contains("ausmItemGlintBaseTexCoord = gl_MultiTexCoord0.xy;"));
    }

    @Test
    void ordinaryGbuffersKeepTheirExistingMatrixRewrite() {
        String transformed = GbuffersBuiltinTransformStage.transformVertex(SOURCE, RenderPass.GBUFFERS_TERRAIN);

        assertTrue(transformed.contains("uniform mat4 iris_TextureMat;"));
        assertTrue(transformed.contains("iris_TextureMat * gl_MultiTexCoord0"));
        assertFalse(transformed.contains("gl_TextureMatrix[0]"));
    }
}
