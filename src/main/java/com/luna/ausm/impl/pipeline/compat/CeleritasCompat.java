package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.MainMod;
import net.minecraftforge.fml.common.Loader;

/**
 * Celeritas is optional. When present, AUSM keeps its compile optimizations,
 * while AUSM remains the sole terrain/entity renderer and frame owner.
 */
public final class CeleritasCompat {
    private static final String MOD_ID = "celeritas";
    private static final String CeleritasRenderer = "org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer";
    private static final String EMBEDDIUMRenderer = "org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager";
    private static final String SHADER_BRIDGE = "org.embeddedt.embeddium.impl.render.ShaderModBridge";
    private static volatile Boolean installed;
    private static volatile boolean diagnosticsLogged;

    private CeleritasCompat() {
    }

    public static boolean installed() {
        Boolean cached = installed;
        if (cached != null) {
            return cached;
        }
        boolean detected = classPresent(CeleritasRenderer);
        installed = detected;
        return detected;
    }

    public static void logDiagnostics() {
        if (diagnosticsLogged) {
            return;
        }
        diagnosticsLogged = true;

        boolean celeritas = installed();
        boolean nothirium = loaderReports("nothirium") || loaderReports("naughthirium");
        boolean shaderBridge = classPresent(SHADER_BRIDGE);
        MainMod.LOGGER.info(
                "[CeleritasCompat] detected={} celeritasRenderer={} embeddiumRenderer={} shaderBridge={} nothirium={}",
                celeritas,
                classPresent(CeleritasRenderer),
                classPresent(EMBEDDIUMRenderer),
                shaderBridge,
                nothirium
        );
        if (celeritas) {
            MainMod.LOGGER.info(
                    "[CeleritasCompat] optimization mode: Celeritas compile helpers retained; AUSM owns frame pacing, terrain, entity, and pass rendering"
            );
        }
    }

    private static boolean loaderReportsInstalled() {
        return loaderReports(MOD_ID);
    }

    private static boolean loaderReports(String modId) {
        try {
            return Loader.isModLoaded(modId);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean classPresent(String className) {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (classPresent(className, context)) {
            return true;
        }
        return classPresent(className, CeleritasCompat.class.getClassLoader());
    }

    private static boolean classPresent(String className, ClassLoader loader) {
        if (loader == null) {
            return false;
        }
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }
}
