package com.l.ausm.impl.pipeline.pack;

import org.lwjgl.opengl.GLContext;

import java.util.Arrays;
import java.util.Locale;

/**
 * Iris feature flags with AUSM's current support boundary.
 */
enum ShaderFeatureFlag {
    SEPARATE_HARDWARE_SAMPLERS(false, false, "separate sampler objects are not implemented"),
    HIGHER_SHADOWCOLOR(false, false, "only the current shadowcolor0 path is implemented"),
    CUSTOM_IMAGES(true, true, "OpenGL 4.2 or ARB_shader_image_load_store is required"),
    PER_BUFFER_BLENDING(true, true, "OpenGL 4.0 or ARB_draw_buffers_blend is required"),
    COMPUTE_SHADERS(true, true, "OpenGL 4.3 or ARB_compute_shader is required"),
    TESSELLATION_SHADERS(false, false, "tessellation shader stages are not implemented"),
    ENTITY_TRANSLUCENT(true, false, "entity translucent shader passes are implemented"),
    REVERSED_CULLING(false, false, "reversed culling is not implemented"),
    BLOCK_EMISSION_ATTRIBUTE(true, false, "block emission is available in at_midBlock.w"),
    CAN_DISABLE_WEATHER(true, false, "weather render toggles are implemented"),
    SSBO(true, true, "OpenGL 4.3 or ARB_shader_storage_buffer_object is required"),
    FADE_VARIABLE(true, false, "per-chunk fade variable is uploaded before terrain chunk draws"),
    TEXTURE_FILTERING(false, false, "Iris texture filtering directives are not implemented"),
    UNKNOWN(false, false, "unknown Iris feature flag");

    private final boolean implemented;
    private final boolean hardwareChecked;
    private final String unavailableReason;

    ShaderFeatureFlag(boolean implemented, boolean hardwareChecked, String unavailableReason) {
        this.implemented = implemented;
        this.hardwareChecked = hardwareChecked;
        this.unavailableReason = unavailableReason;
    }

    static ShaderFeatureFlag fromName(String rawName) {
        String normalized = normalize(rawName);
        return Arrays.stream(values())
                .filter(flag -> flag != UNKNOWN && flag.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElse(UNKNOWN);
    }

    boolean implemented() {
        return implemented;
    }

    boolean hardwareSupported() {
        if (!implemented || !hardwareChecked) {
            return implemented;
        }
        var capabilities = GLContext.getCapabilities();
        return switch (this) {
            case CUSTOM_IMAGES -> capabilities.OpenGL42 || capabilities.GL_ARB_shader_image_load_store;
            case PER_BUFFER_BLENDING -> capabilities.OpenGL40 || capabilities.GL_ARB_draw_buffers_blend;
            case COMPUTE_SHADERS -> capabilities.OpenGL43 || capabilities.GL_ARB_compute_shader;
            case SSBO -> capabilities.OpenGL43 || capabilities.GL_ARB_shader_storage_buffer_object;
            default -> true;
        };
    }

    String unavailableReason() {
        if (!implemented) {
            return unavailableReason;
        }
        if (!hardwareSupported()) {
            return unavailableReason;
        }
        return "";
    }

    private static String normalize(String rawName) {
        if (rawName == null) {
            return "";
        }
        String normalized = rawName.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if ("tesselation_shaders".equals(normalized)) {
            return "tessellation_shaders";
        }
        return normalized;
    }
}
