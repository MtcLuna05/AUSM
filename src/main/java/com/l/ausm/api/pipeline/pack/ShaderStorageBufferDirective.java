package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

public record ShaderStorageBufferDirective(
        int index,
        long size,
        boolean relative,
        float scaleX,
        float scaleY,
        String name
) {
}
