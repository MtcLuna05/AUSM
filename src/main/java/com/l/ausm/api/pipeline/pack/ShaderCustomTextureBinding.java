package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

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
