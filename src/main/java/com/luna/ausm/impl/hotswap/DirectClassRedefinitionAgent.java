package com.luna.ausm.impl.hotswap;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot attach agent for replacing explicitly staged AUSM classes in the
 * game class loader. The directory watcher can see auxiliary loader copies of
 * a class; this agent deliberately targets only LaunchClassLoader.
 */
public final class DirectClassRedefinitionAgent {
    private static final String HOTSWAP_DIRECTORY_PROPERTY = "ausm.hotswap.dir";
    private static final String GAME_CLASS_LOADER = "net.minecraft.launchwrapper.LaunchClassLoader";

    private DirectClassRedefinitionAgent() {
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        if (arguments != null && arguments.startsWith("@inject:")) {
            DynamicClassInjectorAgent.agentmain(arguments.substring("@inject:".length()), instrumentation);
            return;
        }
        String configuredDirectory = System.getProperty(HOTSWAP_DIRECTORY_PROPERTY, "").trim();
        if (configuredDirectory.isEmpty() || arguments == null || arguments.isBlank()) {
            System.err.println("[AUSM HotSwap] Direct redefine requires a staged directory and class name.");
            return;
        }
        try {
            Path root = Path.of(configuredDirectory).toAbsolutePath().normalize();
            List<ClassDefinition> replacements = new ArrayList<>();
            for (String requested : arguments.split(",")) {
                String className = requested.trim();
                Path classFile = root.resolve(className.replace('.', '/') + ".class").normalize();
                if (className.isEmpty() || !classFile.startsWith(root) || !Files.isRegularFile(classFile)) {
                    throw new IOException("Staged class is unavailable: " + className);
                }
                Class<?> target = findGameClass(instrumentation, className);
                if (target == null) {
                    throw new ClassNotFoundException("No LaunchClassLoader class found: " + className);
                }
                replacements.add(new ClassDefinition(target, Files.readAllBytes(classFile)));
            }
            instrumentation.redefineClasses(replacements.toArray(ClassDefinition[]::new));
            System.err.println("[AUSM HotSwap] Directly redefined " + replacements.size() + " game class(es): " + arguments);
        } catch (Throwable throwable) {
            System.err.println("[AUSM HotSwap] Direct redefine rejected; no class changed: " + throwable);
        }
    }

    private static Class<?> findGameClass(Instrumentation instrumentation, String className) {
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            ClassLoader loader = type.getClassLoader();
            if (type.getName().equals(className)
                    && loader != null
                    && GAME_CLASS_LOADER.equals(loader.getClass().getName())
                    && instrumentation.isModifiableClass(type)) {
                return type;
            }
        }
        return null;
    }
}
