package com.luna.ausm.impl.pipeline.pack;

@FunctionalInterface
public interface ShaderTransformStage {
    String apply(String source, ShaderTransformParameters parameters);
}
