package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.regex.Pattern;

/**
 * Complementary's block-entity shader can use IPBR material helpers that read
 * mid-texcoord varyings, but its declarations are guarded more narrowly than
 * the matching entity/hand programs.
 */
public final class ComplementaryBlockIpbrTransformStage implements ShaderTransformStage {
    private static final String NARROW_MIDCOORD_GUARD = "#if defined GENERATED_NORMALS || defined COATED_TEXTURES || defined POM";
    private static final String IRIS_IPBR_MIDCOORD_GUARD =
            "#if defined GENERATED_NORMALS || defined COATED_TEXTURES || defined POM || defined IPBR && defined IS_IRIS";
    private static final Pattern MC_MID_TEX_COORD_ATTRIBUTE =
            Pattern.compile("(?m)^\\s*attribute\\s+vec4\\s+mc_midTexCoord\\s*;\\s*$");
    private static final Pattern AT_TANGENT_ATTRIBUTE =
            Pattern.compile("(?m)^\\s*attribute\\s+vec4\\s+at_tangent\\s*;\\s*$");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() == null
                || parameters.pass().stage() != ProgramStage.GBUFFERS
                || !usesBlockEntityFallback(parameters.pass())) {
            return source;
        }

        String transformed = source.replace(NARROW_MIDCOORD_GUARD, IRIS_IPBR_MIDCOORD_GUARD);
        if (parameters.vertexShader()) {
            transformed = MC_MID_TEX_COORD_ATTRIBUTE.matcher(transformed)
                    .replaceAll("#define mc_midTexCoord gl_MultiTexCoord0");
            transformed = AT_TANGENT_ATTRIBUTE.matcher(transformed)
                    .replaceAll("#define at_tangent vec4(1.0, 0.0, 0.0, 1.0)");
        }
        return transformed;
    }

    private static boolean usesBlockEntityFallback(RenderPass pass) {
        return switch (pass) {
            case GBUFFERS_BLOCK, GBUFFERS_BLOCK_TRANSLUCENT -> true;
            default -> false;
        };
    }
}
