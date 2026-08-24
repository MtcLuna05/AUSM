package com.luna.ausm.impl.pipeline.pack;

public final class FragmentOutputTransformStage implements ShaderTransformStage {
    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader()) {
            return source;
        }
        return source.replaceAll("\\bgl_FragColor\\b", "gl_FragData[0]");
    }
}
