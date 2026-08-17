package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BloomOnlyMaskDiscardTransformStage implements ShaderTransformStage {
    private static final Pattern VERSION_OR_EXTENSION = Pattern.compile("(?m)^(\\s*(?:#version\\b.*|#extension\\b.*)\\R)");
    private static final Pattern AT_MID_BLOCK_DECLARATION =
            Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+vec4\\s+at_midBlock\\s*;");
    private static final Pattern MAIN_OPEN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() == null
                || parameters.pass().stage() != ProgramStage.GBUFFERS
                || !isBlockGbuffersPass(parameters.pass())
                || source.contains("ausm_BloomOnlyMask")) {
            return source;
        }
        if (parameters.vertexShader()) {
            return transformVertex(source, parameters);
        }
        if (parameters.fragmentShader()) {
            return transformFragment(source, parameters);
        }
        return source;
    }

    private static String transformVertex(String source, ShaderTransformParameters parameters) {
        Matcher matcher = MAIN_OPEN.matcher(source);
        if (!matcher.find()) {
            return source;
        }
        String declaration = (parameters.compatibilityProfile() ? "varying" : "out") + " float ausm_BloomOnlyMask;\n";
        if (!AT_MID_BLOCK_DECLARATION.matcher(source).find()) {
            declaration += (parameters.compatibilityProfile() ? "attribute" : "in") + " vec4 at_midBlock;\n";
        }
        String transformed = injectAfterVersion(source, declaration);
        matcher = MAIN_OPEN.matcher(transformed);
        if (!matcher.find()) {
            return transformed;
        }
        return transformed.substring(0, matcher.end())
                + "\n    ausm_BloomOnlyMask = at_midBlock.w > 15.5 ? 1.0 : 0.0;"
                + transformed.substring(matcher.end());
    }

    private static String transformFragment(String source, ShaderTransformParameters parameters) {
        Matcher matcher = MAIN_OPEN.matcher(source);
        if (!matcher.find()) {
            return source;
        }
        String transformed = injectAfterVersion(source,
                (parameters.compatibilityProfile() ? "varying" : "in") + " float ausm_BloomOnlyMask;\n");
        matcher = MAIN_OPEN.matcher(transformed);
        if (!matcher.find()) {
            return transformed;
        }
        return transformed.substring(0, matcher.end())
                + "\n    if (ausm_BloomOnlyMask > 0.5) { discard; }"
                + transformed.substring(matcher.end());
    }

    private static boolean isBlockGbuffersPass(RenderPass pass) {
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP
                || pass == RenderPass.GBUFFERS_DAMAGEDBLOCK
                || pass == RenderPass.GBUFFERS_BLOCK
                || pass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_WATER;
    }

    private static String injectAfterVersion(String source, String declaration) {
        Matcher matcher = VERSION_OR_EXTENSION.matcher(source);
        int insertAt = 0;
        while (matcher.find()) {
            insertAt = matcher.end();
        }
        return source.substring(0, insertAt) + declaration + source.substring(insertAt);
    }
}
