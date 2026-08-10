package com.l.ausm.impl.pipeline.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ThinQuadFaceSeparationTransformStageTest {
    @Test
    void injectsMidBlockAttributeAndInteriorPlaneOffset() {
        String source = """
                #version 130
                attribute vec4 mc_Entity;
                attribute vec4 mc_midTexCoord;
                void main() {
                    vec4 position = gl_Vertex;
                    vertexPos = position.xyz;
                }
                """;

        String transformed = ThinQuadFaceSeparationTransformStage.injectFaceSeparation(source);

        assertTrue(transformed.contains("attribute vec4 at_midBlock;"));
        assertTrue(transformed.contains(ThinQuadFaceSeparationTransformStage.MARKER));
        assertTrue(transformed.contains("ausmThinQuadCenterDistance < 0.4995"));
        assertTrue(transformed.contains("position.xyz += ausmThinQuadNormal * 0.0005"));
        assertEquals(transformed, ThinQuadFaceSeparationTransformStage.injectFaceSeparation(transformed));
    }

    @Test
    void preservesAnExistingMidBlockDeclaration() {
        String source = """
                attribute vec4 mc_Entity;
                attribute vec3 at_midBlock;
                void main() {
                    vec4 position = gl_Vertex;
                    vertexPos = position.xyz;
                }
                """;

        String transformed = ThinQuadFaceSeparationTransformStage.injectFaceSeparation(source);
        assertEquals(1, count(transformed, "at_midBlock;"));
    }

    @Test
    void leavesShadersWithoutTerrainPositionAnchorAlone() {
        String source = "void main() { gl_Position = gl_Vertex; }";
        assertEquals(source, ThinQuadFaceSeparationTransformStage.injectFaceSeparation(source));
    }

    private static int count(String source, String needle) {
        int occurrences = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) {
            occurrences++;
        }
        return occurrences;
    }
}
