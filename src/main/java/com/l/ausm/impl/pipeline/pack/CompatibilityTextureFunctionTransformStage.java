package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompatibilityTextureFunctionTransformStage implements ShaderTransformStage {
    private static final Pattern INTEGER_SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^\\s*uniform\\s+[ui]sampler(?:1D|2D|3D|Cube|1DArray|2DArray|CubeArray|Buffer|2DRect)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        source = normalizeIntegerSamplerTextureCalls(source, parameters);
        if (!parameters.compatibilityProfile()) {
            return source;
        }
        return source
                .replaceAll("\\btextureLod\\s*\\(", "texture2DLod(")
                .replaceAll("\\btexture\\s*\\(", "texture2D(");
    }

    private static String normalizeIntegerSamplerTextureCalls(String source, ShaderTransformParameters parameters) {
        if (parameters.glslVersion() < 130 || !source.contains("texture2D")) {
            return source;
        }

        Set<String> samplers = new LinkedHashSet<>();
        Matcher matcher = INTEGER_SAMPLER_DECLARATION.matcher(source);
        while (matcher.find()) {
            samplers.add(matcher.group(1));
        }
        if (samplers.isEmpty()) {
            return source;
        }

        String transformed = source;
        for (String sampler : samplers) {
            transformed = Pattern.compile("\\btexture2D\\s*\\(\\s*" + Pattern.quote(sampler) + "\\b")
                    .matcher(transformed)
                    .replaceAll(Matcher.quoteReplacement("texture(" + sampler));
            transformed = Pattern.compile("\\btexture2DLod\\s*\\(\\s*" + Pattern.quote(sampler) + "\\b")
                    .matcher(transformed)
                    .replaceAll(Matcher.quoteReplacement("textureLod(" + sampler));
        }
        return transformed;
    }
}
