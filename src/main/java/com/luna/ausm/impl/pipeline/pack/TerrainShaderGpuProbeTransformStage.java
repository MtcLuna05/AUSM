package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.impl.pipeline.TerrainShaderGpuProbe;

/** Injects sparse shader-clock probes into the monolithic terrain fragment program. */
public final class TerrainShaderGpuProbeTransformStage implements ShaderTransformStage {
    private static final String MARKER = "AUSM_TERRAIN_SHADER_PROBE";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if ((parameters.pass() != RenderPass.GBUFFERS_TERRAIN
                && parameters.pass() != RenderPass.GBUFFERS_TERRAIN_SOLID)
                || parameters.shaderType() != 0x8B30
                || parameters.glslVersion() < 430
                || source.contains(MARKER)
                || !TerrainShaderGpuProbe.requestedAndSupported()) {
            return source;
        }
        return instrument(source, TerrainShaderGpuProbe.bindingIndex());
    }

    static String instrument(String source, int bindingIndex) {
        int versionEnd = source.indexOf('\n');
        if (versionEnd < 0 || !source.startsWith("#version")) {
            return source;
        }
        String declarations = "\n#extension GL_ARB_shader_clock : require\n"
                + "#define " + MARKER + " 1\n"
                + "layout(std430, binding = " + bindingIndex + ") coherent buffer AusmTerrainShaderProbeBuffer {\n"
                + "    uint ausmTerrainShaderProbeData[];\n"
                + "};\n"
                + "uniform bool ausmTerrainProbeActive;\n"
                + "bool ausmTerrainShaderProbeSample() {\n"
                + "    uvec2 pixel = uvec2(gl_FragCoord.xy);\n"
                + "    uint hash = pixel.x * 73856093u ^ pixel.y * 19349663u;\n"
                + "    return (hash & 65535u) == 0u;\n"
                + "}\n"
                + "uint ausmTerrainShaderProbeClock() { return clock2x32ARB().x; }\n"
                + "void ausmTerrainShaderProbeRecord(uint scope, uint elapsed) {\n"
                + "    uint base = scope * 3u;\n"
                + "    atomicAdd(ausmTerrainShaderProbeData[base], elapsed);\n"
                + "    atomicAdd(ausmTerrainShaderProbeData[base + 1u], 1u);\n"
                + "    atomicMax(ausmTerrainShaderProbeData[base + 2u], elapsed);\n"
                + "}\n"
                + "bool ausmTerrainProbeSampled = false;\n"
                + "uint ausmTerrainProbeTotalStart = 0u;\n"
                + "uint ausmTerrainProbeSegmentStart = 0u;\n"
                + "uint ausmTerrainProbeLightingStart = 0u;\n"
                + "uint ausmTerrainProbeElapsed[12];\n";
        String transformed = source.substring(0, versionEnd + 1) + declarations + source.substring(versionEnd + 1);
        try {
            transformed = replaceOnce(transformed, "void main() {\n",
                    "void main() {\n"
                            + "    ausmTerrainProbeSampled = false;\n"
                            + "    if (ausmTerrainProbeActive) ausmTerrainProbeSampled = ausmTerrainShaderProbeSample();\n"
                            + "    if (ausmTerrainProbeSampled) {\n"
                            + "        for (int ausmProbeIndex = 0; ausmProbeIndex < 12; ++ausmProbeIndex) ausmTerrainProbeElapsed[ausmProbeIndex] = 0u;\n"
                            + "        ausmTerrainProbeTotalStart = ausmTerrainShaderProbeClock();\n"
                            + "        ausmTerrainProbeSegmentStart = ausmTerrainProbeTotalStart;\n"
                            + "    }\n");
            transformed = replaceOnce(transformed,
                    "    float smoothnessD = 0.0, materialMask = 0.0;",
                    "    if (ausmTerrainProbeSampled) {\n"
                            + "        ausmTerrainProbeElapsed[1] = ausmTerrainShaderProbeClock() - ausmTerrainProbeSegmentStart;\n"
                            + "        ausmTerrainProbeSegmentStart = ausmTerrainShaderProbeClock();\n"
                            + "    }\n"
                            + "    float smoothnessD = 0.0, materialMask = 0.0;");
            transformed = replaceOnce(transformed,
                    "    vec3 colorP = color.rgb;",
                    "    if (ausmTerrainProbeSampled) {\n"
                            + "        ausmTerrainProbeElapsed[2] = ausmTerrainShaderProbeClock() - ausmTerrainProbeSegmentStart;\n"
                            + "        ausmTerrainProbeSegmentStart = ausmTerrainShaderProbeClock();\n"
                            + "    }\n"
                            + "    vec3 colorP = color.rgb;");
            transformed = replaceOnce(transformed,
                    "#ifdef IPBR\n    vec3 maRecolor = vec3(0.0);",
                    "if (ausmTerrainProbeSampled) {\n"
                            + "    ausmTerrainProbeElapsed[3] = ausmTerrainShaderProbeClock() - ausmTerrainProbeSegmentStart;\n"
                            + "    ausmTerrainProbeSegmentStart = ausmTerrainShaderProbeClock();\n"
                            + "}\n"
                            + "#ifdef IPBR\n    vec3 maRecolor = vec3(0.0);");
            transformed = replaceOnce(transformed,
                    "    DoLighting(color, shadowMult, playerPos, viewPos, lViewPos, geoNormal, normalM, dither,",
                    "    if (ausmTerrainProbeSampled) {\n"
                            + "        ausmTerrainProbeElapsed[4] = ausmTerrainShaderProbeClock() - ausmTerrainProbeSegmentStart;\n"
                            + "        ausmTerrainProbeLightingStart = ausmTerrainShaderProbeClock();\n"
                            + "    }\n"
                            + "    DoLighting(color, shadowMult, playerPos, viewPos, lViewPos, geoNormal, normalM, dither,");
            transformed = replaceOnce(transformed,
                    "               centerShadowBias, subsurfaceMode, smoothnessG, highlightMult, emission);",
                    "               centerShadowBias, subsurfaceMode, smoothnessG, highlightMult, emission);\n"
                            + "    if (ausmTerrainProbeSampled) {\n"
                            + "        ausmTerrainProbeElapsed[5] = ausmTerrainShaderProbeClock() - ausmTerrainProbeLightingStart;\n"
                            + "        ausmTerrainProbeSegmentStart = ausmTerrainShaderProbeClock();\n"
                            + "    }");
            transformed = replaceOnce(transformed,
                    "        if (shadowMult.r > 0.00001) {",
                    "        if (ausmTerrainProbeSampled) {\n"
                            + "            ausmTerrainProbeElapsed[6] = ausmTerrainShaderProbeClock() - ausmTerrainProbeLightingStart;\n"
                            + "            ausmTerrainProbeSegmentStart = ausmTerrainShaderProbeClock();\n"
                            + "        }\n"
                            + "        if (shadowMult.r > 0.00001) {");
            transformed = replaceOnce(transformed,
                    "    // Blocklight\n",
                    "    if (ausmTerrainProbeSampled) {\n"
                            + "        ausmTerrainProbeElapsed[7] = ausmTerrainShaderProbeClock() - ausmTerrainProbeSegmentStart;\n"
                            + "        ausmTerrainProbeSegmentStart = ausmTerrainShaderProbeClock();\n"
                            + "    }\n\n"
                            + "    // Blocklight\n");
            transformed = replaceOnce(transformed,
                    "    vec3 minLighting = GetMinimumLighting(lightmapYM);",
                    "    if (ausmTerrainProbeSampled) {\n"
                            + "        ausmTerrainProbeElapsed[8] = ausmTerrainShaderProbeClock() - ausmTerrainProbeSegmentStart;\n"
                            + "        ausmTerrainProbeSegmentStart = ausmTerrainShaderProbeClock();\n"
                            + "    }\n\n"
                            + "    vec3 minLighting = GetMinimumLighting(lightmapYM);");
            transformed = replaceOnce(transformed,
                    "    color.rgb *= pow2(1.0 - darknessLightFactor);\n}",
                    "    color.rgb *= pow2(1.0 - darknessLightFactor);\n"
                            + "    if (ausmTerrainProbeSampled) {\n"
                            + "        ausmTerrainProbeElapsed[9] = ausmTerrainShaderProbeClock() - ausmTerrainProbeSegmentStart;\n"
                            + "    }\n"
                            + "}");
            transformed = replaceOnce(transformed,
                    "    /* DRAWBUFFERS:06 */",
                    "    if (ausmTerrainProbeSampled) {\n"
                            + "        ausmTerrainProbeElapsed[10] = ausmTerrainShaderProbeClock() - ausmTerrainProbeSegmentStart;\n"
                            + "        ausmTerrainProbeSegmentStart = ausmTerrainShaderProbeClock();\n"
                            + "    }\n"
                            + "    /* DRAWBUFFERS:06 */");
            transformed = replaceOnce(transformed,
                    "    gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0);",
                    "    gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0);\n"
                            + "    if (ausmTerrainProbeSampled) {\n"
                            + "        ausmTerrainProbeElapsed[11] = ausmTerrainShaderProbeClock() - ausmTerrainProbeSegmentStart;\n"
                            + "        ausmTerrainProbeElapsed[0] = ausmTerrainShaderProbeClock() - ausmTerrainProbeTotalStart;\n"
                            + "        for (uint ausmProbeScope = 0u; ausmProbeScope < 12u; ++ausmProbeScope) {\n"
                            + "            ausmTerrainShaderProbeRecord(ausmProbeScope, ausmTerrainProbeElapsed[ausmProbeScope]);\n"
                            + "        }\n"
                            + "    }");
            TerrainShaderGpuProbe.reportShaderInstrumented();
            return transformed;
        } catch (IllegalArgumentException missingAnchor) {
            TerrainShaderGpuProbe.reportShaderInstrumentationFailure(missingAnchor.getMessage());
            return source;
        }
    }

    private static String replaceOnce(String source, String target, String replacement) {
        int index = source.indexOf(target);
        if (index < 0) {
            throw new IllegalArgumentException(target.substring(0, Math.min(target.length(), 96)));
        }
        return source.substring(0, index) + replacement + source.substring(index + target.length());
    }
}
