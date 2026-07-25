package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes Entree's AUSM fluid material range usable by shader packs that only
 * recognize Complementary's standard water material ID.
 */
public final class ModdedFluidCompatibilityTransformStage implements ShaderTransformStage {
    private static final String MATERIAL_MARKER = "AUSM_MODDED_FLUID_COMPAT";
    private static final Pattern MATERIAL_ASSIGNMENT = Pattern.compile(
            "(?m)^(\\s*mat\\s*=\\s*int\\s*\\(\\s*mc_Entity\\.x\\s*\\+\\s*0\\.5\\s*\\)\\s*;)"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() != RenderPass.GBUFFERS_WATER) {
            return source;
        }

        if (!parameters.vertexShader() || source.contains(MATERIAL_MARKER)) {
            return source;
        }

        Matcher matcher = MATERIAL_ASSIGNMENT.matcher(source);
        if (!matcher.find()) {
            return source;
        }
        String replacement = matcher.group(1)
                + "\n    // " + MATERIAL_MARKER
                + "\n    if (mat >= 32620 && mat <= 32639) mat = 32000;";
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }
}
