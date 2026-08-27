package com.luna.ausm.impl.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EuphoriaEntreeLodPatchesTest {
    @Test
    void patchesShadowSamplingWithTieredResolutionAndSmoothExit() {
        String source = "    vec3 GetShadow(vec3 shadowPos, float lightmapY, float offset, int shadowSamples, bool leaves, vec3 playerPos) {\n"
                + "        return shadow;\n"
                + "    }\n"
                + "#endif";

        String patched = EuphoriaEntreeLodPatches.patchShadowSampling(source);

        assertTrue(patched.contains("ausmEntreeLodSampleCount(shadowSamples"));
        assertTrue(patched.contains("mix(vec3(1.0), shadow, ausmShadowFeatureWeight)"));
        assertEquals(patched, EuphoriaEntreeLodPatches.patchShadowSampling(patched));
    }

    @Test
    void patchesWaterAndMaterialReflectionsWithTieredResolution() {
        String source = "            #endif\n\n"
                + "            int sr = 0;\n"
                + "                        lod = max(lod - 1.0, 0.0);\n"
                + "                    reflection.a *= refFactor;\n";

        String patched = EuphoriaEntreeLodPatches.patchReflections(source);

        assertTrue(patched.contains("lod += ausmEntreeReflectionMipBias(dist);"));
        assertTrue(patched.contains("reflection.a *= ausmReflectionFeatureWeight;"));
        assertEquals(patched, EuphoriaEntreeLodPatches.patchReflections(patched));
    }

    @Test
    void terrainPatchStopsFoliageWavingAtTheLodTwoBoundary() {
        String source = "        #ifdef WAVING_ANYTHING_TERRAIN\n"
                + "            DoWave(position.xyz, mat);\n"
                + "        #endif\n"
                + "                DoInteractiveWave(playerPosM, mat);\n";

        String patched = EuphoriaEntreePackGenerator.injectLodFallbackUsage("gbuffers_terrain.glsl", source);

        assertTrue(patched.contains("ausmEntreeFoliageWaveWeight(position.xyz)"));
        assertTrue(!patched.contains("ausmTerrainWaveWeight = ausmEntreeDetailWeight"));
    }

    @Test
    void existingGeneratedTerrainPatchMigratesToFoliageSpecificWeight() {
        String source = "float ausmTerrainWaveWeight = ausmEntreeDetailWeight(position.xyz);\n"
                + "playerPosM = mix(ausmInteractivePosition, playerPosM, ausmEntreeDetailWeight(position.xyz));\n";

        String patched = EuphoriaEntreePackGenerator.injectLodFallbackUsage("gbuffers_terrain.glsl", source);

        assertTrue(patched.contains("ausmTerrainWaveWeight = ausmEntreeFoliageWaveWeight(position.xyz)"));
        assertTrue(patched.contains("playerPosM = mix(ausmInteractivePosition, playerPosM, ausmEntreeFoliageWaveWeight(position.xyz))"));
    }

    @Test
    void helperUsesRuntimeBlockRadiiAndCutsFoliageAtLodTwo() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/assets/ausm/euphoria_entree_overlay/shaders/lib/ausm/distantLod.glsl")) {
            assertTrue(stream != null);
            String helper = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(helper.contains("uniform float ausmLod1RadiusBlocks;"));
            assertTrue(helper.contains("uniform float ausmLod4RadiusBlocks;"));
            assertTrue(helper.contains("length(playerPosition) < ausmLod2RadiusBlocks"));
        }
    }

    @Test
    void generated112PatchReceivesTheStablePixelationLibrary(@TempDir Path shaderpack) throws IOException {
        Path pixelationLibrary = shaderpack.resolve("shaders/lib/misc/pixelation.glsl");
        Files.createDirectories(pixelationLibrary.getParent());
        Files.writeString(pixelationLibrary, "unpatched", StandardCharsets.UTF_8);

        EuphoriaEntreePackGenerator.ensureStablePixelationLibrary(pixelationLibrary);

        String patched = Files.readString(pixelationLibrary, StandardCharsets.UTF_8);
        assertTrue(patched.contains("float determinant ="));
        assertTrue(patched.contains("abs(determinant) <= max(1.0e-8"));
    }

    @Test
    void generates112PatchesForBothComplementaryStylesAndEuphoriaOutputs() {
        assertTrue(EuphoriaEntreePackGenerator.isAUSM112PatchSource("ComplementaryUnbound_r5.8.1.zip"));
        assertTrue(EuphoriaEntreePackGenerator.isAUSM112PatchSource("ComplementaryReimagined_r5.8.1.zip"));
        assertTrue(EuphoriaEntreePackGenerator.isAUSM112PatchSource(
                "ComplementaryReimagined_r5.8.1 + EuphoriaPatches_1.9.3"));
        assertFalse(EuphoriaEntreePackGenerator.isAUSM112PatchSource(
                "ComplementaryReimagined_r5.8.1 + AUSM 1.12.2 Patches"));
    }
}
