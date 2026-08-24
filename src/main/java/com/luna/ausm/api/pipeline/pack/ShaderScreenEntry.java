package com.luna.ausm.api.pipeline.pack;

public record ShaderScreenEntry(Type type, String name) {
    public enum Type {
        OPTION,
        SCREEN,
        PROFILE,
        EMPTY
    }
}
