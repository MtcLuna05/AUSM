package com.l.ausm.impl.pipeline.pack;

/**
 * Complementary hides a few Iris/render-stage based effects behind Minecraft
 * version checks. AUSM exposes the required Iris-style data on 1.12, so those
 * checks can be widened without enabling modern block/material remaps.
 */
public final class ComplementarySupportedVersionFeaturesTransformStage implements ShaderTransformStage {
    private static final String IMPROVED_RAIN_GUARD =
            "#if IMPROVED_RAIN_DEFINE == 1 && !defined MC_OS_MAC && !defined COLOR_CODED_PROGRAMS && MC_VERSION >= 11605";
    private static final String AUSM_IMPROVED_RAIN_GUARD =
            "#if IMPROVED_RAIN_DEFINE == 1 && !defined MC_OS_MAC && !defined COLOR_CODED_PROGRAMS // AUSM: improved rain data is available on 1.12";

    private static final String TEXTURED_FOG_GUARD = "#if MC_VERSION >= 11500";
    private static final String AUSM_TEXTURED_FOG_GUARD =
            "#if defined IS_IRIS || MC_VERSION >= 11500 // AUSM: textured particle fog path is supported on 1.12";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!source.contains("Complementary Shaders")) {
            return source;
        }

        String transformed = source.replace(IMPROVED_RAIN_GUARD, AUSM_IMPROVED_RAIN_GUARD);

        if (parameters.fragmentShader()
                && transformed.contains("DoFog(color, sky")) {
            transformed = transformed.replace(TEXTURED_FOG_GUARD, AUSM_TEXTURED_FOG_GUARD);
        }

        return transformed;
    }
}
