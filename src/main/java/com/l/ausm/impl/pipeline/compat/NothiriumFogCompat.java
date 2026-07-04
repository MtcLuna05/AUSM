package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import meldexun.nothirium.mc.util.FogUtil;
import meldexun.renderlib.util.GLShader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public final class NothiriumFogCompat {
    private NothiriumFogCompat() {
    }

    public static void setupFogFromGL(GLShader shader, String source) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.shouldSanitizeShaderlessNothiriumFog()) {
            context.logNothiriumFogProbe(
                    source + "-keep-disabled",
                    GL11.glIsEnabled(GL11.GL_FOG),
                    GL11.glGetInteger(GL11.GL_FOG_MODE),
                    GL11.glGetFloat(GL11.GL_FOG_START),
                    GL11.glGetFloat(GL11.GL_FOG_END),
                    GL11.glGetFloat(GL11.GL_FOG_DENSITY),
                    fogColor(),
                    fogColor()
            );
            FogUtil.setupFogFromGL(shader);
            return;
        }

        boolean fogEnabled = GL11.glIsEnabled(GL11.GL_FOG);
        int fogMode = GL11.glGetInteger(GL11.GL_FOG_MODE);
        float fogStart = GL11.glGetFloat(GL11.GL_FOG_START);
        float fogEnd = GL11.glGetFloat(GL11.GL_FOG_END);
        float fogDensity = GL11.glGetFloat(GL11.GL_FOG_DENSITY);
        float[] originalColor = fogColor();
        float[] adjustedColor = originalColor;
        boolean adjusted = false;
        if (fogEnabled && originalColor.length >= 4 && originalColor[3] != 0.0F) {
            adjustedColor = new float[]{originalColor[0], originalColor[1], originalColor[2], 0.0F};
            setFogColor(adjustedColor);
            adjusted = true;
        }
        context.logNothiriumFogProbe(
                source + (adjusted ? "-head-adjust" : "-head-keep"),
                fogEnabled,
                fogMode,
                fogStart,
                fogEnd,
                fogDensity,
                originalColor,
                adjustedColor
        );
        try {
            FogUtil.setupFogFromGL(shader);
        } finally {
            if (adjusted) {
                setFogColor(originalColor);
                context.logNothiriumFogProbe(
                        source + "-return-restore",
                        GL11.glIsEnabled(GL11.GL_FOG),
                        GL11.glGetInteger(GL11.GL_FOG_MODE),
                        GL11.glGetFloat(GL11.GL_FOG_START),
                        GL11.glGetFloat(GL11.GL_FOG_END),
                        GL11.glGetFloat(GL11.GL_FOG_DENSITY),
                        fogColor(),
                        originalColor
                );
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
