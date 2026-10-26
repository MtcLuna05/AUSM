package com.luna.ausm.impl.client;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

/** MakeUp Ultra Fast 9.x connection points for AUSM's reusable 1.12.2 patch layer. */
final class MakeupUltraFastShaderPackAdapter implements ShaderPackFamilyAdapter {
    static final MakeupUltraFastShaderPackAdapter INSTANCE = new MakeupUltraFastShaderPackAdapter();

    private MakeupUltraFastShaderPackAdapter() {
    }

    @Override
    public String familyId() {
        return "makeup-ultrafast";
    }

    @Override
    public boolean supportsSource(String sourcePackName) {
        if (sourcePackName == null) return false;
        String normalized = sourcePackName.toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "");
        return normalized.startsWith("makeupultrafast");
    }

    @Override
    public boolean recognizes(ShaderPackPatchContext context) throws IOException {
        if (!context.hasShader("common/skytextured_vertex.glsl")
                || !context.hasShader("common/skytextured_fragment.glsl")
                || !context.hasShader("common/solid_blocks_vertex.glsl")
                || !context.hasShader("lib/config.glsl")) {
            return false;
        }
        String vertex = Files.readString(context.shader("gbuffers_skytextured.vsh"));
        String fragment = Files.readString(context.shader("gbuffers_skytextured.fsh"));
        return vertex.contains("MakeUp - gbuffers_skytextured.vsh")
                && fragment.contains("MakeUp - gbuffers_skytextured.fsh");
    }

    @Override
    public void applyVisualPatches(ShaderPackPatchContext context) throws IOException {
        MakeupUltraFast112CompatibilityPatches.inject(context.packRoot());
        MakeupUltraFastAstralConstellationPatch.inject(context.packRoot());
    }

    @Override
    public void applyOptimizationPatches(ShaderPackPatchContext context) throws IOException {
        EuphoriaEntreePackGenerator.ensureLodApiDeclaration(context.shader("shaders.properties"));
        MakeupUltraFast112CompatibilityPatches.injectWavingDistanceLimit(context.packRoot());
    }
}
