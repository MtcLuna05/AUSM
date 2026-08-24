package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Applies small, idempotent quality-LOD edits to Euphoria's stable library
 * hooks. The shader-map allocation stays global, while distant fragments use
 * fewer PCF/ray-march samples and lower-mip reflection lookups until the
 * feature fades out.
 */
final class EuphoriaEntreeLodPatches {
    private static final String HELPER_INCLUDE = "#include \"/lib/ausm/distantLod.glsl\"";
    private static final String SHADOW_MARKER = "// AUSM 1.12.2 tiered shadow-resolution LOD";
    private static final String REFLECTION_MARKER = "// AUSM 1.12.2 tiered reflection-resolution LOD";
    private static final String SHADOW_PATH = "shaders/lib/lighting/shadowSampling.glsl";
    private static final String REFLECTION_PATH = "shaders/lib/materials/materialMethods/reflections.glsl";
    private static final String SHADOW_SIGNATURE = "    vec3 GetShadow(vec3 shadowPos, float lightmapY, float offset, int shadowSamples, bool leaves, vec3 playerPos) {\n";
    private static final String SHADOW_RETURN = "        return shadow;\n    }\n#endif";
    private static final String FILTERED_SHADOW_CALL = "            vec3 shadow = SampleFilteredShadow(shadowPos, offset, colorMult, colorPow);\n";
    private static final String BASIC_SHADOW_CALL = "            vec3 shadow = SampleBasicFilteredShadow(shadowPos, offset);\n";
    private static final String FILTERED_SHADOW_FUNCTION = """
            vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float colorMult, float colorPow) {
                vec3 shadow = vec3(0.0);

                for (int i = 0; i < 4; i++) {
                    shadow += SampleShadow(vec3(offset * shadowOffsets[i] + shadowPos.st, shadowPos.z), colorMult, colorPow);
                }
                shadow += SampleShadow(shadowPos, colorMult, colorPow);

                return shadow * 0.2;
            }
            """;
    private static final String TIERED_FILTERED_SHADOW_FUNCTION = """
            vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float colorMult, float colorPow, int tapCount) {
                vec3 shadow = vec3(0.0);

                for (int i = 0; i < tapCount; i++) {
                    shadow += SampleShadow(vec3(offset * shadowOffsets[i] + shadowPos.st, shadowPos.z), colorMult, colorPow);
                }
                shadow += SampleShadow(shadowPos, colorMult, colorPow);

                return shadow / float(tapCount + 1);
            }
            """;
    private static final String BASIC_SHADOW_FUNCTION = """
            vec3 SampleBasicFilteredShadow(vec3 shadowPos, float offset) {
                float shadow = 0.0;

                for (int i = 0; i < 4; i++) {
                    shadow += shadow2D(shadowtex0, vec3(offset * shadowOffsets[i] + shadowPos.st, shadowPos.z)).x;
                }

                return vec3(shadow * 0.25);
            }
            """;
    private static final String TIERED_BASIC_SHADOW_FUNCTION = """
            vec3 SampleBasicFilteredShadow(vec3 shadowPos, float offset, int tapCount) {
                float shadow = 0.0;

                for (int i = 0; i < tapCount; i++) {
                    shadow += shadow2D(shadowtex0, vec3(offset * shadowOffsets[i] + shadowPos.st, shadowPos.z)).x;
                }

                return vec3(shadow / float(tapCount));
            }
            """;
    private static final String REFLECTION_LOD = "                        lod = max(lod - 1.0, 0.0);\n";
    private static final String REFLECTION_RAY_SETUP = "            #endif\n\n            int sr = 0;\n";
    private static final String REFLECTION_ALPHA = "                    reflection.a *= refFactor;\n";

    private EuphoriaEntreeLodPatches() {
    }

    static void inject(Path patchPack) throws IOException {
        patchFile(patchPack.resolve(SHADOW_PATH), true);
        patchFile(patchPack.resolve(REFLECTION_PATH), false);
    }

    static String patchShadowSampling(String source) {
        if (source.contains(SHADOW_MARKER) || !source.contains(SHADOW_SIGNATURE) || !source.contains(SHADOW_RETURN)) {
            return source;
        }
        String withHelper = includeHelper(source);
        String setup = SHADOW_SIGNATURE
                + "        " + SHADOW_MARKER + "\n"
                + "        float ausmShadowDistance = length(playerPos);\n"
                + "        float ausmShadowResolutionScale = ausmEntreeLodResolutionScale(ausmShadowDistance);\n"
                + "        float ausmShadowFeatureWeight = ausmEntreeLodFeatureWeight(ausmShadowDistance);\n"
                + "        if (ausmShadowFeatureWeight <= 0.0001) return vec3(1.0);\n"
                + "        shadowSamples = ausmEntreeLodSampleCount(shadowSamples, ausmShadowResolutionScale);\n";
        boolean supportsTieredFixedFilters = withHelper.contains(FILTERED_SHADOW_FUNCTION)
                && withHelper.contains(BASIC_SHADOW_FUNCTION);
        String patched = withHelper.replace(SHADOW_SIGNATURE, setup).replace(
                SHADOW_RETURN,
                "        return mix(vec3(1.0), shadow, ausmShadowFeatureWeight);\n    }\n#endif"
        );
        if (!supportsTieredFixedFilters) {
            return patched;
        }
        return patched.replace(FILTERED_SHADOW_FUNCTION, TIERED_FILTERED_SHADOW_FUNCTION)
                .replace(BASIC_SHADOW_FUNCTION, TIERED_BASIC_SHADOW_FUNCTION)
                .replace(
                        FILTERED_SHADOW_CALL,
                        "            vec3 shadow = SampleFilteredShadow(shadowPos, offset, colorMult, colorPow,\n"
                                + "                    ausmEntreeLodSampleCount(4, ausmShadowResolutionScale));\n"
                )
                .replace(
                        BASIC_SHADOW_CALL,
                        "            vec3 shadow = SampleBasicFilteredShadow(shadowPos, offset,\n"
                                + "                    ausmEntreeLodSampleCount(4, ausmShadowResolutionScale));\n"
                );
    }

    static String patchReflections(String source) {
        if (source.contains(REFLECTION_MARKER)) {
            return source;
        }
        String withHelper = includeHelper(source);
        boolean supportsRayMarchLod = withHelper.contains(REFLECTION_RAY_SETUP);
        String patched = withHelper.replace(
                REFLECTION_LOD,
                REFLECTION_LOD + "                        lod += ausmEntreeReflectionMipBias(dist);\n"
        ).replace(
                REFLECTION_RAY_SETUP,
                "            #endif\n\n"
                        + "            " + REFLECTION_MARKER + "\n"
                        + "            float ausmReflectionDistance = length(playerPos);\n"
                        + "            float ausmReflectionResolutionScale = ausmEntreeLodResolutionScale(ausmReflectionDistance);\n"
                        + "            float ausmReflectionFeatureWeight = ausmEntreeLodFeatureWeight(ausmReflectionDistance);\n"
                        + "            if (ausmReflectionFeatureWeight <= 0.0001) return vec4(0.0);\n"
                        + "            sampleCount = ausmEntreeLodSampleCount(sampleCount, ausmReflectionResolutionScale);\n"
                        + "            refinementCount = ausmEntreeLodSampleCount(refinementCount, ausmReflectionResolutionScale);\n\n"
                        + "            int sr = 0;\n"
        );
        if (supportsRayMarchLod) {
            patched = patched.replace(
                    REFLECTION_ALPHA,
                    REFLECTION_ALPHA + "                    reflection.a *= ausmReflectionFeatureWeight;\n"
            );
        }
        if (patched.equals(withHelper)) {
            return source;
        }
        return patched.contains(REFLECTION_MARKER)
                ? patched
                : patched.replace(HELPER_INCLUDE, HELPER_INCLUDE + "\n" + REFLECTION_MARKER);
    }

    private static void patchFile(Path file, boolean shadow) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        String source = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String patched = shadow ? patchShadowSampling(source) : patchReflections(source);
        if (!patched.equals(source)) {
            Files.writeString(file, patched, StandardCharsets.UTF_8);
            MainMod.LOGGER.info("[AUSM112Lod] Injected tiered {} LOD into {}", shadow ? "shadow" : "reflection", file.getFileName());
        }
    }

    private static String includeHelper(String source) {
        return source.contains(HELPER_INCLUDE) ? source : HELPER_INCLUDE + "\n\n" + source;
    }
}
