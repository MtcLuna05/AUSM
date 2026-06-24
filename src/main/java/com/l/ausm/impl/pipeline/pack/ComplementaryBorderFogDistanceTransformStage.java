package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;

/**
 * Complimentary Entree used to stretch Complementary's shared renderDistance
 * through AUSM_FOG_DISTANCE_MULT. That variable feeds more than atmospheric fog:
 * border fog, cloud depth, DH/TAA fades, and distance masks all expect it to be
 * the real loaded-view distance. Keep renderDistance on upstream semantics and
 * let the shaderpack apply AUSM_FOG_DISTANCE_MULT only inside atmospheric fog.
 */
public final class ComplementaryBorderFogDistanceTransformStage implements ShaderTransformStage {
    private static final String MARKER = "AUSM: renderDistance stays real";

    private static final String DH_DISTANCE_SCALED =
            "float renderDistance = float(dhRenderDistance) * AUSM_FOG_DISTANCE_MULT;";
    private static final String DH_DISTANCE_REAL =
            "float renderDistance = float(dhRenderDistance); // " + MARKER;

    private static final String VOXY_DISTANCE_SCALED =
            "float renderDistance = (vxRenderDistance * 16.0 - 256.0) * AUSM_FOG_DISTANCE_MULT;";
    private static final String VOXY_DISTANCE_REAL =
            "float renderDistance = vxRenderDistance * 16.0 - 256.0; // " + MARKER;

    private static final String VANILLA_DISTANCE_SCALED =
            "float renderDistance = far * AUSM_FOG_DISTANCE_MULT;";
    private static final String VANILLA_DISTANCE_REAL =
            "float renderDistance = far; // " + MARKER;

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!source.contains("AUSM_FOG_DISTANCE_MULT") || source.contains(MARKER)) {
            return source;
        }

        String transformed = source
                .replace(DH_DISTANCE_SCALED, DH_DISTANCE_REAL)
                .replace(VOXY_DISTANCE_SCALED, VOXY_DISTANCE_REAL)
                .replace(VANILLA_DISTANCE_SCALED, VANILLA_DISTANCE_REAL);
        if (!transformed.equals(source)) {
            MainMod.LOGGER.debug("[ShaderTransform] Restored Complementary renderDistance to real view distance");
        }
        return transformed;
    }
}
