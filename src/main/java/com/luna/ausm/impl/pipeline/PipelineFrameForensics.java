package com.luna.ausm.impl.pipeline;

import java.nio.FloatBuffer;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Read-only numerical diagnostics for a deliberately requested frame capture.
 *
 * <p>PNG output cannot reveal HDR overflow, negative values, NaNs, or alpha
 * contamination. This class reads the live texture as floats and reduces it to
 * a compact report while preserving the caller's OpenGL bindings.</p>
 */
final class PipelineFrameForensics {
    private PipelineFrameForensics() {
    }

    static String describeTexture(String label, int texture, int width, int height) {
        if (texture <= 0 || width <= 0 || height <= 0) {
            return label + " texture=" + texture + " size=" + width + "x" + height + " unavailable";
        }
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            FloatBuffer pixels = BufferUtils.createFloatBuffer(Math.multiplyExact(Math.multiplyExact(width, height), 4));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            return summarize(label, texture, width, height, pixels);
        } catch (RuntimeException | LinkageError e) {
            return label + " texture=" + texture + " size=" + width + "x" + height + " read-failed=" + e.getClass().getSimpleName();
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    static String describeFramebuffer(String label, Framebuffer framebuffer) {
        if (framebuffer == null) {
            return label + " framebuffer=null";
        }
        return describeTexture(label, framebuffer.framebufferTexture, framebuffer.framebufferWidth, framebuffer.framebufferHeight)
                + " fbo=" + framebuffer.framebufferObject;
    }

    private static String summarize(String label, int texture, int width, int height, FloatBuffer pixels) {
        double luminanceSum = 0.0D;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        int invalid = 0;
        int negative = 0;
        int overbright = 0;
        int nearBlack = 0;
        int alphaZero = 0;
        int alphaPartial = 0;
        int pixelsCount = width * height;
        for (int pixel = 0; pixel < pixelsCount; pixel++) {
            float red = pixels.get();
            float green = pixels.get();
            float blue = pixels.get();
            float alpha = pixels.get();
            if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue) || !Float.isFinite(alpha)) {
                invalid++;
                continue;
            }
            float peak = Math.max(red, Math.max(green, blue));
            float floor = Math.min(red, Math.min(green, blue));
            min = Math.min(min, floor);
            max = Math.max(max, peak);
            double luminance = red * 0.2126D + green * 0.7152D + blue * 0.0722D;
            luminanceSum += luminance;
            if (floor < 0.0F) {
                negative++;
            }
            if (peak > 1.0F) {
                overbright++;
            }
            if (luminance <= 0.002D) {
                nearBlack++;
            }
            if (alpha <= 0.001F) {
                alphaZero++;
            } else if (alpha < 0.999F) {
                alphaPartial++;
            }
        }
        int valid = pixelsCount - invalid;
        return String.format(
                "%s texture=%d size=%dx%d valid=%d invalid=%d rgb[min=%.5f,max=%.5f,avgLuma=%.5f,neg=%d,over1=%d,black=%d] alpha[zero=%d,partial=%d]",
                label, texture, width, height, valid, invalid,
                valid == 0 ? Float.NaN : min,
                valid == 0 ? Float.NaN : max,
                valid == 0 ? Double.NaN : luminanceSum / valid,
                negative, overbright, nearBlack, alphaZero, alphaPartial
        );
    }
}
