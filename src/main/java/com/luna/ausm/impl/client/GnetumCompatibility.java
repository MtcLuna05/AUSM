package com.luna.ausm.impl.client;

/** Detects Gnetum without depending on its private HUD-cache configuration. */
public final class GnetumCompatibility {
    private static final boolean INSTALLED = detectGnetum();

    private GnetumCompatibility() {
    }

    public static boolean isInstalled() {
        return INSTALLED;
    }

    private static boolean detectGnetum() {
        try {
            Class.forName("me.decce.gnetum.Gnetum", false, GnetumCompatibility.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
