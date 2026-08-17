package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "meldexun.nothirium.mc.util.FogUtil", remap = false)
public class NothiriumFogUtilMixin {
    @Unique
    private static float[] ausm$originalFogColor;

    @Unique
    private static boolean ausm$adjustedFogColor;

    @Inject(method = "setupFogFromGL", at = @At("HEAD"), remap = false)
    private static void ausm$beforeSetupFogFromGl(@Coerce Object shader, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.shouldSanitizeShaderlessNothiriumFog()) {
            return;
        }

        boolean fogEnabled = GL11.glIsEnabled(GL11.GL_FOG);
        int fogMode = GL11.glGetInteger(GL11.GL_FOG_MODE);
        float fogStart = GL11.glGetFloat(GL11.GL_FOG_START);
        float fogEnd = GL11.glGetFloat(GL11.GL_FOG_END);
        float fogDensity = GL11.glGetFloat(GL11.GL_FOG_DENSITY);
        float[] color = ausm$getFogColor();
        ausm$originalFogColor = color;
        ausm$adjustedFogColor = false;

        if (fogEnabled && color != null && color.length >= 4 && color[3] != 0.0F) {
            float[] adjusted = new float[]{color[0], color[1], color[2], 0.0F};
            ausm$setFogColor(adjusted);
            ausm$adjustedFogColor = true;
            context.logNothiriumFogProbe("head-adjust", fogEnabled, fogMode, fogStart, fogEnd, fogDensity, color, adjusted);
            return;
        }

        context.logNothiriumFogProbe("head-keep", fogEnabled, fogMode, fogStart, fogEnd, fogDensity, color, color);
    }

    @Inject(method = "setupFogFromGL", at = @At("RETURN"), remap = false)
    private static void ausm$afterSetupFogFromGl(@Coerce Object shader, CallbackInfo ci) {
        if (ausm$adjustedFogColor && ausm$originalFogColor != null) {
            ausm$setFogColor(ausm$originalFogColor);
            PipelineContext.getInstance().logNothiriumFogProbe(
                    "return-restore",
                    GL11.glIsEnabled(GL11.GL_FOG),
                    GL11.glGetInteger(GL11.GL_FOG_MODE),
                    GL11.glGetFloat(GL11.GL_FOG_START),
                    GL11.glGetFloat(GL11.GL_FOG_END),
                    GL11.glGetFloat(GL11.GL_FOG_DENSITY),
                    ausm$getFogColor(),
                    ausm$originalFogColor
            );
        }
        ausm$originalFogColor = null;
        ausm$adjustedFogColor = false;
    }

    @Unique
    private static float[] ausm$getFogColor() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_FOG_COLOR, buffer);
        return new float[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
    }

    @Unique
    private static void ausm$setFogColor(float[] color) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
        buffer.put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.flip();
        GL11.glFog(GL11.GL_FOG_COLOR, buffer);
    }
}
