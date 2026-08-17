package com.l.ausm.impl.pipeline.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemGlintCoverageTransformStageTest {
    @Test
    void preservesRawAtlasCoordinatesBeforeTheGlintTextureMatrix() {
        String source = """
                #version 130
                out vec2 texCoord;
                void main() {
                    texCoord = (iris_TextureMat * gl_MultiTexCoord0).xy;
                }
                """;

        String transformed = ItemGlintCoverageTransformStage.transformVertex(source);

        assertTrue(transformed.contains("out vec2 ausmItemGlintBaseTexCoord;"));
        assertTrue(transformed.contains("ausmItemGlintBaseTexCoord = gl_MultiTexCoord0.xy;"));
        assertEquals(transformed, ItemGlintCoverageTransformStage.transformVertex(transformed));
    }

    @Test
    void discardsOnlyItemGlintWhereTheBaseAtlasIsTransparent() {
        String source = """
                #version 130
                in vec2 texCoord;
                void main() {
                    vec4 color = texture2D(tex, texCoord);
                    gl_FragData[0] = color;
                }
                """;

        String transformed = ItemGlintCoverageTransformStage.transformFragment(source);

        assertTrue(transformed.contains("uniform sampler2D ausmItemGlintBaseAtlas;"));
        assertTrue(transformed.contains("uniform float ausmItemAlphaTestRef;"));
        assertTrue(transformed.contains("ausmItemGlintMask == 1"));
        assertTrue(transformed.contains(
                "ausmItemGlintBaseTexCoord).a <= max(ausmItemAlphaTestRef, 0.001)) discard;"));
        assertEquals(transformed, ItemGlintCoverageTransformStage.transformFragment(transformed));
    }
}
