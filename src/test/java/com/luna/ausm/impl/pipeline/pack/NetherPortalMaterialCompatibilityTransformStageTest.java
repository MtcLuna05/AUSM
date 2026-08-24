package com.luna.ausm.impl.pipeline.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NetherPortalMaterialCompatibilityTransformStageTest {
    @Test
    void makesNestedPortalBranchReachableWithoutClassifyingItAsWater() {
        String source = """
                if (ausmIsModdedColoredFluid) {
                    coloredFluid();
                } else if (mat >= 32000) {
                    if (mat < 32004) {
                        water();
                    } else if (mat == 30020) {
                        netherPortal();
                    }
                }
                """;

        String repaired = NetherPortalMaterialCompatibilityTransformStage.repairMaterialRoute(source);

        assertTrue(repaired.contains("else if (mat == 30020 || mat >= 32000)"));
        assertTrue(repaired.contains("if (mat >= 32000 && mat < 32004)"));
        assertTrue(repaired.contains("else if (mat == 30020)"));
        assertTrue(repaired.contains(NetherPortalMaterialCompatibilityTransformStage.MARKER));
        assertEquals(repaired, NetherPortalMaterialCompatibilityTransformStage.repairMaterialRoute(repaired));
    }

    @Test
    void leavesUnrelatedWaterTreesAlone() {
        String source = "if (mat >= 32000) { if (mat < 32004) water(); }";
        assertEquals(source, NetherPortalMaterialCompatibilityTransformStage.repairMaterialRoute(source));
    }
}
