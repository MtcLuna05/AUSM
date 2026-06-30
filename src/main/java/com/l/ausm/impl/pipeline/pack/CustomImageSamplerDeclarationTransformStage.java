package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;

import java.util.regex.Pattern;

public final class CustomImageSamplerDeclarationTransformStage implements ShaderTransformStage {
    private static final Pattern VERSION_OR_EXTENSION = Pattern.compile("(?m)^(\\s*(?:#version\\b.*|#extension\\b.*)\\R)");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        return applyTo(source);
    }

    public static String applyTo(String source) {
        StringBuilder declarations = new StringBuilder();
        appendMissingSampler(source, declarations, "voxel_sampler", "usampler3D");
        appendMissingSampler(source, declarations, "floodfill_sampler", "sampler3D");
        appendMissingSampler(source, declarations, "floodfill_sampler_copy", "sampler3D");

        if (declarations.isEmpty()) {
            return source;
        }
        MainMod.LOGGER.debug("[ShaderTransform] Injected custom image sampler declarations");
        return insertAfterVersionAndExtensions(source, declarations.toString());
    }

    private static void appendMissingSampler(String source, StringBuilder declarations, String name, String type) {
        if (!containsWord(source, name) || declaresUniform(source, name)) {
            return;
        }
        declarations.append("uniform ")
                .append(type)
                .append(' ')
                .append(name)
                .append(";\n");
    }

    private static boolean containsWord(String source, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(source).find();
    }

    private static boolean declaresUniform(String source, String name) {
        return Pattern.compile("(?m)^\\s*uniform\\s+\\w+\\s+" + Pattern.quote(name) + "\\s*;").matcher(source).find();
    }

    private static String insertAfterVersionAndExtensions(String source, String insertion) {
        var matcher = VERSION_OR_EXTENSION.matcher(source);
        int insertAt = 0;
        while (matcher.find()) {
            insertAt = matcher.end();
        }
        return source.substring(0, insertAt) + insertion + source.substring(insertAt);
    }
}
