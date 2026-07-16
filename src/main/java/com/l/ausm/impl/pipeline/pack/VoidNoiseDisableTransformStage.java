package com.l.ausm.impl.pipeline.pack;

public final class VoidNoiseDisableTransformStage implements ShaderTransformStage {
    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        // Custom Void sky features are supported by the active pack. Preserve
        // its feature defines instead of silently disabling them at compile time.
        return source
                .replace("starCoverage * 0.70 * skyParams.y", "starCoverage * 0.92 * skyParams.y")
                .replace("(0.70 + 2.80 * starCoverage)", "(0.95 + 3.85 * starCoverage)");
    }
}
