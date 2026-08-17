package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.WorldProvider;

public final class ShaderDimensionContext {
    private static final ThreadLocal<Integer> OVERRIDE_DIMENSION = new ThreadLocal<>();

    private ShaderDimensionContext() {
    }

    public static int currentDimensionId() {
        Integer override = OVERRIDE_DIMENSION.get();
        if (override != null && override != Integer.MIN_VALUE) {
            return override;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        WorldClient world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        WorldProvider provider = MinecraftReflectionCompat.worldProvider(world);
        if (provider == null) {
            return 0;
        }
        return MinecraftReflectionCompat.providerDimension(provider);
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
