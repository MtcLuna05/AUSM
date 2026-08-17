package com.l.ausm.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OptionalMixinTargetsTest {
    @Test
    void resolvesAnOptionalMixinBySimpleName() {
        OptionalMixinTarget target = OptionalMixinTargets.find(
                "com.l.ausm.impl.mixin.compat.NothiriumRenderChunkTaskCompileMixin");

        assertEquals("meldexun/nothirium/mc/renderer/chunk/RenderChunkTaskCompile.class",
                target.resourcePath());
        assertTrue(target.allowJarFallback());
    }

    @Test
    void preservesTargetsThatMustNotScanTheModsDirectory() {
        OptionalMixinTarget target = OptionalMixinTargets.find(
                "com.l.ausm.impl.mixin.compat.EuphoriaPatcherEntreeMixin");

        assertFalse(target.allowJarFallback());
    }

    @Test
    void leavesNonOptionalMixinsUnrestricted() {
        assertNull(OptionalMixinTargets.find("com.l.ausm.impl.mixin.pipeline.EntityRendererMixin"));
        assertNull(OptionalMixinTargets.find(null));
    }
}
