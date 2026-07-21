package com.l.ausm.impl.pipeline.compat;

import org.lwjgl.opengl.GL11;

/** Preserves the caller's fog state around shaderless Nothirium terrain draws. */
public final class ShaderlessNothiriumFogGuard {
    private int depth;
    private boolean fogPreviouslyEnabled;

    public void begin(boolean shouldDisableFog) {
        if (!shouldDisableFog) {
            return;
        }

        if (depth++ == 0) {
            fogPreviouslyEnabled = GL11.glIsEnabled(GL11.GL_FOG);
            if (fogPreviouslyEnabled) {
                GL11.glDisable(GL11.GL_FOG);
            }
        }
    }

    public void end() {
        if (depth <= 0) {
            return;
        }

        if (--depth == 0) {
            if (fogPreviouslyEnabled) {
                GL11.glEnable(GL11.GL_FOG);
            }
            fogPreviouslyEnabled = false;
        }
    }
}
