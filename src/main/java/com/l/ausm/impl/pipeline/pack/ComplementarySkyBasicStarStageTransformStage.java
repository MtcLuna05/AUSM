package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

/**
 * Complementary's legacy 1.12 star detection treats grayscale sky vertices as
 * vanilla stars. Overcast/gray sky colors can therefore discard the whole
 * skybasic dome, including procedural sun/moon. AUSM exposes Iris renderStage,
 * so use it directly.
 */
public final class ComplementarySkyBasicStarStageTransformStage implements ShaderTransformStage {
    private static final String LEGACY_STAGE_GUARD =
            "#if MC_VERSION >= 11605 && (defined IS_ANGELICA || defined IS_IRIS)";
    private static final String AUSM_STAGE_GUARD =
            "#if defined IS_IRIS || defined IS_ANGELICA || MC_VERSION >= 11605 // AUSM: renderStage is reliable on 1.12";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() != RenderPass.GBUFFERS_SKYBASIC) {
            return source;
        }
        String transformed = source;
        if (parameters.vertexShader()
                && transformed.contains("vanillaStars = float(renderStage == MC_RENDER_STAGE_STARS)")
                && transformed.contains("Vanilla Star Dedection by Builderb0y")) {
            transformed = transformed.replace(LEGACY_STAGE_GUARD, AUSM_STAGE_GUARD);
        }
        return transformed;
    }
}
