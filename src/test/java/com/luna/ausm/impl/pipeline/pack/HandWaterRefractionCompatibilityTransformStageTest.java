package com.luna.ausm.impl.pipeline.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HandWaterRefractionCompatibilityTransformStageTest {
    @Test
    void excludesThePackHandDepthDomainBeforeReadingTheWaterMaterialTag() {
        String source = """
                #version 430 compatibility
                vec2 DoRefraction(inout vec3 color, inout float z0, inout float z1, vec3 viewPos, float lViewPos) {
                    if (int(texelFetch(colortex6, texelCoord, 0).g * 255.1) != 241) return texCoord.xy;
                    return texCoord.xy + vec2(0.01);
                }
                """;

        String transformed = HandWaterRefractionCompatibilityTransformStage.transformFragment(source);

        assertTrue(transformed.contains("// AUSM_HAND_WATER_REFRACTION_EXCLUSION"));
        assertTrue(transformed.contains("if (z0 <= 0.56) return texCoord.xy;"));
        assertTrue(transformed.indexOf("z0 <= 0.56") < transformed.indexOf("texelFetch(colortex6"));
        assertEquals(transformed,
                HandWaterRefractionCompatibilityTransformStage.transformFragment(transformed));
    }

    @Test
    void leavesOtherRefractionImplementationsUnchanged() {
        String source = "vec2 DoRefraction(inout vec3 color, inout float z0) { return vec2(z0); }\n";
        assertEquals(source, HandWaterRefractionCompatibilityTransformStage.transformFragment(source));
    }
}
