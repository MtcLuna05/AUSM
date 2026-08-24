package com.luna.ausm.impl.pipeline;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Harmonizes the lower vanilla sky dome with the upper one only when their
 * active fixed-function colours visibly disagree. This happens before lower
 * geometry submission, so the visible horizon seam is corrected rather than
 * merely hidden around the finite outer edges.
 */
public final class VanillaSkyHorizonSmoother {
    private static final float COLOR_JUMP_THRESHOLD = 0.035F;
    private static final ThreadLocal<Deque<SkyFrame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private VanillaSkyHorizonSmoother() {
    }

    public static void beginVanillaSky() {
        FRAMES.get().push(new SkyFrame());
    }

    public static void endVanillaSky() {
        Deque<SkyFrame> frames = FRAMES.get();
        if (!frames.isEmpty()) {
            frames.pop();
        }
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    public static void captureUpperDomeColor() {
        SkyFrame frame = currentFrame();
        if (frame != null) {
            frame.upperColor = currentColor();
        }
    }

    public static void harmonizeLowerDomeColor() {
        SkyFrame frame = currentFrame();
        if (frame == null || frame.repaired || frame.upperColor == null) {
            return;
        }
        float[] lowerColor = currentColor();
        float[] reconciledColor = reconciledLowerDomeColor(frame.upperColor, lowerColor);
        if (reconciledColor == lowerColor) {
            return;
        }
        frame.repaired = true;
        GL11.glColor4f(reconciledColor[0], reconciledColor[1], reconciledColor[2], reconciledColor[3]);
    }

    static boolean hasVisibleColorJump(float[] upperColor, float[] lowerColor) {
        if (!isOpaqueColor(upperColor) || !isOpaqueColor(lowerColor)) {
            return false;
        }
        float red = upperColor[0] - lowerColor[0];
        float green = upperColor[1] - lowerColor[1];
        float blue = upperColor[2] - lowerColor[2];
        return (float) Math.sqrt(red * red + green * green + blue * blue) >= COLOR_JUMP_THRESHOLD;
    }

    static float[] reconciledLowerDomeColor(float[] upperColor, float[] lowerColor) {
        if (!hasVisibleColorJump(upperColor, lowerColor)) {
            return lowerColor;
        }
        return new float[]{upperColor[0], upperColor[1], upperColor[2], lowerColor[3]};
    }

    private static boolean isOpaqueColor(float[] color) {
        return color != null && color.length == 4 && color[3] > 0.99F;
    }

    private static SkyFrame currentFrame() {
        Deque<SkyFrame> frames = FRAMES.get();
        return frames.isEmpty() ? null : frames.peek();
    }

    private static float[] currentColor() {
        FloatBuffer color = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        return new float[]{color.get(0), color.get(1), color.get(2), color.get(3)};
    }

    private static final class SkyFrame {
        private float[] upperColor;
        private boolean repaired;
    }
}
