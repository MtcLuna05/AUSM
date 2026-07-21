package com.l.ausm.impl.pipeline;

/** Angle interpolation used by temporal-history reset detection. */
final class PipelineTemporalMath {
    private PipelineTemporalMath() { }
    static float interpolateAngle(float previous, float current, float partialTicks) { return previous + wrapDegrees(current - previous) * partialTicks; }
    static float wrapDegrees(float value) { value %= 360.0f; if (value >= 180.0f) value -= 360.0f; if (value < -180.0f) value += 360.0f; return value; }
}
