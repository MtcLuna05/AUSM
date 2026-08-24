package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import java.nio.FloatBuffer;
import meldexun.nothirium.mc.util.FogUtil;
import meldexun.renderlib.util.GLShader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class NothiriumFogCompat {
    private NothiriumFogCompat() {
    }

    public static void setupFogFromGL(GLShader shader, String source) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.shouldSanitizeShaderlessNothiriumFog()) {
            FogUtil.setupFogFromGL(shader);
            return;
        }

        boolean fogEnabled = GL11.glIsEnabled(GL11.GL_FOG);
        float[] originalColor = fogColor();
        float[] adjustedColor = originalColor;
        boolean adjusted = false;
        if (fogEnabled && originalColor.length >= 4 && originalColor[3] != 0.0F) {
            adjustedColor = new float[]{originalColor[0], originalColor[1], originalColor[2], 0.0F};
            setFogColor(adjustedColor);
            adjusted = true;
        }
        try {
            FogUtil.setupFogFromGL(shader);
        } finally {
            if (adjusted) {
                setFogColor(originalColor);
            }
        }
    }

    private static float[] fogColor() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_FOG_COLOR, buffer);
        return new float[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
    }

    private static void setFogColor(float[] color) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
        buffer.put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.flip();
        GL11.glFog(GL11.GL_FOG_COLOR, buffer);
    }
}
