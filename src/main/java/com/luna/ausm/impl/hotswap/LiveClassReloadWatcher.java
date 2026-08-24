package com.luna.ausm.impl.hotswap;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

final class LiveClassReloadWatcher implements Runnable {
    private static final String MIXIN_PACKAGE_SEGMENT = ".mixin.";
    private static final String MIXIN_AGENT_LOADER = "org.spongepowered.tools.agent.MixinAgentClassLoader";
    private static final String AGENT_PACKAGE = "com.luna.ausm.impl.hotswap.";
    private static final long WRITE_SETTLE_MILLIS = 120L;
    private static volatile LiveClassReloadWatcher activeWatcher;

    private final Instrumentation instrumentation;
    private final Path root;
    private final WatchService watchService;
    private final Map<WatchKey, Path> watchedDirectories = new HashMap<>();
    private final ClassRedefinitionTransaction transactions;

    private LiveClassReloadWatcher(Instrumentation instrumentation, Path root) throws IOException {
        this.instrumentation = instrumentation;
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        this.watchService = FileSystems.getDefault().newWatchService();
        this.transactions = new ClassRedefinitionTransaction(
                instrumentation::redefineClasses,
                this::readBaselineDefinition);
        registerTree(this.root);
    }

    static void start(Instrumentation instrumentation, Path root) {
        if (!instrumentation.isRedefineClassesSupported()) {
            System.err.println("[AUSM HotSwap] JVM class redefinition is unavailable.");
            return;
        }
        try {
            LiveClassReloadWatcher watcher = new LiveClassReloadWatcher(instrumentation, root);
            activeWatcher = watcher;
            Thread thread = new Thread(watcher, "AUSM live class reload");
            thread.setDaemon(true);
            thread.start();
            System.err.println("[AUSM HotSwap] Watching production classes in " + watcher.root);
        } catch (IOException exception) {
            System.err.println("[AUSM HotSwap] Could not start class watcher: " + exception);
        }
    }

    static boolean rollbackLastBatch() {
        LiveClassReloadWatcher watcher = activeWatcher;
        if (watcher == null) {
            System.err.println("[AUSM HotSwap] Cannot roll back: no live watcher is active.");
            return false;
        }
        return watcher.rollback();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                List<Path> changedClasses = new ArrayList<>();
                collectChangedClasses(key, changedClasses);
                while ((key = watchService.poll(WRITE_SETTLE_MILLIS, TimeUnit.MILLISECONDS)) != null) {
                    collectChangedClasses(key, changedClasses);
                }
                process(changedClasses);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                System.err.println("[AUSM HotSwap] Watcher error: " + exception);
            }
        }
    }

    private void collectChangedClasses(WatchKey key, List<Path> changedClasses) {
        Path directory = watchedDirectories.get(key);
        if (directory == null) {
            key.reset();
            return;
        }
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }
            Path changed = directory.resolve((Path) event.context());
            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                registerTreeSafely(changed);
            } else if (changed.toString().endsWith(".class")) {
                changedClasses.add(changed);
            }
        }
        key.reset();
    }

    private void process(List<Path> changedClasses) {
        if (changedClasses.isEmpty()) {
            return;
        }
        List<Path> distinctClasses = changedClasses.stream().distinct().toList();
        // Define every new support class before redefining loaded dependants.
        // This prevents a frame from executing newly redefined bytecode while
        // one of the classes referenced by that bytecode is still absent.
        distinctClasses.forEach(this::defineIfNew);
        reloadBatch(distinctClasses);
    }

    private void defineIfNew(Path classFile) {
        String className = className(classFile);
        if (className == null
                || className.startsWith(AGENT_PACKAGE)
                || className.contains(MIXIN_PACKAGE_SEGMENT)
                || !loadedCandidates(className).isEmpty()) {
            return;
        }
        try {
            byte[] bytecode = Files.readAllBytes(classFile);
            Class<?> defined = DynamicClassDefinitionSupport.tryDefine(instrumentation, className, bytecode);
            if (defined != null) {
                System.err.println("[AUSM HotSwap] Defined new " + className + " via "
                        + defined.getClassLoader().getClass().getName());
            }
        } catch (IOException exception) {
            System.err.println("[AUSM HotSwap] Could not read " + classFile + ": " + exception);
        }
    }

    private void reloadBatch(List<Path> classFiles) {
        Map<Class<?>, byte[]> replacements = new LinkedHashMap<>();
        try {
            for (Path classFile : classFiles) {
                String className = className(classFile);
                if (className == null || className.startsWith(AGENT_PACKAGE)) {
                    continue;
                }
                byte[] bytecode = Files.readAllBytes(classFile);
                List<Class<?>> candidates = loadedCandidates(className);
                if (candidates.isEmpty()) {
                    System.err.println("[AUSM HotSwap] Staged " + className + " (not loaded yet).");
                    continue;
                }
                candidates.forEach(candidate -> replacements.put(candidate, bytecode));
            }

            List<Class<?>> redefined = transactions.apply(replacements);
            if (!redefined.isEmpty()) {
                System.err.println("[AUSM HotSwap] Atomically redefined " + redefined.size()
                        + " class(es): " + classNames(redefined));
                System.err.println("[AUSM HotSwap] Previous bytecode retained; run rollbackHotSwap"
                        + " before the JVM exits to restore this batch.");
            }
        } catch (Throwable throwable) {
            System.err.println("[AUSM HotSwap] Atomic batch rejected; no loaded class changed: " + throwable);
        }
    }

    private List<Class<?>> loadedCandidates(String className) {
        List<Class<?>> loaded = new ArrayList<>();
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            if (type.getName().equals(className) && instrumentation.isModifiableClass(type)) {
                loaded.add(type);
            }
        }
        if (!className.contains(MIXIN_PACKAGE_SEGMENT)) {
            return loaded;
        }
        List<Class<?>> mixinAgentCopies = loaded.stream()
                .filter(type -> type.getClassLoader() != null)
                .filter(type -> MIXIN_AGENT_LOADER.equals(type.getClassLoader().getClass().getName()))
                .toList();
        if (!mixinAgentCopies.isEmpty()) {
            return mixinAgentCopies;
        }
        System.err.println("[AUSM HotSwap] No MixinAgent copy is loaded for " + className + "; restart-only mixin change.");
        return List.of();
    }

    private boolean rollback() {
        try {
            if (!transactions.canRollback()) {
                System.err.println("[AUSM HotSwap] No committed live batch is available to roll back.");
                return false;
            }
            List<Class<?>> restored = transactions.rollback();
            System.err.println("[AUSM HotSwap] Atomically rolled back " + restored.size()
                    + " class(es): " + classNames(restored));
            return true;
        } catch (Throwable throwable) {
            System.err.println("[AUSM HotSwap] Rollback failed; the current definitions remain active: " + throwable);
            return false;
        }
    }

    private byte[] readBaselineDefinition(Class<?> target) throws IOException {
        String resourceName = target.getName().replace('.', '/') + ".class";
        ClassLoader loader = target.getClassLoader();
        try (InputStream input = loader == null
                ? ClassLoader.getSystemResourceAsStream(resourceName)
                : loader.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Class resource is unavailable for " + target.getName());
            }
            return input.readAllBytes();
        }
    }

    private static String classNames(List<Class<?>> classes) {
        return classes.stream().map(Class::getName).collect(Collectors.joining(", "));
    }

    private String className(Path classFile) {
        if (!classFile.normalize().startsWith(root) || !Files.isRegularFile(classFile)) {
            return null;
        }
        String relative = root.relativize(classFile.toAbsolutePath().normalize()).toString();
        return relative.substring(0, relative.length() - ".class".length())
                .replace('/', '.')
                .replace('\\', '.');
    }

    private void registerTree(Path start) throws IOException {
        try (var directories = Files.walk(start)) {
            directories.filter(Files::isDirectory)
                    .sorted(Comparator.naturalOrder())
                    .forEach(this::registerDirectorySafely);
        }
    }

    private void registerTreeSafely(Path start) {
        try {
            registerTree(start);
        } catch (IOException exception) {
            System.err.println("[AUSM HotSwap] Could not watch " + start + ": " + exception);
        }
    }

    private void registerDirectorySafely(Path directory) {
        try {
            WatchKey key = directory.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            watchedDirectories.put(key, directory);
        } catch (IOException exception) {
            System.err.println("[AUSM HotSwap] Could not watch " + directory + ": " + exception);
        }
    }
}
