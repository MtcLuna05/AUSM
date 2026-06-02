package com.l.ausm.api.pipeline.pack;

public record ShaderRawTextureDirective(
        String samplerName,
        String replacementSamplerName,
        ShaderImageTarget target,
        String resourcePath,
        String internalFormat,
        int width,
        int height,
        int depth,
        String pixelFormat,
        String pixelType
) {
}
