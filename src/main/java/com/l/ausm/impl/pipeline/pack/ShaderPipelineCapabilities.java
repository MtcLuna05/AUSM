package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

public record ShaderPipelineCapabilities(
        boolean compute,
        boolean images,
        boolean storageBuffers,
        boolean customUniforms,
        boolean customTextures,
        boolean extraProgramArrayEntries
) {
    public static ShaderPipelineCapabilities from(ShaderPackDirectives directives) {
        return new ShaderPipelineCapabilities(
                directives.computeDirectives().hasComputes(),
                !directives.images().isEmpty(),
                !directives.storageBuffers().isEmpty(),
                !directives.customUniforms().expressions().isEmpty(),
                !directives.textureDirectives().globalTextures().isEmpty()
                        || directives.textureDirectives().programTextures().values().stream().anyMatch(list -> !list.isEmpty())
                        || directives.textureDirectives().rawTextureCount() > 0,
                false
        );
    }

    public ShaderPipelineCapabilities withExtraProgramArrayEntries(boolean value) {
        return new ShaderPipelineCapabilities(compute, images, storageBuffers, customUniforms, customTextures, value);
    }
}
