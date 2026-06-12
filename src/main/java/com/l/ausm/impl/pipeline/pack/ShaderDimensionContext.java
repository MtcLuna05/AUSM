package com.l.ausm.impl.pipeline.pack;

import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

public final class ShaderDimensionContext {
    private static final ThreadLocal<Integer> OVERRIDE_DIMENSION = new ThreadLocal<>();

    private ShaderDimensionContext() {
    }

    public static int currentDimensionId() {
        Integer override = OVERRIDE_DIMENSION.get();
        if (override != null && override != Integer.MIN_VALUE) {
            return override;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.world.provider == null) {
            return 0;
        }
        return mc.world.provider.getDimension();
    }

    public static <T> T withDimension(int dimensionId, Supplier<T> action) {
        if (dimensionId == Integer.MIN_VALUE) {
            return action.get();
        }

        Integer previous = OVERRIDE_DIMENSION.get();
        OVERRIDE_DIMENSION.set(dimensionId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                OVERRIDE_DIMENSION.remove();
            } else {
                OVERRIDE_DIMENSION.set(previous);
            }
        }
    }

    public static void runWithDimension(int dimensionId, Runnable action) {
        withDimension(dimensionId, () -> {
            action.run();
            return null;
        });
    }
}
