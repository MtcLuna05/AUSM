package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps shader-pack item glint inside the alpha coverage of the item's atlas
 * sprite. Vanilla normally gets this mask indirectly from GL_EQUAL against
 * the base item depth, but shader-pack hand programs can leave depth on empty
 * texels. The explicit atlas test is item-only; armor glint remains unchanged.
 */
public final class ItemGlintCoverageTransformStage implements ShaderTransformStage {
    static final String MARKER = "AUSM_ITEM_GLINT_COVERAGE";
    private static final Pattern VERSION = Pattern.compile("(?m)^(\\s*#version\\b.*\\R)");
    private static final Pattern VERTEX_TEXCOORD_ASSIGNMENT = Pattern.compile(
            "(?m)^(\\s*texCoord\\s*=\\s*\\([^;]*gl_MultiTexCoord0[^;]*;)$"
    );
    private static final Pattern FRAGMENT_COLOR_SAMPLE = Pattern.compile(
            "(?m)^(\\s*vec4\\s+color\\s*=\\s*texture2D\\s*\\(\\s*tex\\s*,\\s*texCoord\\s*\\)\\s*;)$"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() != RenderPass.GBUFFERS_ARMOR_GLINT || source.contains(MARKER)) {
            return source;
        }
        if (parameters.vertexShader()) {
            return transformVertex(source);
        }
        if (parameters.fragmentShader()) {
            return transformFragment(source);
        }
        return source;
    }

    static String transformVertex(String source) {
        if (source.contains(MARKER)) {
            return source;
        }
        Matcher assignment = VERTEX_TEXCOORD_ASSIGNMENT.matcher(source);
        if (!assignment.find()) {
            return source;
        }
        String declaration = "// " + MARKER + "\nout vec2 ausmItemGlintBaseTexCoord;\n";
        String transformed = injectAfterVersion(source, declaration);
        assignment = VERTEX_TEXCOORD_ASSIGNMENT.matcher(transformed);
        if (!assignment.find()) {
            return source;
        }
        return transformed.substring(0, assignment.end())
                + "\n" + leadingWhitespace(assignment.group(1))
                + "ausmItemGlintBaseTexCoord = gl_MultiTexCoord0.xy;"
                + transformed.substring(assignment.end());
    }

    static String transformFragment(String source) {
        if (source.contains(MARKER)) {
            return source;
        }
        Matcher sample = FRAGMENT_COLOR_SAMPLE.matcher(source);
        if (!sample.find()) {
            return source;
        }
        String declaration = "// " + MARKER + "\n"
                + "uniform sampler2D ausmItemGlintBaseAtlas;\n"
                + "uniform int ausmItemGlintMask;\n"
                + "uniform float ausmItemAlphaTestRef;\n"
                + "in vec2 ausmItemGlintBaseTexCoord;\n";
        String transformed = injectAfterVersion(source, declaration);
        sample = FRAGMENT_COLOR_SAMPLE.matcher(transformed);
        if (!sample.find()) {
            return source;
        }
        String indent = leadingWhitespace(sample.group(1));
        return transformed.substring(0, sample.end())
                + "\n" + indent
                + "if (ausmItemGlintMask == 1 && texture2D(ausmItemGlintBaseAtlas, "
                + "ausmItemGlintBaseTexCoord).a <= max(ausmItemAlphaTestRef, 0.001)) discard;"
                + transformed.substring(sample.end());
    }

    private static String injectAfterVersion(String source, String declaration) {
        Matcher matcher = VERSION.matcher(source);
        return matcher.find()
                ? source.substring(0, matcher.end()) + declaration + source.substring(matcher.end())
                : declaration + source;
    }

    private static String leadingWhitespace(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return line.substring(0, index);
    }
}
