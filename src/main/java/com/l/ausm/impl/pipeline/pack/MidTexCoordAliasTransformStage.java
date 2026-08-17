package com.l.ausm.impl.pipeline.pack;

import java.util.regex.Pattern;

public final class MidTexCoordAliasTransformStage implements ShaderTransformStage {
    private static final Pattern MULTI_TEX_COORD_3 = Pattern.compile("\\bgl_MultiTexCoord3\\b");
    private static final Pattern MC_MID_TEX_DECLARATION = Pattern.compile("\\b(attribute|in)\\s+vec[234]\\s+mc_midTexCoord\\b");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.vertexShader() || !MULTI_TEX_COORD_3.matcher(source).find()) {
            return source;
        }

        String transformed = MULTI_TEX_COORD_3.matcher(source).replaceAll("mc_midTexCoord");
        if (MC_MID_TEX_DECLARATION.matcher(transformed).find()) {
            return transformed;
        }

        int versionEnd = transformed.indexOf('\n');
        if (versionEnd == -1 || !transformed.startsWith("#version")) {
            return "attribute vec4 mc_midTexCoord;\n" + transformed;
        }
        return transformed.substring(0, versionEnd + 1)
                + "attribute vec4 mc_midTexCoord;\n"
                + transformed.substring(versionEnd + 1);
    }
}
