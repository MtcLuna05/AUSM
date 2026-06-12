package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.pack.ShaderFeatureSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates Iris feature declarations before AUSM allocates GL resources.
 */
public final class ShaderFeatureValidator {

    private ShaderFeatureValidator() {
    }

    public static Result validate(ShaderPackDirectives directives) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        ShaderFeatureSet features = directives.features();

        for (String feature : features.required()) {
            validateExplicitFeature(feature, true, errors, warnings);
        }
        for (String feature : features.optional()) {
            validateExplicitFeature(feature, false, errors, warnings);
        }

        ShaderPipelineCapabilities capabilities = directives.capabilities();
        validateImplicitCapability(capabilities.compute(), ShaderFeatureFlag.COMPUTE_SHADERS, "compute shader sources", errors);
        validateImplicitCapability(capabilities.images(), ShaderFeatureFlag.CUSTOM_IMAGES, "image directives", errors);
        validateImplicitCapability(capabilities.storageBuffers(), ShaderFeatureFlag.SSBO, "bufferObject directives", errors);
        validateImplicitCapability(capabilities.perBufferBlending(), ShaderFeatureFlag.PER_BUFFER_BLENDING, "per-buffer blend directives", errors);

        if (capabilities.extraProgramArrayEntries()) {
            warnings.add("Pack declares program array entries beyond AUSM's fixed 1.12 fullscreen pass slots; extra entries will be ignored.");
        }
        return new Result(List.copyOf(errors), List.copyOf(warnings));
    }

    private static void validateExplicitFeature(String feature, boolean required, List<String> errors, List<String> warnings) {
        ShaderFeatureFlag flag = ShaderFeatureFlag.fromName(feature);
        if (flag != ShaderFeatureFlag.UNKNOWN && flag.implemented() && flag.hardwareSupported()) {
            return;
        }

        String message = feature + ": " + flag.unavailableReason();
        if (required) {
            errors.add("Required Iris feature is unavailable: " + message);
        } else {
            warnings.add("Optional Iris feature is unavailable: " + message);
        }
    }

    private static void validateImplicitCapability(boolean used, ShaderFeatureFlag flag, String source, List<String> errors) {
        if (!used || flag.hardwareSupported()) {
            return;
        }
        errors.add(source + " require " + flag.name().toLowerCase(java.util.Locale.ROOT) + ": " + flag.unavailableReason());
    }

    public record Result(List<String> errors, List<String> warnings) {
        public boolean supported() {
            return errors.isEmpty();
        }

        public String summary() {
            if (!errors.isEmpty()) {
                return String.join("; ", errors);
            }
            return String.join("; ", warnings);
        }
    }
}
