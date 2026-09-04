package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes fixed-function vertex position builtins accept an optional AUSM chunk
 * offset. When present, the Nothirium bridge uploads this offset per draw so
 * shaderpack world-position and projection math use the same chunk-space
 * translation source.
 */
public final class NothiriumChunkOffsetTransformStage implements ShaderTransformStage {
    private static final Pattern GL_VERTEX = Pattern.compile("\\bgl_Vertex\\b");
    private static final Pattern FTRANSFORM = Pattern.compile("\\bftransform\\s*\\(\\s*\\)");
    private static final Pattern VA_POSITION_DEFINE =
            Pattern.compile("(?m)^\\s*#\\s*define\\s+vaPosition\\b.*$");
    private static final Pattern VERSION_OR_EXTENSION = Pattern.compile("(?m)^(\\s*(?:#version\\b.*|#extension\\b.*)\\R)");
    private static final Set<RenderPass> CHUNK_OFFSET_PASSES = EnumSet.of(
            RenderPass.GBUFFERS_TERRAIN,
            RenderPass.GBUFFERS_TERRAIN_SOLID,
            RenderPass.GBUFFERS_TERRAIN_CUTOUT,
            RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP,
            RenderPass.GBUFFERS_DAMAGEDBLOCK,
            RenderPass.GBUFFERS_BLOCK,
            RenderPass.GBUFFERS_BLOCK_TRANSLUCENT,
            RenderPass.GBUFFERS_WATER,
            RenderPass.SHADOW,
            RenderPass.SHADOW_SOLID,
            RenderPass.SHADOW_CUTOUT,
            RenderPass.SHADOW_WATER
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if ((parameters.pass() == RenderPass.GBUFFERS_TERRAIN
                || parameters.pass() == RenderPass.GBUFFERS_TERRAIN_SOLID)
                && parameters.shaderType() == 0x8B30) {
            String fixedSampleCount = "float samplesDiv2 = ANISOTROPIC_FILTER / 2.0;\n"
                    + "    vec2 ADivSamples = A / ANISOTROPIC_FILTER;";
            String adaptiveSampleCount = "float ausmAfSamples = clamp(ceil(M / max(m, 0.00001)), "
                    + "1.0, float(ANISOTROPIC_FILTER));\n"
                    + "    float samplesDiv2 = ausmAfSamples * 0.5;\n"
                    + "    vec2 ADivSamples = A / ausmAfSamples;";
            if (source.contains(fixedSampleCount)
                    && source.contains("filteredColor.a /= ANISOTROPIC_FILTER;")) {
                source = source.replace(fixedSampleCount, adaptiveSampleCount)
                        .replace("filteredColor.a /= ANISOTROPIC_FILTER;",
                                "filteredColor.a /= ausmAfSamples;");
            }
            if (source.contains("uniform int ausmLodFallbackEnabled;")
                    && source.contains("uniform float ausmLod1RadiusBlocks;")
                    && source.contains("if (!noGeneratedNormals) GenerateNormals(normalM, colorP);")) {
                source = source.replace(
                        "if (!noGeneratedNormals) GenerateNormals(normalM, colorP);",
                        "if (!noGeneratedNormals && (ausmLodFallbackEnabled <= 0 || lViewPos < ausmLod1RadiusBlocks)) "
                                + "GenerateNormals(normalM, colorP);"
                );
            }
            String taaFilteredShadow = "vec3 shadow = SampleTAAFilteredShadow(shadowPos, offset, shadowSamples, "
                    + "leaves, colorMult, colorPow);";
            if (source.contains("float ausmShadowResolutionScale = ausmEntreeLodResolutionScale(ausmShadowDistance);")
                    && source.contains(taaFilteredShadow)) {
                source = source.replace(
                        taaFilteredShadow,
                        "vec3 shadow = ausmShadowResolutionScale >= 2.0\n"
                                + "                    ? SampleShadow(shadowPos, colorMult, colorPow)\n"
                                + "                    : SampleTAAFilteredShadow(shadowPos, offset, shadowSamples, "
                                + "leaves, colorMult, colorPow);"
                );
            }
            String fixedFilterSignature =
                    "    vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float colorMult, float colorPow) {";
            int fixedFilterStart = source.indexOf(fixedFilterSignature);
            int fixedFilterEnd = fixedFilterStart < 0
                    ? -1
                    : source.indexOf("\n    vec3 SampleBasicFilteredShadow", fixedFilterStart);
            String fixedFilterCall =
                    "vec3 shadow = SampleFilteredShadow(shadowPos, offset, colorMult, colorPow);";
            if (source.contains("float ausmShadowResolutionScale = ausmEntreeLodResolutionScale(ausmShadowDistance);")
                    && fixedFilterStart >= 0
                    && fixedFilterEnd > fixedFilterStart
                    && source.contains(fixedFilterCall)) {
                String fixedFilter = source.substring(fixedFilterStart, fixedFilterEnd);
                String tieredFixedFilter = fixedFilter
                        .replace(fixedFilterSignature,
                                "    vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float colorMult, "
                                        + "float colorPow, int tapCount) {")
                        .replace(
                                "for (int i = 0; i < 4; i++) {\n"
                                        + "            shadow += SampleShadow(vec3(offset * shadowOffsets[i] "
                                        + "+ shadowPos.st, shadowPos.z), colorMult, colorPow);\n"
                                        + "        }",
                                "shadow += SampleShadow(vec3(offset * shadowOffsets[0] + shadowPos.st, "
                                        + "shadowPos.z), colorMult, colorPow);\n"
                                        + "        if (tapCount >= 2) {\n"
                                        + "            shadow += SampleShadow(vec3(offset * shadowOffsets[1] "
                                        + "+ shadowPos.st, shadowPos.z), colorMult, colorPow);\n"
                                        + "        }\n"
                                        + "        if (tapCount >= 4) {\n"
                                        + "            shadow += SampleShadow(vec3(offset * shadowOffsets[2] "
                                        + "+ shadowPos.st, shadowPos.z), colorMult, colorPow);\n"
                                        + "            shadow += SampleShadow(vec3(offset * shadowOffsets[3] "
                                        + "+ shadowPos.st, shadowPos.z), colorMult, colorPow);\n"
                                        + "        }"
                        )
                        .replace("return shadow * 0.2;", "return shadow / float(tapCount + 1);");
                source = source.substring(0, fixedFilterStart)
                        + tieredFixedFilter
                        + source.substring(fixedFilterEnd);
                source = source.replace(
                        fixedFilterCall,
                        "vec3 shadow = ausmShadowResolutionScale >= 2.0\n"
                                + "                    ? SampleShadow(shadowPos, colorMult, colorPow)\n"
                                + "                    : SampleFilteredShadow(shadowPos, offset, colorMult, "
                                + "colorPow, 4);"
                );
            }
            String coloredLightingStart = "    #if COLORED_LIGHTING_INTERNAL > 0\n"
                    + "        // Prepare";
            String guardedColoredLightingStart = "    #if COLORED_LIGHTING_INTERNAL > 0\n"
                    + "        vec3 ausmColoredLightAbsPlayerPos = abs(playerPos);\n"
                    + "        #if COLORED_LIGHTING_INTERNAL <= 512\n"
                    + "            ausmColoredLightAbsPlayerPos.y *= 2.0;\n"
                    + "        #elif COLORED_LIGHTING_INTERNAL == 768\n"
                    + "            ausmColoredLightAbsPlayerPos.y *= 3.0;\n"
                    + "        #elif COLORED_LIGHTING_INTERNAL == 1024\n"
                    + "            ausmColoredLightAbsPlayerPos.y *= 4.0;\n"
                    + "        #endif\n"
                    + "        float ausmColoredLightMaxPlayerPos = max(ausmColoredLightAbsPlayerPos.x,\n"
                    + "                max(ausmColoredLightAbsPlayerPos.y, ausmColoredLightAbsPlayerPos.z));\n"
                    + "        if (ausmColoredLightMaxPlayerPos < effectiveACTdistance * 0.5) {\n"
                    + "        // Prepare";
            String coloredLightingEnd =
                    "        blockLighting = mix(specialLighting, blockLighting, blocklightDecider);\n"
                            + "        //if (heldItemId2 == 40000";
            String guardedColoredLightingEnd =
                    "        blockLighting = mix(specialLighting, blockLighting, blocklightDecider);\n"
                            + "        }\n"
                            + "        //if (heldItemId2 == 40000";
            if (source.contains(coloredLightingStart) && source.contains(coloredLightingEnd)) {
                source = source.replace(coloredLightingStart, guardedColoredLightingStart)
                        .replace(coloredLightingEnd, guardedColoredLightingEnd);
            }
        }
        if (parameters.pass() == null
                || (parameters.pass().stage() != ProgramStage.GBUFFERS
                && parameters.pass().stage() != ProgramStage.SHADOW)
                || !isTerrainOffsetPass(parameters.pass())
                || parameters.shaderType() != 0x8B31
                || source.contains("ausm_ChunkVertex")) {
            return source;
        }
        boolean hasLegacyVertexReference = source.contains("gl_Vertex") || FTRANSFORM.matcher(source).find();
        if (!hasLegacyVertexReference && !VA_POSITION_DEFINE.matcher(source).find()) {
            return source;
        }

        String transformed = FTRANSFORM.matcher(source)
                .replaceAll(Matcher.quoteReplacement("(gl_ModelViewProjectionMatrix * ausm_ChunkVertex())"));
        if (source.contains("gl_Vertex")) {
            transformed = GL_VERTEX.matcher(transformed).replaceAll("ausm_ChunkVertex()");
        } else if (!VA_POSITION_DEFINE.matcher(transformed).find()) {
            return source;
        }
        transformed = VA_POSITION_DEFINE.matcher(transformed)
                .replaceAll(Matcher.quoteReplacement("#define vaPosition ausm_ChunkVertex().xyz"));
        return injectAfterVersion(transformed, declarationFor());
    }

    private static boolean isTerrainOffsetPass(RenderPass pass) {
        return CHUNK_OFFSET_PASSES.contains(pass);
    }

    private static String injectAfterVersion(String source, String declaration) {
        Matcher matcher = VERSION_OR_EXTENSION.matcher(source);
        int insertAt = 0;
        while (matcher.find()) {
            insertAt = matcher.end();
        }
        return source.substring(0, insertAt) + declaration + source.substring(insertAt);
    }

    static String declarationFor() {
        return "uniform vec3 ausm_ChunkOffset;\n"
                + "attribute vec3 ausm_ChunkOffsetInstanced;\n"
                + "vec4 ausm_ChunkVertex() {\n"
                + "    return gl_Vertex + vec4(ausm_ChunkOffset + ausm_ChunkOffsetInstanced, 0.0);\n"
                + "}\n";
    }
}
