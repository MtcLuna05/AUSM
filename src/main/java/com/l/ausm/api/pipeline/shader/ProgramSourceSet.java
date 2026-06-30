package com.l.ausm.api.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

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
