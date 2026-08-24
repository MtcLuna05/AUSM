package com.luna.ausm.impl.pipeline.pack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyIntModuloTransformStage implements ShaderTransformStage {
    private static final Pattern SIMPLE_INT_MODULO = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*%\\s*(\\d+)\\b");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.compatibilityProfile() || !source.contains("%")) {
            return source;
        }

        Matcher matcher = SIMPLE_INT_MODULO.matcher(source);
        StringBuffer transformed = new StringBuffer(source.length());
        while (matcher.find()) {
            String divisor = matcher.group(2);
            matcher.appendReplacement(
                    transformed,
                    Matcher.quoteReplacement("int(mod(float(" + matcher.group(1) + "), " + divisor + ".0))")
            );
        }
        matcher.appendTail(transformed);
        return transformed.toString();
    }
}
