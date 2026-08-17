package com.l.ausm.impl.pipeline.pack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacySamplerAliasTransformStage implements ShaderTransformStage {
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile("(?m)^(\\s*uniform\\s+sampler\\w+\\s+)(gcolor|texture)(\\s*;\\s*)$");
    private static final Pattern WORD_TEXTURE = Pattern.compile("\\btexture\\b(?!\\s*\\()");
    private static final Pattern WORD_GCOLOR = Pattern.compile("\\bgcolor\\b");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader()) {
            return source;
        }

        Matcher matcher = SAMPLER_DECLARATION.matcher(source);
        StringBuffer out = new StringBuffer();
        boolean renamedTexture = false;
        boolean renamedGcolor = false;

        while (matcher.find()) {
            String name = matcher.group(2);
            if ("texture".equals(name)) {
                renamedTexture = true;
            } else if ("gcolor".equals(name)) {
                renamedGcolor = true;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + "gtexture" + matcher.group(3)));
        }
        matcher.appendTail(out);

        String transformed = out.toString();
        if (renamedTexture) {
            transformed = WORD_TEXTURE.matcher(transformed).replaceAll("gtexture");
        }
        if (renamedGcolor) {
            transformed = WORD_GCOLOR.matcher(transformed).replaceAll("gtexture");
        }
        return transformed;
    }
}
