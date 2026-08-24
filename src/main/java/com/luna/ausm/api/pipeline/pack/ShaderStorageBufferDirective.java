package com.luna.ausm.api.pipeline.pack;

public record ShaderStorageBufferDirective(
        int index,
        long size,
        boolean relative,
        float scaleX,
        float scaleY,
        String name
) {
}
