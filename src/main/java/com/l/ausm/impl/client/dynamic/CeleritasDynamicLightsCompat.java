package com.l.ausm.impl.client.dynamic;

import com.l.ausm.impl.MainMod;
import net.minecraftforge.fml.common.Loader;

public final class CeleritasDynamicLightsCompat {
    private static final String MOD_ID = "celeritasdynamiclights";
    private static final String CONFIG_CLASS = "toni.sodiumdynamiclights.config.DynamicLightsConfig";
    private static final String MODE_CLASS = "toni.sodiumdynamiclights.DynamicLightsMode";
    private static final String LOCKOUT_MESSAGE = "Disabled because Celeritas Dynamic Lights is installed.";

    private static Boolean installed;
    private static boolean lockoutLogged;

    private CeleritasDynamicLightsCompat() {
    }

    public static boolean installed() {
        Boolean cached = installed;
        if (cached != null) {
            return cached;
        }

        boolean detected = loaderReportsInstalled() || classPresent(CONFIG_CLASS) || classPresent(MODE_CLASS);
        installed = detected;
        if (detected) {
            logLockout();
        }
        return detected;
    }

    public static String lockoutMessage() {
        return LOCKOUT_MESSAGE;
    }

    public static void logLockout() {
        if (lockoutLogged) {
            return;
        }
        lockoutLogged = true;
        MainMod.LOGGER.info("[DynamicLights] AUSM shaderless dynamic lights disabled because Celeritas Dynamic Lights is installed");
    }

    private static boolean loaderReportsInstalled() {
        try {
            return Loader.isModLoaded(MOD_ID);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean classPresent(String className) {
        if (classPresent(className, Thread.currentThread().getContextClassLoader())) {
            return true;
        }
        return classPresent(className, CeleritasDynamicLightsCompat.class.getClassLoader());
    }

    private static boolean classPresent(String className, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }
}
