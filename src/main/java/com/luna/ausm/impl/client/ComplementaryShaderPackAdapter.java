package com.luna.ausm.impl.client;

import java.io.IOException;
import java.nio.file.Files;

/** Complementary Unbound/Reimagined and Euphoria connection points. */
final class ComplementaryShaderPackAdapter implements ShaderPackFamilyAdapter {
    static final ComplementaryShaderPackAdapter INSTANCE = new ComplementaryShaderPackAdapter();

    private ComplementaryShaderPackAdapter() {
    }

    @Override
    public String familyId() {
        return "complementary";
    }

    @Override
    public boolean supportsSource(String sourcePackName) {
        return sourcePackName != null
                && (sourcePackName.startsWith("ComplementaryUnbound")
                || sourcePackName.startsWith("ComplementaryReimagined")
                || sourcePackName.startsWith("Complimentary Entree + EuphoriaPatches_"));
    }

    @Override
    public boolean recognizes(ShaderPackPatchContext context) throws IOException {
        if (!context.hasShader("program/gbuffers_terrain.glsl")
                || !context.hasShader("program/gbuffers_skytextured.glsl")) {
            return false;
        }
        String terrain = Files.readString(context.shader("program/gbuffers_terrain.glsl"));
        return terrain.contains("Complementary Shaders by EminGT");
    }

    @Override
    public void applyVisualPatches(ShaderPackPatchContext context) throws IOException {
        EuphoriaEntreePackGenerator.installBundledLuminousBlockMappings(context.packRoot());
        EuphoriaEntreePackGenerator.ensureAUSMOptionsCategory(
                context.shader("shaders.properties"), context.shader("lang/en_US.lang"));
        AstralConstellationPatch.inject(context.packRoot());
        EntreeCompatibilityPatches.inject(context.packRoot());
        EuphoriaEntreePackGenerator.installBundledLegacyBlockMappings(context.packRoot());
        ComplementaryGlintPatch.inject(context.packRoot(), context.sourcePackName());
    }

    @Override
    public void applyOptimizationPatches(ShaderPackPatchContext context) throws IOException {
        EuphoriaEntreePackGenerator.ensureLodApiDeclaration(context.shader("shaders.properties"));
        EuphoriaEntreePackGenerator.ensureBundledLodHelper(context.shader("lib/ausm/distantLod.glsl"));
        EuphoriaEntreePackGenerator.ensureStablePixelationLibrary(context.shader("lib/misc/pixelation.glsl"));
        EuphoriaEntreePackGenerator.ensureDriverSafeFxaaLibrary(context.shader("lib/antialiasing/fxaa.glsl"));
        EuphoriaEntreePackGenerator.ensureClampedVolumetricLight(
                context.shader("lib/atmospherics/volumetricLight/volumetricLight.glsl"));
        EuphoriaEntreeLodPatches.inject(context.packRoot());
        EuphoriaEntreePackGenerator.injectLodHelperIntoProgram(context.shader("program/gbuffers_terrain.glsl"));
        EuphoriaEntreePackGenerator.injectLodHelperIntoProgram(context.shader("program/gbuffers_water.glsl"));
    }
}
