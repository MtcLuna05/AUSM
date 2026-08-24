package com.luna.ausm.impl.hotswap;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** One-shot attach agent for defining explicitly named staged classes. */
public final class DynamicClassInjectorAgent {
    private static final String HOTSWAP_DIRECTORY_PROPERTY = "ausm.hotswap.dir";

    private DynamicClassInjectorAgent() {
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        if (arguments != null && arguments.trim().equals("@rollback")) {
            LiveClassReloadWatcher.rollbackLastBatch();
            return;
        }
        String configuredDirectory = System.getProperty(HOTSWAP_DIRECTORY_PROPERTY, "").trim();
        if (configuredDirectory.isEmpty()) {
            System.err.println("[AUSM HotSwap] Cannot inject class: no live-class directory configured.");
            return;
        }
        if (arguments == null || arguments.isBlank()) {
            System.err.println("[AUSM HotSwap] Cannot inject class: pass one or more comma-separated class names.");
            return;
        }
        Path root = Path.of(configuredDirectory).toAbsolutePath().normalize();
        for (String requested : arguments.split(",")) {
            String className = requested.trim();
            if (className.isEmpty() || className.contains(".mixin.")) {
                continue;
            }
            Path classFile = root.resolve(className.replace('.', '/') + ".class").normalize();
            if (!classFile.startsWith(root) || !Files.isRegularFile(classFile)) {
                System.err.println("[AUSM HotSwap] Staged class not found: " + className);
                continue;
            }
            try {
                List<Class<?>> definitions = DynamicClassDefinitionSupport.tryDefineAll(
                        instrumentation, className, Files.readAllBytes(classFile));
                if (definitions.isEmpty()) {
                    System.err.println("[AUSM HotSwap] No loaded same-package anchor for " + className);
                } else {
                    System.err.println("[AUSM HotSwap] Injected " + className + " into " + definitions.size()
                            + " loader(s): " + definitions.stream()
                            .map(defined -> defined.getClassLoader().getClass().getName())
                            .distinct()
                            .toList());
                }
            } catch (IOException exception) {
                System.err.println("[AUSM HotSwap] Could not inject " + className + ": " + exception);
            }
        }
    }
}
