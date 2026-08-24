package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;

/**
 * Complementary's shared lighting include assumes terrain texture coordinates
 * whenever pixelated lighting is enabled. The line program deliberately has no
 * texCoord varying, so disable only that optional path for gbuffers_line.
 */
public final class PixelatedLineCompatibilityTransformStage implements ShaderTransformStage {
    private static final String MARKER = "// AUSM: gbuffers_line has no texCoord for pixelated lighting";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader()
                || parameters.pass() != RenderPass.GBUFFERS_LINE
                || source.contains(MARKER)
                || !source.contains("ComputeTexelOffset(tex, texCoord)")
                || !source.contains("DO_PIXELATION_EFFECTS")) {
            return source;
        }

        int insertion = source.indexOf("//Lighting//");
        if (insertion < 0) {
            insertion = source.indexOf("void DoLighting");
        }
        if (insertion < 0) {
            return source;
        }

        String guard = "#ifdef DO_PIXELATION_EFFECTS\n"
                + "#undef DO_PIXELATION_EFFECTS\n"
                + "#endif\n"
                + MARKER + "\n";
        return source.substring(0, insertion) + guard + source.substring(insertion);
    }
}
