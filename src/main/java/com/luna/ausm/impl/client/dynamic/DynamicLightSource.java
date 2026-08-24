package com.luna.ausm.impl.client.dynamic;

import net.minecraft.util.math.BlockPos;

final class DynamicLightSource {
    private final String key;
    private final double x;
    private final double y;
    private final double z;
    private final int light;

    DynamicLightSource(String key, double x, double y, double z, int light) {
        this.key = key;
        this.x = x;
        this.y = y;
        this.z = z;
        this.light = light;
    }

    String key() {
        return key;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    int light() {
        return light;
    }

    int lightAt(double blockX, double blockY, double blockZ) {
        double dx = blockX - x;
        double dy = blockY - y;
        double dz = blockZ - z;
        double maxDistance = light + 0.5D;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared > maxDistance * maxDistance) {
            return 0;
        }
        return Math.max(0, light - (int) Math.floor(Math.sqrt(distanceSquared)));
    }

    BlockPos blockPos() {
        return new BlockPos(x, y, z);
    }

    boolean sameRenderRegion(DynamicLightSource other) {
        return other != null
                && light == other.light
                && blockPos().equals(other.blockPos());
    }
}
