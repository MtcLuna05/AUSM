package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import net.minecraftforge.fml.common.Loader;

/** Keeps optional DH classes outside AUSM's normal class-loading path. */
public final class DistantHorizonsCompat {
    private static boolean installed;
    private static boolean shadowRenderingFailed;
    private static java.lang.reflect.Method shadowRender;
    private DistantHorizonsCompat() {
    }

    public static void init() {
        installed = Loader.isModLoaded("distanthorizons") || Loader.isModLoaded("DistantHorizons");
        if (!installed) {
            return;
        }
        try {
            Class.forName("com.luna.ausm.impl.client.DistantHorizonsNativeBridge")
                    .getMethod("initialize").invoke(null);
        } catch (ReflectiveOperationException | LinkageError failure) {
            MainMod.LOGGER.warn("[AUSMDistantHorizons] Native texture integration unavailable.", failure);
        }
    }

    public static boolean installed() {
        return installed;
    }

    /** Checks loader state directly so settings can choose a DH-aware default during preInit. */
    public static boolean detected() {
        return installed || Loader.isModLoaded("distanthorizons") || Loader.isModLoaded("DistantHorizons");
    }

    public static int renderShadow(float partialTicks) {
        if (!installed || shadowRenderingFailed) return 0;
        try {
            if (shadowRender == null) {
                shadowRender = Class.forName("com.luna.ausm.impl.client.DistantHorizonsShadowBridge")
                        .getMethod("render", float.class);
            }
            return (Integer) shadowRender.invoke(null, partialTicks);
        } catch (ReflectiveOperationException | LinkageError failure) {
            shadowRenderingFailed = true;
            MainMod.LOGGER.warn("[AUSMDistantHorizons] Could not render LOD shadow casters.", failure);
            return 0;
        }
    }

    public static void renderDeferred() {
        if (!installed) return;
        try {
            Class.forName("com.luna.ausm.impl.client.DistantHorizonsShadedBridge")
                    .getDeclaredMethod("renderDeferred").invoke(null);
        } catch (ReflectiveOperationException | LinkageError failure) {
            MainMod.LOGGER.warn("[AUSMDistantHorizons] Could not render shaderpack water.", failure);
        }
    }

    public static void releaseRenderingSettings() {
        shadowRenderingFailed = false;
        if (installed) {
            try {
                Class.forName("com.luna.ausm.impl.client.DistantHorizonsNativeBridge")
                        .getMethod("releaseRenderingSettings").invoke(null);
            } catch (ReflectiveOperationException | LinkageError failure) {
                MainMod.LOGGER.warn("[AUSMDistantHorizons] Could not release the shader rendering settings.", failure);
            }
        }
    }
}
