package com.luna.ausm.api.pipeline.shader;

import com.luna.ausm.api.pipeline.pack.ShaderProgramDirectives;
import java.util.Optional;

/**
 * Iris-style shaderpack program source object.
 *
 * <p>The source strings are the pack's raw stage contents. Runtime compilation
 * still runs AUSM's 1.12 preprocessor from the stage path so includes and
 * option overrides keep their existing behavior while the loader moves toward
 * Iris' source/directive ownership model.</p>
 */
public record ShaderProgramSource(
        ProgramId programId,
        String sourceName,
        String vertexPath,
        String vertexSource,
        String tessellationControlPath,
        String tessellationControlSource,
        String tessellationEvaluationPath,
        String tessellationEvaluationSource,
        String geometryPath,
        String geometrySource,
        String fragmentPath,
        String fragmentSource,
        ShaderProgramDirectives directives
) {
    public String name() {
        return sourceName != null ? sourceName : programId.sourceName();
    }

    public Optional<String> vertexSourceOptional() {
        return Optional.ofNullable(vertexSource);
    }

    public Optional<String> tessellationControlSourceOptional() {
        return Optional.ofNullable(tessellationControlSource);
    }

    public Optional<String> tessellationEvaluationSourceOptional() {
        return Optional.ofNullable(tessellationEvaluationSource);
    }

    public Optional<String> geometrySourceOptional() {
        return Optional.ofNullable(geometrySource);
    }

    public Optional<String> fragmentSourceOptional() {
        return Optional.ofNullable(fragmentSource);
    }

    public boolean hasAnyStage() {
        return vertexPath != null
                || tessellationControlPath != null
                || tessellationEvaluationPath != null
                || geometryPath != null
                || fragmentPath != null;
    }

    public boolean isValid() {
        return vertexPath != null && fragmentPath != null;
    }
}
