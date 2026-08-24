package com.luna.ausm.impl.pipeline.pack;

public record ShaderPipelineCapabilities(
        boolean compute,
        boolean images,
        boolean storageBuffers,
        boolean customUniforms,
        boolean customTextures,
        boolean perBufferBlending,
        boolean geometry,
        boolean tessellation,
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
                        || directives.textureDirectives().programArrayTextures().values().stream().anyMatch(list -> !list.isEmpty())
                        || directives.textureDirectives().rawTextureCount() > 0,
                directives.programDirectives().values().stream().anyMatch(directivesForProgram -> !directivesForProgram.attachmentBlendModes().isEmpty()),
                false,
                false,
                false
        );
    }

    public ShaderPipelineCapabilities withExtraProgramArrayEntries(boolean value) {
        return new ShaderPipelineCapabilities(compute, images, storageBuffers, customUniforms, customTextures, perBufferBlending, geometry, tessellation, value);
    }

    public ShaderPipelineCapabilities withGeometry(boolean value) {
        return new ShaderPipelineCapabilities(compute, images, storageBuffers, customUniforms, customTextures, perBufferBlending, value, tessellation, extraProgramArrayEntries);
    }

    public ShaderPipelineCapabilities withTessellation(boolean value) {
        return new ShaderPipelineCapabilities(compute, images, storageBuffers, customUniforms, customTextures, perBufferBlending, geometry, value, extraProgramArrayEntries);
    }
}
