package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderDimensionContext;
import com.l.ausm.impl.pipeline.pack.ShaderPackLayout;

public final class ProgramSourceResolver {

    private ProgramSourceResolver() {
    }

    public static ProgramSourceSet resolve(ShaderPack pack, RenderPass pass) {
        return resolve(pack, pass.programId());
    }

    public static ProgramSourceSet resolve(ShaderPack pack, ProgramId programId) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        int dimensionId = ShaderDimensionContext.currentDimensionId();

        return new ProgramSourceSet(
                programId,
                resolveStage(pack, layout, dimensionId, programId, ".vsh"),
                resolveStage(pack, layout, dimensionId, programId, ".fsh"),
                resolveStage(pack, layout, dimensionId, programId, ".gsh")
        );
    }

    private static String resolveStage(ShaderPack pack, ShaderPackLayout layout, int dimensionId, ProgramId programId, String extension) {
        for (int candidateDimensionId : dimensionFallbackOrder(dimensionId)) {
            for (String dimensionBase : layout.dimensionProgramBaseAliases(candidateDimensionId, programId)) {
                String dimensionPath = dimensionBase + extension;
                if (pack.hasResource(dimensionPath)) {
                    return dimensionPath;
                }
            }
        }

        for (String rootBase : layout.programBaseAliases(programId)) {
            String rootPath = rootBase + extension;
            if (pack.hasResource(rootPath)) {
                return rootPath;
            }
        }
        return null;
    }

    private static int[] dimensionFallbackOrder(int dimensionId) {
        if (dimensionId == 0) {
            return new int[]{0};
        }
        return new int[]{dimensionId, 0};
    }

}
