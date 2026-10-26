package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.MainMod;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.common.Loader;

/** Replays the vanilla layer callback skipped by AUSM's direct Nothirium draws. */
public final class CurvyPipesTerrainBridge {
    private static boolean resolved;
    private static Method renderLevel;

    private CurvyPipesTerrainBridge() {
    }

    public static void render(BlockRenderLayer layer) {
        if (layer != BlockRenderLayer.CUTOUT_MIPPED && layer != BlockRenderLayer.TRANSLUCENT) {
            return;
        }
        if (!resolved) {
            resolved = true;
            if (Loader.isModLoaded("curvy_pipes")) {
                try {
                    Class<?> handler = Class.forName("cyb0124.curvy_pipes.client.ClientHandler", false,
                            CurvyPipesTerrainBridge.class.getClassLoader());
                    renderLevel = handler.getMethod("renderLevel", BlockRenderLayer.class);
                } catch (ReflectiveOperationException | LinkageError failure) {
                    MainMod.LOGGER.warn("[CurvyPipesCompat] Could not resolve the terrain callback", failure);
                }
            }
        }
        if (renderLevel == null) {
            return;
        }
        try {
            renderLevel.invoke(null, layer);
        } catch (InvocationTargetException failure) {
            // A native/render failure must not silently disable all pipes.
            Throwable cause = failure.getCause();
            if (cause instanceof Error error) throw error;
            if (cause instanceof RuntimeException exception) throw exception;
            throw new IllegalStateException("Curvy Pipes terrain callback failed", cause);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Curvy Pipes terrain callback is inaccessible", failure);
        }
    }
}
