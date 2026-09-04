package com.luna.ausm.impl.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class ShaderlessSkyRendererTest {
    @Test
    void leavesShaderlessSkyGeometryToTheNativeRenderer() {
        assertFalse(ShaderlessSkyOwnership.shouldReplaceVanillaSky(false));
        assertFalse(ShaderlessSkyOwnership.shouldReplaceVanillaSky(true));
    }
}
