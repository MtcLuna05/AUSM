package com.l.ausm.impl.pipeline;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.time.LocalDateTime;

/** Stateless frame-time, calendar, matrix, and camera-coordinate helpers. */
final class PipelineFrameValues {
    private PipelineFrameValues() {
    }

    static int[] currentDate() { LocalDateTime now = LocalDateTime.now(); return new int[]{now.getYear(), now.getMonthValue(), now.getDayOfMonth()}; }
    static int[] currentTime() { LocalDateTime now = LocalDateTime.now(); return new int[]{now.getHour(), now.getMinute(), now.getSecond()}; }
    static int[] currentYearTime() {
        LocalDateTime now = LocalDateTime.now();
        int elapsed = (now.getDayOfYear() - 1) * 86400 + now.getHour() * 3600 + now.getMinute() * 60 + now.getSecond();
        return new int[]{elapsed, now.toLocalDate().lengthOfYear() * 86400 - elapsed};
    }
    static float smoothingFactor(float halfLifeDeciseconds, float frameTimeSeconds) {
        if (halfLifeDeciseconds <= 0.0f) return 1.0f;
        float decay = (float) (Math.log(2.0) / (halfLifeDeciseconds * 0.1f));
        return 1.0f - (float) Math.exp(-decay * Math.max(0.0f, frameTimeSeconds));
    }
    static FloatBuffer createIrisLightmapTextureMatrix() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(new float[]{0.00390625f, 0, 0, 0, 0, 0.00390625f, 0, 0, 0, 0, 0.00390625f, 0, 0.03125f, 0.03125f, 0.03125f, 1});
        return (FloatBuffer) buffer.flip();
    }
    static FloatBuffer irisLightmapTextureMatrix(FloatBuffer matrix) { matrix.position(0); return matrix; }
    static int[] cameraPositionInt(double[] position) { return new int[]{(int) Math.floor(position[0]), (int) Math.floor(position[1]), (int) Math.floor(position[2])}; }
    static float[] cameraPositionFract(double[] position) { return new float[]{(float) (position[0] - Math.floor(position[0])), (float) (position[1] - Math.floor(position[1])), (float) (position[2] - Math.floor(position[2]))}; }
    static double irisCameraShift(double adjusted, double delta, double absoluteAdjusted) {
        return absoluteAdjusted > 30000.0 || delta > 1000.0 ? -(adjusted - adjusted % 30000.0) : 0.0;
    }
}
