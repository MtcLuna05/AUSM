package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

public record ShaderScreenEntry(Type type, String name) {
    public enum Type {
        OPTION,
        SCREEN,
        PROFILE,
        EMPTY
    }
}
