package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

public final class FragmentOutputTransformStage implements ShaderTransformStage {
    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader()) {
            return source;
        }
        return source.replaceAll("\\bgl_FragColor\\b", "gl_FragData[0]");
    }
}
