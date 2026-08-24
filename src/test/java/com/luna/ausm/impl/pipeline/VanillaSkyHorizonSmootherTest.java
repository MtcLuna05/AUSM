package com.luna.ausm.impl.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VanillaSkyHorizonSmootherTest {
    @Test
    void ignoresNormalContinuousSkyColours() {
        assertFalse(VanillaSkyHorizonSmoother.hasVisibleColorJump(
                new float[]{0.60F, 0.70F, 0.90F, 1.0F},
                new float[]{0.62F, 0.71F, 0.89F, 1.0F}
        ));
    }

    @Test
    void repairsAVisibleOpaqueDomeMismatch() {
        assertTrue(VanillaSkyHorizonSmoother.hasVisibleColorJump(
                new float[]{0.60F, 0.70F, 0.90F, 1.0F},
                new float[]{0.25F, 0.35F, 0.55F, 1.0F}
        ));
    }

    @Test
    void matchesTheLowerDomeToTheUpperColourWhenTheSeamIsVisible() {
        float[] reconciled = VanillaSkyHorizonSmoother.reconciledLowerDomeColor(
                new float[]{0.60F, 0.70F, 0.90F, 1.0F},
                new float[]{0.25F, 0.35F, 0.55F, 1.0F}
        );

        assertArrayEquals(new float[]{0.60F, 0.70F, 0.90F, 1.0F}, reconciled);
    }

    @Test
    void repairsTheSubtleHorizonMismatchVisibleInVanillaSky() {
        assertTrue(VanillaSkyHorizonSmoother.hasVisibleColorJump(
                new float[]{0.60F, 0.70F, 0.90F, 1.0F},
                new float[]{0.57F, 0.68F, 0.88F, 1.0F}
        ));
    }

    @Test
    void doesNotBridgeTranslucentSkyState() {
        assertFalse(VanillaSkyHorizonSmoother.hasVisibleColorJump(
                new float[]{0.60F, 0.70F, 0.90F, 0.5F},
                new float[]{0.25F, 0.35F, 0.55F, 1.0F}
        ));
    }
}
