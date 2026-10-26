package com.luna.ausm.impl.client;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

/** BSL v10 connection points for AUSM's reusable 1.12.2 patch layer. */
final class BslShaderPackAdapter implements ShaderPackFamilyAdapter {
    static final BslShaderPackAdapter INSTANCE = new BslShaderPackAdapter();

    private BslShaderPackAdapter() {
    }

    @Override
    public String familyId() {
        return "bsl";
    }

    @Override
    public boolean supportsSource(String sourcePackName) {
        if (sourcePackName == null) return false;
        String normalized = sourcePackName.toLowerCase(Locale.ROOT);
        return normalized.equals("bsl") || normalized.startsWith("bsl_") || normalized.startsWith("bsl ");
    }

    @Override
    public boolean recognizes(ShaderPackPatchContext context) throws IOException {
        if (!context.hasShader("program/gbuffers_terrain.glsl")
                || !context.hasShader("program/gbuffers_skytextured.glsl")
                || !context.hasShader("program/shadowcomp.glsl")) {
            return false;
        }
        String terrain = Files.readString(context.shader("program/gbuffers_terrain.glsl"));
        String sky = Files.readString(context.shader("program/gbuffers_skytextured.glsl"));
        return terrain.contains("BSL Shaders v10 Series by Capt Tatsu")
                && sky.contains("BSL Shaders v10 Series by Capt Tatsu");
    }

    @Override
    public void applyVisualPatches(ShaderPackPatchContext context) throws IOException {
        Bsl112CompatibilityPatches.inject(context.packRoot());
        BslAstralConstellationPatch.inject(context.packRoot());
    }

    @Override
    public void applyOptimizationPatches(ShaderPackPatchContext context) throws IOException {
        EuphoriaEntreePackGenerator.ensureLodApiDeclaration(context.shader("shaders.properties"));
        Bsl112CompatibilityPatches.injectWavingDistanceLimit(context.packRoot());
    }
}
