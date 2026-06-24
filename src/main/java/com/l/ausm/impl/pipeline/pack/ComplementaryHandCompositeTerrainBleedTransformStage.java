package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.MainMod;

/**
 * Complementary's composite1 pass samples pre-hand terrain/translucency buffers.
 * AUSM renders the hand after deferred terrain work, so hand pixels can inherit
 * stale terrain effects unless they are explicitly treated as opaque hand pixels.
 */
public final class ComplementaryHandCompositeTerrainBleedTransformStage implements ShaderTransformStage {
    private static final String COLOR_READ =
            "vec3 color = texelFetch(colortex0, texelCoord, 0).rgb;";
    private static final String PATCHED_COLOR_READ =
            "vec3 color = texelFetch(colortex0, texelCoord, 0).rgb;\n"
                    + "    vec3 ausmHandBaseColor = color;";
    private static final String DEPTH1_READ =
            "float z1 = texelFetch(depthtex1, texelCoord, 0).r;";
    private static final String PATCHED_DEPTH1_READ =
            "float z1 = texelFetch(depthtex1, texelCoord, 0).r;\n"
                    + "    int ausmMaterialMaskInt = int(texelFetch(colortex6, texelCoord, 0).g * 255.1);\n"
                    + "    bool ausmHandOpaquePixel = ausmMaterialMaskInt == 254;";
    private static final String TRANSLUCENT_MULT =
            "vec3 translucentMult = 1.0 - texelFetch(colortex3, texelCoord, 0).rgb; //TM5723";
    private static final String PATCHED_TRANSLUCENT_MULT =
            "vec3 translucentMult = ausmHandOpaquePixel ? vec3(1.0) : 1.0 - texelFetch(colortex3, texelCoord, 0).rgb; // AUSM: avoid pre-hand terrain bleed";
    private static final String COLOR_GAMMA =
            "color = pow(color, vec3(2.2));";
    private static final String PATCHED_COLOR_GAMMA = """
    if (ausmHandOpaquePixel) {
        color = ausmHandBaseColor;
        volumetricEffect = vec4(0.0);
    }

    color = pow(color, vec3(2.2));""";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader() || parameters.pass() != RenderPass.COMPOSITE1) {
            return source;
        }
        if (!looksLikeComplementaryComposite1(source) || source.contains("ausmHandOpaquePixel")) {
            return source;
        }

        String transformed = source
                .replace(COLOR_READ, PATCHED_COLOR_READ)
                .replace(DEPTH1_READ, PATCHED_DEPTH1_READ)
                .replace(TRANSLUCENT_MULT, PATCHED_TRANSLUCENT_MULT)
                .replace(COLOR_GAMMA, PATCHED_COLOR_GAMMA);
        if (!isCompletePatch(transformed)) {
            MainMod.LOGGER.warn("[ShaderTransform] Skipped incomplete Complementary composite1 hand terrain bleed patch");
            return source;
        }
        if (!transformed.equals(source)) {
            MainMod.LOGGER.debug("[ShaderTransform] Patched Complementary composite1 hand terrain bleed");
        }
        return transformed;
    }

    private static boolean looksLikeComplementaryComposite1(String source) {
        return source.contains("Complementary Shaders by EminGT")
                && source.contains("vec3 translucentMult = 1.0 - texelFetch(colortex3, texelCoord, 0).rgb; //TM5723")
                && source.contains("volumetricEffect")
                && source.contains(COLOR_GAMMA);
    }

    private static boolean isCompletePatch(String source) {
        return source.contains("vec3 ausmHandBaseColor = color;")
                && source.contains("bool ausmHandOpaquePixel = ausmMaterialMaskInt == 254;")
                && source.contains("vec3 translucentMult = ausmHandOpaquePixel ? vec3(1.0)")
                && source.contains("color = ausmHandBaseColor;");
    }
}
