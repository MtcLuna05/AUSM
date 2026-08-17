package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ProgramStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backports Iris' composite center-depth transformer for packs that still
 * declare centerDepthSmooth as a float.
 */
public final class CompositeDepthSmoothTransformStage implements ShaderTransformStage {
    private static final Pattern CENTER_DEPTH_SMOOTH_DECLARATION =
            Pattern.compile("(?m)^\\s*uniform\\s+float\\s+centerDepthSmooth\\s*;\\s*$");
    private static final Pattern CENTER_DEPTH_SMOOTH_REFERENCE = Pattern.compile("\\bcenterDepthSmooth\\b");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() == null || !parameters.fragmentShader() || !parameters.pass().stage().isFullscreenStage()) {
            return source;
        }
        if (parameters.pass().stage() == ProgramStage.PREPARE || parameters.pass().stage() == ProgramStage.SHADOW) {
            return source;
        }

        Matcher declaration = CENTER_DEPTH_SMOOTH_DECLARATION.matcher(source);
        if (!declaration.find()) {
            return source;
        }

        String transformed = declaration.replaceFirst("uniform sampler2D iris_centerDepthSmooth;");
        String sample = parameters.compatibilityProfile()
                ? "texture2D(iris_centerDepthSmooth, vec2(0.5)).r"
                : "texture(iris_centerDepthSmooth, vec2(0.5)).r";
        return CENTER_DEPTH_SMOOTH_REFERENCE.matcher(transformed).replaceAll(Matcher.quoteReplacement(sample));
    }
}
