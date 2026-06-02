package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import java.util.Arrays;
import java.util.List;

/**
 * Adapter between Iris-style indexed fullscreen program arrays and the current
 * fixed 1.12 render-pass slots.
 */
public record FullscreenProgramArray(
        ProgramArrayId arrayId,
        List<RenderPass> fixedPasses,
        int declaredProgramCount
) {
    public boolean hasExtraPrograms() {
        return declaredProgramCount > fixedPasses.size();
    }

    public static FullscreenProgramArray fromProgramSet(ProgramArrayId arrayId, ShaderProgramSet programSet) {
        List<RenderPass> fixedPasses = switch (arrayId) {
            case PREPARE -> List.of(RenderPass.PREPARE);
            case DEFERRED -> Arrays.asList(RenderPass.DEFERRED_PASSES);
            case COMPOSITE -> Arrays.asList(RenderPass.COMPOSITE_PASSES);
            case SETUP, BEGIN, SHADOWCOMP -> List.of();
        };

        int declaredProgramCount = 0;
        if (programSet != null) {
            declaredProgramCount = (int) programSet.programArray(arrayId).stream()
                    .filter(ShaderProgramSource::hasAnyStage)
                    .count();
        }
        return new FullscreenProgramArray(arrayId, List.copyOf(fixedPasses), declaredProgramCount);
    }
}
