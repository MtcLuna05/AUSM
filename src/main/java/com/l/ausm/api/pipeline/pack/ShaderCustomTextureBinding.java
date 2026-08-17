package com.l.ausm.api.pipeline.pack;

public record ShaderCustomTextureBinding(
        String samplerName,
        String resourcePath,
        boolean blur,
        boolean clamp
) {
    public ShaderCustomTextureBinding(String samplerName, String resourcePath) {
        this(samplerName, resourcePath, false, false);
    }
}
