package com.luna.ausm.impl.pipeline.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainShaderGpuProbeTransformStageTest {
    @Test
    void instrumentsMajorTerrainFragmentRegions() {
        String source = "#version 430 compatibility\n"
                + "void DoLighting(inout vec4 color, inout vec3 shadowMult, vec3 playerPos, vec3 viewPos, float lViewPos, vec3 geoNormal, vec3 normalM, float dither,\n"
                + "                vec3 worldGeoNormal, vec2 lightmap, bool noSmoothLighting, bool noDirectionalShading, bool noVanillaAO,\n"
                + "                bool centerShadowBias, int subsurfaceMode, float smoothnessG, float highlightMult, float emission, inout float purkinjeOverwrite, bool isLightSource,\n"
                + "                inout float enderDragonDead) {\n"
                + "        if (shadowMult.r > 0.00001) {\n"
                + "        }\n"
                + "    // Blocklight\n"
                + "    vec3 minLighting = GetMinimumLighting(lightmapYM, playerPos);\n"
                + "    color.rgb *= pow2(1.0 - darknessLightFactor);\n"
                + "}\n"
                + "void main() {\n"
                + "    vec3 normalM = normal, geoNormal = normal, shadowMult = vec3(1.0), normal_PH = normal;\n\n"
                + "    #ifdef GBUFFERS_VOXELS\n"
                + "    #endif\n\n"
                + "    float smoothnessD = 0.0, materialMask = 0.0;\n"
                + "#ifdef IPBR\n"
                + "    vec3 maRecolor = vec3(0.0);\n"
                + "#endif\n"
                + "    #ifdef IPBR\n"
                + "        color.rgb += maRecolor;\n"
                + "    #endif\n"
                + "    DoLighting(color, shadowMult, playerPos, viewPos, lViewPos, geoNormal, normalM, dither,\n"
                + "               enderDragonDead);\n\n"
                + "    #ifdef SS_BLOCKLIGHT\n"
                + "    #endif\n"
                + "    /* DRAWBUFFERS:06 */\n"
                + "    #endif\n\n"
                + "}\n\n"
                + "#endif\n\n"
                + "//////////Vertex Shader\n";

        String transformed = TerrainShaderGpuProbeTransformStage.instrument(source, 95);

        assertTrue(transformed.contains("#extension GL_ARB_shader_clock : require"));
        assertTrue(transformed.contains("layout(std430, binding = 95)"));
        assertTrue(transformed.contains("uint ausmTerrainProbeElapsed[12]"));
        for (int scope = 0; scope < 12; scope++) {
            assertTrue(transformed.contains("ausmTerrainProbeElapsed[" + scope + "]"));
        }
        assertTrue(transformed.contains("ausmTerrainProbeElapsed[7] = ausmTerrainShaderProbeClock()"));
        assertTrue(transformed.contains("ausmTerrainProbeElapsed[8] = ausmTerrainShaderProbeClock()"));
        assertTrue(transformed.contains("ausmTerrainProbeElapsed[9] = ausmTerrainShaderProbeClock()"));
        assertTrue(transformed.contains("ausmProbeScope < 12u"));
    }
}
