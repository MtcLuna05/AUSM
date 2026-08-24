package com.luna.ausm.impl.pipeline.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemAlphaCoverageTransformStageTest {
    @Test
    void discardsTransparentItemFragmentsBeforeTheyWriteDepthOrMaterials() {
        String source = """
                #version 130
                in vec2 texCoord;
                void main() {
                    vec4 color = texture2D(tex, texCoord);
                    if (color.a > 0.001) {
                        gl_FragData[0] = color;
                    }
                }
                """;

        String transformed = ItemAlphaCoverageTransformStage.transformFragment(source);

        assertTrue(transformed.contains("uniform float ausmItemAlphaTestRef;"));
        assertTrue(transformed.contains("if (color.a <= ausmItemAlphaTestRef) discard;"));
        assertTrue(transformed.indexOf("discard;") < transformed.indexOf("if (color.a > 0.001)"));
        assertEquals(transformed, ItemAlphaCoverageTransformStage.transformFragment(transformed));
    }

    @Test
    void leavesUnrecognizedShaderShapesUnchanged() {
        String source = "#version 130\nvoid main() { gl_FragData[0] = vec4(1.0); }\n";
        assertEquals(source, ItemAlphaCoverageTransformStage.transformFragment(source));
    }
}
