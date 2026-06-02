package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

import java.util.List;

public record ShaderScreen(String id, List<ShaderScreenEntry> entries) {
}
