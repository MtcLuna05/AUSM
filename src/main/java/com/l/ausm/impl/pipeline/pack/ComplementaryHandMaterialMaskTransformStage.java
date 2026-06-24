package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

/**
 * Complementary's hand shader is intended to mark hand pixels as no-TAA/no-SSAO.
 * Some 1.12 item paths reach the hand shader with generic/unknown item material
 * state and can clear that mask, which makes the final composite treat hand
 * pixels like world history. Keep the upstream hand mask for every surviving
 * hand fragment without changing non-hand programs.
 */
public final class ComplementaryHandMaterialMaskTransformStage implements ShaderTransformStage {
    private static final String HAND_MATERIAL_OUTPUT =
            "gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0);";
    private static final String PATCHED_HAND_MATERIAL_OUTPUT =
            "gl_FragData[1] = vec4(smoothnessD, max(materialMask, OSIEBCA * 254.0), skyLightFactor, 1.0);";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader() || parameters.pass() != RenderPass.GBUFFERS_HAND) {
            return source;
        }
        return source.replace(HAND_MATERIAL_OUTPUT, PATCHED_HAND_MATERIAL_OUTPUT);
    }
}
