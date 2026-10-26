package com.luna.ausm.impl.client;

import java.io.IOException;

/** Family-specific connection points for the reusable AUSM 1.12.2 patch layer. */
interface ShaderPackFamilyAdapter {
    String familyId();

    boolean supportsSource(String sourcePackName);

    boolean recognizes(ShaderPackPatchContext context) throws IOException;

    void applyVisualPatches(ShaderPackPatchContext context) throws IOException;

    void applyOptimizationPatches(ShaderPackPatchContext context) throws IOException;
}
