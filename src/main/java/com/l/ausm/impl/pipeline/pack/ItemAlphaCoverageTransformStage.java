package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces the alpha-test coverage that OptiFine item/hand programs expect.
 *
 * <p>Compatibility-profile alpha testing is not a reliable substitute for a
 * fragment discard with shader-pack MRT programs.  A transparent fragment can
 * otherwise still update depth and material attachments, turning the item's
 * complete baked quad into hand geometry for glint, water refraction and
 * temporal post-processing.</p>
 */
public final class ItemAlphaCoverageTransformStage implements ShaderTransformStage {
    static final String MARKER = "AUSM_ITEM_ALPHA_COVERAGE";
    private static final Pattern VERSION = Pattern.compile("(?m)^(\\s*#version\\b.*\\R)");
    private static final Pattern COLOR_SAMPLE = Pattern.compile(
            "(?m)^(\\s*vec4\\s+color\\s*=\\s*(?:texture2D|texture)\\s*\\(\\s*"
                    + "(?:tex|texture|gtexture)\\s*,\\s*texCoord\\s*\\)\\s*;)$"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader()
                || !usesItemCoverage(parameters.pass())
                || source.contains(MARKER)) {
            return source;
        }
        return transformFragment(source);
    }

    static String transformFragment(String source) {
        if (source.contains(MARKER)) {
            return source;
        }
        Matcher sample = COLOR_SAMPLE.matcher(source);
        if (!sample.find()) {
            return source;
        }

        String declaration = "// " + MARKER + "\nuniform float ausmItemAlphaTestRef;\n";
        Matcher version = VERSION.matcher(source);
        String transformed = version.find()
                ? source.substring(0, version.end()) + declaration + source.substring(version.end())
                : declaration + source;

        sample = COLOR_SAMPLE.matcher(transformed);
        if (!sample.find()) {
            return source;
        }
        String indent = leadingWhitespace(sample.group(1));
        return transformed.substring(0, sample.end())
                + "\n" + indent + "if (color.a <= ausmItemAlphaTestRef) discard;"
                + transformed.substring(sample.end());
    }

    private static boolean usesItemCoverage(RenderPass pass) {
        return pass == RenderPass.GBUFFERS_TEXTURED
                || pass == RenderPass.GBUFFERS_TEXTURED_LIT
                || pass == RenderPass.GBUFFERS_ITEM
                || pass == RenderPass.GBUFFERS_HAND
                || pass == RenderPass.GBUFFERS_HAND_WATER;
    }

    private static String leadingWhitespace(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return line.substring(0, index);
    }
}
