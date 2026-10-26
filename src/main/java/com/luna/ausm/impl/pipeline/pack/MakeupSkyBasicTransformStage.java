package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Separates MakeUp's vanilla sky dome from its grayscale star geometry. */
final class MakeupSkyBasicTransformStage implements ShaderTransformStage {
    static final String MARKER = "AUSM_MAKEUP_SKY_PHASE_MASK";
    private static final String MAKEUP_PROGRAM = "MakeUp - gbuffers_skybasic.vsh";
    private static final Pattern VERSION_OR_EXTENSION = Pattern.compile(
            "(?m)^(\\s*(?:#version\\b.*|#extension\\b.*)\\R)"
    );
    private static final Pattern STAR_TEST = Pattern.compile(
            "float\\(\\s*gl_Color\\.r\\s*==\\s*gl_Color\\.g\\s*&&\\s*"
                    + "gl_Color\\.g\\s*==\\s*gl_Color\\.b\\s*&&\\s*"
                    + "gl_Color\\.r\\s*>\\s*0\\.0\\s*\\)"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.vertexShader()
                || parameters.pass() != RenderPass.GBUFFERS_SKYBASIC
                || !source.contains(MAKEUP_PROGRAM)
                || source.contains(MARKER)) {
            return source;
        }

        Matcher starTest = STAR_TEST.matcher(source);
        if (!starTest.find()) {
            return source;
        }

        String transformed = starTest.replaceFirst(Matcher.quoteReplacement(
                "float(renderStage == MC_RENDER_STAGE_STARS && "
                        + "gl_Color.r == gl_Color.g && gl_Color.g == gl_Color.b && gl_Color.r > 0.0)"
        ));
        String declaration = "// " + MARKER + "\nuniform int renderStage;\n";
        Matcher insertionPoint = VERSION_OR_EXTENSION.matcher(transformed);
        int insertAt = 0;
        while (insertionPoint.find()) {
            insertAt = insertionPoint.end();
        }
        return transformed.substring(0, insertAt) + declaration + transformed.substring(insertAt);
    }
}
