package com.luna.ausm.impl.pipeline.pack;

/**
 * Carries AbyssalCraft's dimension atmosphere into Complementary Unbound.
 * The direct-light change is deliberately gated by ACT: without its colored
 * lighting path Unbound should retain its normal monochrome sunlight.
 */
public final class AbyssalDimensionLightingTransformStage implements ShaderTransformStage {
    private static final String MARKER = "AUSM_ABYSSAL_DIMENSION_LIGHTING";
    // Retained to preserve the loaded class schema for live transform reloads.
    @SuppressWarnings("unused")
    private static final String INCLUDE_GUARD = "#endif //INCLUDE_LIGHT_AND_AMBIENT_COLORS";
    private static final String LIGHTING = """
            // AUSM_ABYSSAL_DIMENSION_LIGHTING
            #if COLORED_LIGHTING_INTERNAL > 0 && defined AUSM_ABYSSAL_WASTELAND
                #define AUSM_ABYSSAL_LIGHT_TINT vec3(0.46, 0.70, 1.04)
                #define AUSM_ABYSSAL_AMBIENT_TINT vec3(0.55, 0.78, 1.00)
            #elif COLORED_LIGHTING_INTERNAL > 0 && defined AUSM_DREADLANDS
                #define AUSM_ABYSSAL_LIGHT_TINT vec3(1.18, 0.34, 0.24)
                #define AUSM_ABYSSAL_AMBIENT_TINT vec3(0.92, 0.31, 0.25)
            #else
                #define AUSM_ABYSSAL_LIGHT_TINT vec3(1.0)
                #define AUSM_ABYSSAL_AMBIENT_TINT vec3(1.0)
            #endif
            """;

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader()
                || source.contains(MARKER)
                || !source.contains("INCLUDE_LIGHT_AND_AMBIENT_COLORS")) {
            return source;
        }
        String withTints = source.replaceAll("(vec3\\s+lightColor\\s*=)", "$1 AUSM_ABYSSAL_LIGHT_TINT *");
        withTints = withTints.replaceAll("(vec3\\s+ambientColor\\s*=)", "$1 AUSM_ABYSSAL_AMBIENT_TINT *");
        int versionAt = withTints.indexOf("#version");
        int insertAt = versionAt < 0 ? 0 : withTints.indexOf('\n', versionAt) + 1;
        return withTints.substring(0, insertAt) + LIGHTING + '\n' + withTints.substring(insertAt);
    }
}
