package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.pack.ShaderFeatureSet;
import org.lwjgl.opengl.GLContext;

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
        validateGeometryCapability(capabilities.geometry(), errors);
        validateImplicitCapability(capabilities.tessellation(), ShaderFeatureFlag.TESSELLATION_SHADERS, "tessellation shader sources", errors);

        if (capabilities.extraProgramArrayEntries()) {
            warnings.add("Pack declares indexed fullscreen program-array entries outside AUSM's current 1.12 adapter coverage.");
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

    private static void validateGeometryCapability(boolean used, List<String> errors) {
        if (!used || GLContext.getCapabilities().OpenGL32) {
            return;
        }
        errors.add("geometry shader sources require OpenGL 3.2 geometry shader support");
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
