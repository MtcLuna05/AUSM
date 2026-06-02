package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

@FunctionalInterface
public interface ShaderTransformStage {
    String apply(String source, ShaderTransformParameters parameters);
}
