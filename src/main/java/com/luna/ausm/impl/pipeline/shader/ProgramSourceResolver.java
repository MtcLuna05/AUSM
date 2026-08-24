package com.luna.ausm.impl.pipeline.shader;

import com.luna.ausm.api.pipeline.shader.ProgramId;
import com.luna.ausm.api.pipeline.shader.ProgramSourceSet;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.impl.pipeline.pack.ShaderDimensionContext;
import com.luna.ausm.impl.pipeline.pack.ShaderPack;
import com.luna.ausm.impl.pipeline.pack.ShaderPackLayout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
                resolveStage(pack, layout, dimensionId, programId, ".tcs"),
                resolveStage(pack, layout, dimensionId, programId, ".tes"),
                resolveFragmentStage(pack, layout, dimensionId, programId),
                resolveStage(pack, layout, dimensionId, programId, ".gsh")
        );
    }

    private static String resolveFragmentStage(ShaderPack pack, ShaderPackLayout layout, int dimensionId, ProgramId programId) {
        String fragmentPath = resolveStage(pack, layout, dimensionId, programId, ".fsh");
        if (fragmentPath != null) {
            return fragmentPath;
        }
        String glslPath = resolveStage(pack, layout, dimensionId, programId, ".glsl");
        return glslPath != null && !isStageGuardedSource(pack, glslPath) ? glslPath : null;
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

    private static boolean isStageGuardedSource(ShaderPack pack, String path) {
        String source = readKnownExisting(pack, path);
        return source != null
                && (source.contains("#ifdef FRAGMENT_SHADER")
                || source.contains("#ifdef VERTEX_SHADER")
                || source.contains("#if defined FRAGMENT_SHADER")
                || source.contains("#if defined VERTEX_SHADER")
                || source.contains("#if defined(FRAGMENT_SHADER)")
                || source.contains("#if defined(VERTEX_SHADER)"));
    }

    private static String readKnownExisting(ShaderPack pack, String path) {
        if (path == null) {
            return null;
        }
        try (InputStream stream = pack.getResourceAsStream(path)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return null;
        }
    }

}
