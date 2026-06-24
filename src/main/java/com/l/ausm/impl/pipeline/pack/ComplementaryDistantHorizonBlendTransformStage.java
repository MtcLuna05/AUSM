package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;

/**
 * Complementary enables DH_BLENDING on every pass when Distant Horizons is
 * present. AUSM renders vanilla chunks through regular gbuffers passes, so that
 * dither-discard fade can look like distant chunks randomly reloading while the
 * camera moves. Keep it only for actual DH/VOXY passes.
 */
public final class ComplementaryDistantHorizonBlendTransformStage implements ShaderTransformStage {
    private static final String DH_BLEND_GUARD =
            "    #if defined DISTANT_HORIZONS && defined TAA\n"
                    + "        #define DH_BLENDING\n"
                    + "    #endif";
    private static final String AUSM_DH_BLEND_GUARD =
            "    #if defined DISTANT_HORIZONS && defined TAA && (defined DH_TERRAIN || defined DH_WATER || defined VOXY_PATCH) // AUSM: no vanilla-pass DH dither fade\n"
                    + "        #define DH_BLENDING\n"
                    + "    #endif";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!source.contains("Complementary Shaders") || !source.contains(DH_BLEND_GUARD)) {
            return source;
        }

        String transformed = source.replace(DH_BLEND_GUARD, AUSM_DH_BLEND_GUARD);
        if (!transformed.equals(source)) {
            MainMod.LOGGER.debug("[ShaderTransform] Restricted Complementary DH_BLENDING to DH/VOXY passes");
        }
        return transformed;
    }
}
