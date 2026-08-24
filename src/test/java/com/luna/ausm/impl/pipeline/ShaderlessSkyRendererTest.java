package com.luna.ausm.impl.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderlessSkyRendererTest {
    @Test
    void ownsVanillaEquivalentSkyOnlyWhenTheShaderPipelineIsInactive() {
        assertTrue(ShaderlessSkyOwnership.shouldReplaceVanillaSky(false));
        assertFalse(ShaderlessSkyOwnership.shouldReplaceVanillaSky(true));
    }
}
