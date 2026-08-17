package com.l.ausm.impl.pipeline.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderCompatibilityHotPathTransformTest {
    @Test
    void normalizesAllIntegerSamplerCallsInOnePass() {
        String source = """
                #version 130
                uniform usampler2D voxelSampler;
                uniform sampler2D colorSampler;
                void main() {
                    uvec4 a = texture2D(voxelSampler, vec2(0.0));
                    uvec4 b = texture2DLod(voxelSampler, vec2(0.0), 0.0);
                    vec4 c = texture2D(colorSampler, vec2(0.0));
                }
                """;

        String transformed = new CompatibilityTextureFunctionTransformStage().apply(
                source, new ShaderTransformParameters(0, null, 130));

        assertTrue(transformed.contains("texture(voxelSampler"));
        assertTrue(transformed.contains("textureLod(voxelSampler"));
        assertTrue(transformed.contains("texture2D(colorSampler"));
    }

    @Test
    void rewritesKnownImageLayoutsWithOneDeclarationScan() {
        String source = """
                #version 330 compatibility
                layout(r32ui) writeonly uniform uimage3D voxelimg;
                uniform writeonly image2D playerAtlas_img;
                layout(rgba32f) writeonly uniform image3D unrelated;
                void main() { imageStore(playerAtlas_img, ivec2(0), vec4(1.0)); }
                """;

        String transformed = new ImageStoreCompatibilityTransformStage().apply(source, null);

        assertTrue(transformed.startsWith("#version 430 compatibility"));
        assertTrue(transformed.contains("layout(r8ui) writeonly uniform uimage3D voxelimg;"));
        assertTrue(transformed.contains("layout(rgba8) uniform writeonly image2D playerAtlas_img;"));
        assertTrue(transformed.contains("layout(rgba32f) writeonly uniform image3D unrelated;"));
        assertFalse(transformed.contains("layout(r32ui) writeonly uniform uimage3D voxelimg;"));
        assertEquals(transformed, new ImageStoreCompatibilityTransformStage().apply(transformed, null));
    }
}
