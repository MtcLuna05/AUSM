package com.l.ausm.impl.pipeline.compat;

/**
 * Draws Nothirium's prepared chunk VBOs with AUSM's active shader.
 * <p>
 * Nothirium owns the normal terrain visibility lists. Calling its setup from
 * the shadow camera path corrupts that state, while calling its render method
 * binds Nothirium's own shader and normal camera matrix. This bridge only reads
 * the already prepared lists and emits vanilla-layout VBO draws.
 */
public final class NothiriumShadowRenderer extends NothiriumShadowVertexSetup {

}
