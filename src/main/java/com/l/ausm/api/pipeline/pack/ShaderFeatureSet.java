package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public record ShaderFeatureSet(
        List<String> required,
        List<String> optional
) {
    public static ShaderFeatureSet empty() {
        return new ShaderFeatureSet(List.of(), List.of());
    }

    public static ShaderFeatureSet parse(Properties properties) {
        return new ShaderFeatureSet(
                parseList(properties.getProperty("iris.features.required")),
                parseList(properties.getProperty("iris.features.optional"))
        );
    }

    public boolean requires(String feature) {
        return required.contains(normalize(feature));
    }

    public boolean optional(String feature) {
        return optional.contains(normalize(feature));
    }

    private static List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.trim().split("\\s+"))
                .map(ShaderFeatureSet::normalize)
                .filter(token -> !token.isBlank())
                .distinct()
                .toList();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
