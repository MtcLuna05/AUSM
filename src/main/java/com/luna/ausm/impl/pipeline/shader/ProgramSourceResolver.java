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
        return resolve(pack, programId, ShaderDimensionContext.currentDimensionId());
    }

    static ProgramSourceSet resolve(ShaderPack pack, ProgramId programId, int dimensionId) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);

        String vertexPath = resolveStage(pack, layout, dimensionId, programId, ".vsh");
        String fragmentPath = resolveStage(pack, layout, dimensionId, programId, ".fsh");
        String glslPath = resolveStage(pack, layout, dimensionId, programId, ".glsl");
        if (glslPath != null && isReservedUnusedProgram(pack, glslPath)) {
            // Some packs ship placeholder program/*.glsl files without a
            // corresponding worldN entrypoint. Complementary uses this for
            // composite2; compiling the placeholder creates a noisy failed
            // program and can leave the pass incorrectly considered active.
            glslPath = null;
        }
        if (glslPath != null && isStageGuardedSource(pack, glslPath)) {
            if (vertexPath == null) {
                vertexPath = glslPath;
            }
            if (fragmentPath == null) {
                fragmentPath = glslPath;
            }
        } else if (fragmentPath == null) {
            fragmentPath = glslPath;
        }

        return new ProgramSourceSet(
                programId,
                vertexPath,
                resolveStage(pack, layout, dimensionId, programId, ".tcs"),
                resolveStage(pack, layout, dimensionId, programId, ".tes"),
                fragmentPath,
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

    private static boolean isReservedUnusedProgram(ShaderPack pack, String path) {
        if (path == null || !path.endsWith("composite2.glsl")) {
            return false;
        }
        String source = readKnownExisting(pack, path);
        return source != null && source.contains("reserved for future use")
                && source.contains("completely unused");
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
