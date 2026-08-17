package com.l.ausm.impl.pipeline.pack;

@FunctionalInterface
public interface ShaderTransformStage {
    String apply(String source, ShaderTransformParameters parameters);
}
