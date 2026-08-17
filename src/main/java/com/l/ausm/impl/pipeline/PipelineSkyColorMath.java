package com.l.ausm.impl.pipeline;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.util.math.Vec3d;

/**
 * Pure color operations used by owned-sky rendering.
 */
final class PipelineSkyColorMath {
    private PipelineSkyColorMath() {
    }

    static Vec3d desaturate(Vec3d color, double saturation) {
        double s = clamp01(saturation), x = MinecraftReflectionCompat.vecX(color), y = MinecraftReflectionCompat.vecY(color), z = MinecraftReflectionCompat.vecZ(color), l = x * 0.299 + y * 0.587 + z * 0.114;
        return new Vec3d(clamp01(l + (x - l) * s), clamp01(l + (y - l) * s), clamp01(l + (z - l) * s));
    }

    static Vec3d mix(Vec3d from, Vec3d to, double factor) {
        double t = clamp01(factor), i = 1.0 - t;
        return new Vec3d(clamp01(MinecraftReflectionCompat.vecX(from) * i + MinecraftReflectionCompat.vecX(to) * t), clamp01(MinecraftReflectionCompat.vecY(from) * i + MinecraftReflectionCompat.vecY(to) * t), clamp01(MinecraftReflectionCompat.vecZ(from) * i + MinecraftReflectionCompat.vecZ(to) * t));
    }

    static double clamp01(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }
}
