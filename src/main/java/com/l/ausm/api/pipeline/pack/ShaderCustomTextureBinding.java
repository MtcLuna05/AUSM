package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

public record ShaderCustomTextureBinding(
        String samplerName,
        String resourcePath
) {
}
