package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;

/**
 * Holds the shader-compile request raised while a client world is connecting
 * and exposes the first safe render boundary at which that request may run.
 */
public final class ShaderPipelineWorldLoadGate {
    private int pendingDimensionId = Integer.MIN_VALUE;
    private boolean pending;

    public void queue(int dimensionId) {
        pending = true;
        pendingDimensionId = dimensionId;
    }

    public void clear() {
        pending = false;
        pendingDimensionId = Integer.MIN_VALUE;
    }

    public boolean isPending() {
        return pending;
    }

    public int pendingDimensionId() {
        return pendingDimensionId;
    }

    public static boolean isPlayableWorldReady() {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        return minecraft != null
                && MinecraftReflectionCompat.world(minecraft) != null
                && MinecraftReflectionCompat.player(minecraft) != null
                && MinecraftReflectionCompat.renderViewEntity(minecraft) != null
                && !(MinecraftReflectionCompat.currentScreen(minecraft) instanceof GuiDownloadTerrain);
    }
}
