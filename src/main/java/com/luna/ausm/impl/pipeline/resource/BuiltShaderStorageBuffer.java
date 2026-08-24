package com.luna.ausm.impl.pipeline.resource;

import com.luna.ausm.api.pipeline.pack.ShaderStorageBufferDirective;

public record BuiltShaderStorageBuffer(
        int index,
        long size,
        boolean relative,
        float scaleX,
        float scaleY,
        byte[] initialContent
) {
    public static BuiltShaderStorageBuffer from(ShaderStorageBufferDirective directive, byte[] initialContent) {
        return new BuiltShaderStorageBuffer(
                directive.index(),
                directive.size(),
                directive.relative(),
                directive.scaleX(),
                directive.scaleY(),
                initialContent == null ? null : initialContent.clone()
        );
    }
}
