package com.luna.ausm.impl.hotswap;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class DynamicClassDefinitionSupport {
    private DynamicClassDefinitionSupport() {
    }

    static Class<?> tryDefine(Instrumentation instrumentation, String className, byte[] bytecode) {
        List<Class<?>> definitions = tryDefineAll(instrumentation, className, bytecode);
        return definitions.isEmpty() ? null : definitions.get(0);
    }

    static List<Class<?>> tryDefineAll(Instrumentation instrumentation, String className, byte[] bytecode) {
        if (instrumentation == null || className == null || bytecode == null) {
            return List.of();
        }
        int packageEnd = className.lastIndexOf('.');
        if (packageEnd <= 0) {
            return List.of();
        }
        String packageName = className.substring(0, packageEnd);
        Set<ClassLoader> attemptedLoaders = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Class<?>> definitions = new ArrayList<>();
        for (Class<?> anchor : instrumentation.getAllLoadedClasses()) {
            ClassLoader loader = anchor.getClassLoader();
            if (loader == null
                    || !packageName.equals(anchor.getPackageName())
                    || !attemptedLoaders.add(loader)) {
                continue;
            }
            Class<?> loaded = loadedClass(instrumentation, className, loader);
            if (loaded != null) {
                definitions.add(loaded);
                continue;
            }
            try {
                Class<?> defined = MethodHandles.privateLookupIn(anchor, MethodHandles.lookup()).defineClass(bytecode);
                if (!className.equals(defined.getName())) {
                    throw new LinkageError("Staged class name mismatch: expected " + className
                            + " but defined " + defined.getName());
                }
                definitions.add(defined);
            } catch (IllegalAccessException | IllegalArgumentException | LinkageError failure) {
                Class<?> raced = loadedClass(instrumentation, className, loader);
                if (raced != null) {
                    definitions.add(raced);
                    continue;
                }
                System.err.println("[AUSM HotSwap] Could not define " + className
                        + " beside " + anchor.getName() + ": " + failure);
            }
        }
        return List.copyOf(definitions);
    }

    static Class<?> loadedClass(Instrumentation instrumentation, String className) {
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded.getName().equals(className)) {
                return loaded;
            }
        }
        return null;
    }

    private static Class<?> loadedClass(Instrumentation instrumentation, String className, ClassLoader loader) {
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded.getName().equals(className) && loaded.getClassLoader() == loader) {
                return loaded;
            }
        }
        return null;
    }
}
