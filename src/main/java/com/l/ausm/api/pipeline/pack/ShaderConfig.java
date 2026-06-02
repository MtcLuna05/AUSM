package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;

import java.util.List;
import java.util.Map;

/**
 * Represents the configuration parsed from a shader pack.
 * Usually populated by scanning comments in the GLSL files.
 */
public record ShaderConfig(
    Map<Integer, String> bufferFormats,
    Map<Integer, Boolean> bufferClearFlags,
    List<Integer> drawBuffers
) {
    // We can add more fields as we implement features (e.g., centerDepthSmooth, wetnessHalfLife)
}
