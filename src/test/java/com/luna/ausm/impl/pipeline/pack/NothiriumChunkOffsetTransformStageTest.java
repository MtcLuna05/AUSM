package com.luna.ausm.impl.pipeline.pack;

import org.junit.jupiter.api.Test;

import com.luna.ausm.api.pipeline.shader.RenderPass;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NothiriumChunkOffsetTransformStageTest {
    @Test
    void suppliesScalarAndInstancedOffsetsToShadowTerrain() {
        String transformed = NothiriumChunkOffsetTransformStage.declarationFor();

        assertTrue(transformed.contains("uniform vec3 ausm_ChunkOffset;"));
        assertTrue(transformed.contains("attribute vec3 ausm_ChunkOffsetInstanced;"));
        assertTrue(transformed.contains("ausm_ChunkOffset + ausm_ChunkOffsetInstanced"));
    }

    @Test
    void reducesFarTerrainTextureAndShadowSampling() {
        String source = "uniform int ausmLodFallbackEnabled;\n"
                + "uniform float ausmLod1RadiusBlocks;\n"
                + "float ausmShadowResolutionScale = ausmEntreeLodResolutionScale(ausmShadowDistance);\n"
                + "float samplesDiv2 = ANISOTROPIC_FILTER / 2.0;\n"
                + "    vec2 ADivSamples = A / ANISOTROPIC_FILTER;\n"
                + "filteredColor.a /= ANISOTROPIC_FILTER;\n"
                + "if (!noGeneratedNormals) GenerateNormals(normalM, colorP);\n"
                + "vec3 shadow = SampleTAAFilteredShadow(shadowPos, offset, shadowSamples, leaves, colorMult, colorPow);\n";

        for (RenderPass pass : new RenderPass[]{RenderPass.GBUFFERS_TERRAIN, RenderPass.GBUFFERS_TERRAIN_SOLID}) {
            String transformed = new NothiriumChunkOffsetTransformStage().apply(
                    source,
                    new ShaderTransformParameters(0x8B30, pass, 120)
            );

            assertTrue(transformed.contains("float ausmAfSamples = clamp"), pass.name());
            assertTrue(transformed.contains("filteredColor.a /= ausmAfSamples;"), pass.name());
            assertTrue(transformed.contains("lViewPos < ausmLod1RadiusBlocks"), pass.name());
            assertTrue(transformed.contains("ausmShadowResolutionScale >= 2.0"), pass.name());
            assertTrue(transformed.contains("? SampleShadow(shadowPos, colorMult, colorPow)"), pass.name());
            assertFalse(transformed.contains("float samplesDiv2 = ANISOTROPIC_FILTER / 2.0;"), pass.name());
        }
    }

    @Test
    void tiersFixedShadowFilterForTerrainWhenTaaIsDisabled() {
        String source = "float ausmShadowResolutionScale = ausmEntreeLodResolutionScale(ausmShadowDistance);\n"
                + "    vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float colorMult, float colorPow) {\n"
                + "        vec3 shadow = vec3(0.0);\n"
                + "\n"
                + "        for (int i = 0; i < 4; i++) {\n"
                + "            shadow += SampleShadow(vec3(offset * shadowOffsets[i] + shadowPos.st, shadowPos.z), colorMult, colorPow);\n"
                + "        }\n"
                + "        shadow += SampleShadow(shadowPos, colorMult, colorPow);\n"
                + "\n"
                + "        return shadow * 0.2;\n"
                + "    }\n"
                + "\n"
                + "    vec3 SampleBasicFilteredShadow(vec3 shadowPos, float offset) { return vec3(1.0); }\n"
                + "vec3 shadow = SampleFilteredShadow(shadowPos, offset, colorMult, colorPow);\n";

        String transformed = new NothiriumChunkOffsetTransformStage().apply(
                source,
                new ShaderTransformParameters(0x8B30, RenderPass.GBUFFERS_TERRAIN, 120)
        );

        assertTrue(transformed.contains("float colorPow, int tapCount)"));
        assertTrue(transformed.contains("shadowOffsets[0]"));
        assertTrue(transformed.contains("if (tapCount >= 2)"));
        assertTrue(transformed.contains("if (tapCount >= 4)"));
        assertFalse(transformed.contains("for (int i = 0; i < tapCount; i++)"));
        assertTrue(transformed.contains("return shadow / float(tapCount + 1);"));
        assertTrue(transformed.contains("ausmShadowResolutionScale >= 2.0"));
        assertTrue(transformed.contains("? SampleShadow(shadowPos, colorMult, colorPow)"));
        assertTrue(transformed.contains("colorPow, 4);"));
        assertFalse(transformed.contains("return shadow * 0.2;"));
    }

    @Test
    void skipsDiscardedFarColoredLightingWork() {
        String source = "    #if COLORED_LIGHTING_INTERNAL > 0\n"
                + "        // Prepare\n"
                + "        blockLighting = mix(specialLighting, blockLighting, blocklightDecider);\n"
                + "        //if (heldItemId2 == 40000 debug)\n";

        String transformed = new NothiriumChunkOffsetTransformStage().apply(
                source,
                new ShaderTransformParameters(0x8B30, RenderPass.GBUFFERS_TERRAIN, 120)
        );

        assertTrue(transformed.contains("vec3 ausmColoredLightAbsPlayerPos = abs(playerPos);"));
        assertTrue(transformed.contains("ausmColoredLightAbsPlayerPos.y *= 2.0;"));
        assertTrue(transformed.contains("ausmColoredLightMaxPlayerPos < effectiveACTdistance * 0.5"));
        assertTrue(transformed.contains("blockLighting = mix(specialLighting, blockLighting, blocklightDecider);\n"
                + "        }"));
    }
}
