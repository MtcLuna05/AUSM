package com.l.ausm.impl.pipeline.pack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Declares Iris' per-chunk fade variable for vertex shaders.
 */
public final class FadeVariableTransformStage implements ShaderTransformStage {
    private static final Pattern VERSION_OR_EXTENSION = Pattern.compile("(?m)^(\\s*(?:#version\\b.*|#extension\\b.*)\\R)");
    private static final Pattern MC_CHUNK_FADE_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:uniform\\s+|attribute\\s+|varying\\s+|in\\s+|out\\s+)?float\\s+mc_chunkFade\\b"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.vertexShader()
                || !source.contains("mc_chunkFade")
                || MC_CHUNK_FADE_DECLARATION.matcher(source).find()) {
            return source;
        }

        return injectAfterVersion(source, "uniform float mc_chunkFade;\n");
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
