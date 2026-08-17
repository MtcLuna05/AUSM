package com.l.ausm.impl.pipeline.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ShaderEnvironmentDefinesTest {
    @Test
    void advertisesTheDepthRangeActuallyUsedForFirstPersonGeometry() {
        assertEquals("0.125", ShaderEnvironmentDefines.handDepthDefine());
    }
}
