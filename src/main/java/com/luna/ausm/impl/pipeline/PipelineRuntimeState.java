package com.luna.ausm.impl.pipeline;

/**
 * The central hub for the active render pipeline.
 * Replaces the monolithic Shaders class with a cleaner context object.
 */
abstract class PipelineRuntimeState extends PipelineRuntimeDiagnosticsState9 {
    protected PipelineRuntimeState() {
        registerBaseUniforms();
    }
}
