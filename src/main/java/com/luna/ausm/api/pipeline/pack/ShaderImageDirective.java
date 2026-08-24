package com.luna.ausm.api.pipeline.pack;

public record ShaderImageDirective(
        String name,
        String samplerName,
        ShaderImageTarget target,
        String format,
        String internalFormat,
        String pixelType,
        boolean clear,
        boolean relative,
        int width,
        int height,
        int depth,
        float relativeWidth,
        float relativeHeight
) {
}
