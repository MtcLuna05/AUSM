package com.luna.ausm.impl.hotswap;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;

/**
 * Early bootstrap used only by the local live-render development profile.
 */
public final class AusmHotSwapAgent {
    private static final String HOTSWAP_DIRECTORY_PROPERTY = "ausm.hotswap.dir";

    private AusmHotSwapAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        instrumentation.addTransformer(new FoundationMixinAgentClassLoaderTransformer(), false);

        String configuredDirectory = System.getProperty(HOTSWAP_DIRECTORY_PROPERTY, "").trim();
        if (configuredDirectory.isEmpty()) {
            System.err.println("[AUSM HotSwap] No live-class directory configured; bootstrap patch only.");
            return;
        }
        LiveClassReloadWatcher.start(instrumentation, Path.of(configuredDirectory));
    }
}
