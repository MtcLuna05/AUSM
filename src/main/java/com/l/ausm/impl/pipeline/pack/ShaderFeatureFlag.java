package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.l.ausm.impl.pipeline.shader.ShaderBindingLayout;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.util.Arrays;
import java.util.Locale;

/**
 * Iris feature flags with AUSM's current support boundary.
 */
enum ShaderFeatureFlag {
    SEPARATE_HARDWARE_SAMPLERS(true, true, "OpenGL 3.3 sampler objects are required"),
    HIGHER_SHADOWCOLOR(true, true, "8 shadow color attachments and reserved sampler units are required"),
    CUSTOM_IMAGES(true, true, "OpenGL 4.2 or ARB_shader_image_load_store is required"),
    PER_BUFFER_BLENDING(true, true, "OpenGL 4.0 or ARB_draw_buffers_blend is required"),
    COMPUTE_SHADERS(true, true, "OpenGL 4.3 or ARB_compute_shader is required"),
    TESSELLATION_SHADERS(true, true, "OpenGL 4.0 or ARB_tessellation_shader is required"),
    ENTITY_TRANSLUCENT(true, false, "entity translucent shader passes are implemented"),
    REVERSED_CULLING(true, false, "shadow.culling=reversed is implemented"),
    BLOCK_EMISSION_ATTRIBUTE(true, false, "block emission is available in at_midBlock.w"),
    CAN_DISABLE_WEATHER(true, false, "weather render toggles are implemented"),
    SSBO(true, true, "OpenGL 4.3 or ARB_shader_storage_buffer_object is required"),
    FADE_VARIABLE(true, false, "per-chunk fade variable is uploaded before terrain chunk draws"),
    TEXTURE_FILTERING(true, false, "custom texture blur/clamp metadata is implemented"),
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
            case SEPARATE_HARDWARE_SAMPLERS -> capabilities.OpenGL33;
            case HIGHER_SHADOWCOLOR -> supportsHigherShadowColor();
            case TESSELLATION_SHADERS -> capabilities.OpenGL40 || capabilities.GL_ARB_tessellation_shader;
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

    private static boolean supportsHigherShadowColor() {
        int drawBuffers = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        int colorAttachments = GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
        int textureUnits = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        return drawBuffers >= ShadowFramebuffer.SHADOW_COLOR_TARGET_COUNT
                && colorAttachments >= ShadowFramebuffer.SHADOW_COLOR_TARGET_COUNT
                && textureUnits > ShaderBindingLayout.CUSTOM_TEXTURE_BASE_UNIT;
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
