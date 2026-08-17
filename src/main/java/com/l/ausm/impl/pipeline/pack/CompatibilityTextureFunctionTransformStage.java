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
    private static final Pattern INTEGER_TEXTURE_CALL = Pattern.compile(
            "\\btexture2D(Lod)?\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\b"
    );
    private static final Pattern TEXTURE_LOD_CALL = Pattern.compile("\\btextureLod\\s*\\(");
    private static final Pattern TEXTURE_CALL = Pattern.compile("\\btexture\\s*\\(");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        source = normalizeIntegerSamplerTextureCalls(source, parameters);
        if (!parameters.compatibilityProfile()) {
            return source;
        }
        source = TEXTURE_LOD_CALL.matcher(source).replaceAll("texture2DLod(");
        return TEXTURE_CALL.matcher(source).replaceAll("texture2D(");
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

        Matcher calls = INTEGER_TEXTURE_CALL.matcher(source);
        StringBuffer transformed = new StringBuffer(source.length());
        boolean changed = false;
        while (calls.find()) {
            String sampler = calls.group(2);
            if (!samplers.contains(sampler)) {
                continue;
            }
            String function = calls.group(1) != null ? "textureLod" : "texture";
            calls.appendReplacement(transformed, Matcher.quoteReplacement(function + "(" + sampler));
            changed = true;
        }
        if (!changed) {
            return source;
        }
        calls.appendTail(transformed);
        return transformed.toString();
    }
}
