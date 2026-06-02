package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.regex.Pattern;

/**
 * Entity renderers use several vanilla vertex layouts in 1.12.2. Until AUSM
 * has matching extended layouts for all of them, keep advanced-material entity
 * shaders from reading stale tangent and mid-texcoord attributes.
 */
public final class EntityAdvancedMaterialFallbackTransformStage implements ShaderTransformStage {
    private static final Pattern MC_MID_TEX_COORD_ATTRIBUTE =
            Pattern.compile("(?m)^\\s*attribute\\s+vec4\\s+mc_midTexCoord\\s*;\\s*$");
    private static final Pattern AT_TANGENT_ATTRIBUTE =
            Pattern.compile("(?m)^\\s*attribute\\s+vec4\\s+at_tangent\\s*;\\s*$");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() == null
                || parameters.pass().stage() != ProgramStage.GBUFFERS
                || !parameters.vertexShader()
                || !usesEntityFallback(parameters.pass())) {
            return source;
        }

        String transformed = MC_MID_TEX_COORD_ATTRIBUTE.matcher(source)
                .replaceAll("#define mc_midTexCoord gl_MultiTexCoord0");
        transformed = AT_TANGENT_ATTRIBUTE.matcher(transformed)
                .replaceAll("#define at_tangent vec4(1.0, 0.0, 0.0, 1.0)");
        return transformed;
    }

    private static boolean usesEntityFallback(RenderPass pass) {
        return switch (pass) {
            case GBUFFERS_ENTITIES, GBUFFERS_ENTITIES_TRANSLUCENT, GBUFFERS_ENTITIES_GLOWING,
                    GBUFFERS_HAND, GBUFFERS_HAND_WATER, GBUFFERS_ITEM, GBUFFERS_LIGHTNING,
                    GBUFFERS_ARMOR_GLINT, GBUFFERS_SPIDEREYES -> true;
            default -> false;
        };
    }
}
