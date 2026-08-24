package com.luna.ausm.api.pipeline.shader;

public record ProgramSourceSet(
        ProgramId programId,
        String vertexPath,
        String tessellationControlPath,
        String tessellationEvaluationPath,
        String fragmentPath,
        String geometryPath
) {
    public String programName() {
        return programId.sourceName();
    }
}
