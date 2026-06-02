package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderPackLayout;
import net.minecraft.client.Minecraft;

public final class ProgramSourceResolver {

    private ProgramSourceResolver() {
    }

    public static ProgramSourceSet resolve(ShaderPack pack, RenderPass pass) {
        return resolve(pack, pass.programId());
    }

    public static ProgramSourceSet resolve(ShaderPack pack, ProgramId programId) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        int dimensionId = currentDimensionId();
        if (hasAnyDimensionPrograms(pack, layout) && !hasDimensionPrograms(pack, layout, dimensionId)) {
            return new ProgramSourceSet(programId, null, null, null);
        }

        return new ProgramSourceSet(
                programId,
                resolveStage(pack, layout, dimensionId, programId, ".vsh"),
                resolveStage(pack, layout, dimensionId, programId, ".fsh"),
                resolveStage(pack, layout, dimensionId, programId, ".gsh")
        );
    }

    private static String resolveStage(ShaderPack pack, ShaderPackLayout layout, int dimensionId, ProgramId programId, String extension) {
        for (String dimensionBase : layout.dimensionProgramBaseAliases(dimensionId, programId)) {
            String dimensionPath = dimensionBase + extension;
            if (pack.hasResource(dimensionPath)) {
                return dimensionPath;
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

    private static boolean hasDimensionPrograms(ShaderPack pack, ShaderPackLayout layout, int dimensionId) {
        for (ProgramId programId : ProgramId.values()) {
            for (String base : layout.dimensionProgramBaseAliases(dimensionId, programId)) {
                if (pack.hasResource(base + ".vsh") || pack.hasResource(base + ".fsh") || pack.hasResource(base + ".gsh")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAnyDimensionPrograms(ShaderPack pack, ShaderPackLayout layout) {
        int current = currentDimensionId();
        return hasDimensionPrograms(pack, layout, current)
                || hasDimensionPrograms(pack, layout, -1)
                || hasDimensionPrograms(pack, layout, 0)
                || hasDimensionPrograms(pack, layout, 1);
    }

    private static int currentDimensionId() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.world.provider == null) {
            return 0;
        }
        return mc.world.provider.getDimension();
    }
}
