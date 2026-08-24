package com.luna.ausm.impl.pipeline.compat;

/**
 * Optional zero-reflection bridge mixed into Nothirium render chunks.
 * Kept free of Nothirium types so the shadow renderer remains loadable when
 * Nothirium is not installed.
 */
public interface NothiriumShadowChunkAccess {

    int ausm$blockX();

    int ausm$blockY();

    int ausm$blockZ();

    Object ausm$vboPart(Object pass);

    Object ausm$lastCompileTaskResult();

    boolean ausm$isDirty();
}
