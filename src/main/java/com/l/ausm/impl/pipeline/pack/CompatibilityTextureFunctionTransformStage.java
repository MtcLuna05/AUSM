package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

public final class CompatibilityTextureFunctionTransformStage implements ShaderTransformStage {
    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.compatibilityProfile()) {
            return source;
        }
        return source
                .replaceAll("\\btextureLod\\s*\\(", "texture2DLod(")
                .replaceAll("\\btexture\\s*\\(", "texture2D(");
    }
}
