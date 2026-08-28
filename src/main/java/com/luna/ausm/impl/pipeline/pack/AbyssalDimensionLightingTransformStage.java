package com.luna.ausm.impl.pipeline.pack;

/**
 * Carries AbyssalCraft's dimension atmosphere into Complementary Unbound.
 * The direct-light tint requires ACT's colored-lighting path and can be
 * disabled independently in AUSM's shader settings.
 */
public final class AbyssalDimensionLightingTransformStage implements ShaderTransformStage {
    private static final String MARKER = "AUSM_ABYSSAL_DIMENSION_LIGHTING";
    private static final String DREADLANDS_LIGHTSHAFT_MARKER = "AUSM_DREADLANDS_DISABLE_LIGHTSHAFTS";
    private static final String ABYSSAL_LENS_FLARE_MARKER = "AUSM_ABYSSAL_DISABLE_LENS_FLARE";
    private static final String LIGHTING = """
            // AUSM_ABYSSAL_DIMENSION_LIGHTING
            #if COLORED_LIGHTING_INTERNAL > 0 && AUSM_ABYSSAL_SUNLIGHT == 1 && defined AUSM_ABYSSAL_WASTELAND
                #define AUSM_ABYSSAL_LIGHT_TINT vec3(0.46, 0.70, 1.04)
                #define AUSM_ABYSSAL_AMBIENT_TINT vec3(0.55, 0.78, 1.00)
            #elif COLORED_LIGHTING_INTERNAL > 0 && AUSM_ABYSSAL_SUNLIGHT == 1 && defined AUSM_DREADLANDS
                #define AUSM_ABYSSAL_LIGHT_TINT vec3(1.18, 0.34, 0.24)
                #define AUSM_ABYSSAL_AMBIENT_TINT vec3(0.92, 0.31, 0.25)
            #else
                #define AUSM_ABYSSAL_LIGHT_TINT vec3(1.0)
                #define AUSM_ABYSSAL_AMBIENT_TINT vec3(1.0)
            #endif
            """;

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader() || source.contains(MARKER)) {
            return source;
        }
        if (source.contains("void DoLensFlare")) {
            return disableAbyssalLensFlare(source);
        }
        if (!source.contains("INCLUDE_LIGHT_AND_AMBIENT_COLORS")) {
            return source;
        }
        String withTints = source.replaceAll("(vec3\\s+lightColor\\s*=)", "$1 AUSM_ABYSSAL_LIGHT_TINT *");
        withTints = withTints.replaceAll("(vec3\\s+ambientColor\\s*=)", "$1 AUSM_ABYSSAL_AMBIENT_TINT *");
        withTints = disableDreadlandsLightShafts(withTints);
        int versionAt = withTints.indexOf("#version");
        int insertAt = versionAt < 0 ? 0 : withTints.indexOf('\n', versionAt) + 1;
        return withTints.substring(0, insertAt) + LIGHTING + '\n' + withTints.substring(insertAt);
    }

    private static String disableDreadlandsLightShafts(String source) {
        if (source.contains(DREADLANDS_LIGHTSHAFT_MARKER) || !source.contains("GetVolumetricLight")) {
            return source;
        }
        return source.replaceFirst(
                "(vec4\\s+GetVolumetricLight\\s*\\([^)]*\\)\\s*\\{)",
                "$1\n    #if defined AUSM_DREADLANDS\n"
                        + "        // " + DREADLANDS_LIGHTSHAFT_MARKER + "\n"
                        + "        return vec4(0.0);\n"
                        + "    #endif");
    }

    private static String disableAbyssalLensFlare(String source) {
        return source.replaceFirst(
                "(void\\s+DoLensFlare\\s*\\([^)]*\\)\\s*\\{)",
                "$1\n    #if defined AUSM_ABYSSAL_WASTELAND || defined AUSM_DREADLANDS\n"
                        + "        // " + MARKER + " " + ABYSSAL_LENS_FLARE_MARKER + "\n"
                        + "        return;\n"
                        + "    #endif");
    }
}
