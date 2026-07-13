package com.l.ausm.impl.pipeline.pack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoidNoiseDisableTransformStage implements ShaderTransformStage {
    private static final Pattern VOID_NOISE_DEFINE = Pattern.compile(
            "(?m)^(\\s*#\\s*define\\s+(?:AUSM_VOID_SKYBOX|AUSM_VOID_CELESTIALS|AUSM_VOID_CUSTOM_MOON|AUSM_VOID_PLANETS|"
                    + "AUSM_VOID_STARS|AUSM_VOID_ASTRAL_STARS|AUSM_VOID_NEBULA)\\s+)(\\S+)(.*)$"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (source == null || !source.contains("AUSM_VOID_")) {
            return source;
        }

        Matcher matcher = VOID_NOISE_DEFINE.matcher(source);
        StringBuffer transformed = new StringBuffer(source.length());
        boolean changed = false;
        while (matcher.find()) {
            matcher.appendReplacement(transformed, Matcher.quoteReplacement(matcher.group(1) + "0" + matcher.group(3)));
            changed = true;
        }
        if (!changed) {
            return source;
        }
        matcher.appendTail(transformed);
        return transformed.toString();
    }
}
