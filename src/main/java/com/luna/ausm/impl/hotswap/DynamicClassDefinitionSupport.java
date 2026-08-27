package com.luna.ausm.impl.hotswap;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
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
            return Collections.emptyList();
        }
        int packageEnd = className.lastIndexOf('.');
        if (packageEnd <= 0) {
            return Collections.emptyList();
        }
        String packageName = className.substring(0, packageEnd);
        Set<ClassLoader> attemptedLoaders = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Class<?>> definitions = new ArrayList<>();
        for (Class<?> anchor : instrumentation.getAllLoadedClasses()) {
            ClassLoader loader = anchor.getClassLoader();
            if (loader == null
                    || anchor.getPackage() == null
                    || !packageName.equals(anchor.getPackage().getName())
                    || !attemptedLoaders.add(loader)) {
                continue;
            }
            Class<?> loaded = loadedClass(instrumentation, className, loader);
            if (loaded != null) {
                definitions.add(loaded);
                continue;
            }
            try {
                Class<?> defined = defineClass(anchor, loader, className, bytecode);
                if (!className.equals(defined.getName())) {
                    throw new LinkageError("Staged class name mismatch: expected " + className
                            + " but defined " + defined.getName());
                }
                definitions.add(defined);
            } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError failure) {
                Class<?> raced = loadedClass(instrumentation, className, loader);
                if (raced != null) {
                    definitions.add(raced);
                    continue;
                }
                System.err.println("[AUSM HotSwap] Could not define " + className
                        + " beside " + anchor.getName() + ": " + failure);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(definitions));
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

    private static Class<?> defineClass(Class<?> anchor, ClassLoader loader, String className, byte[] bytecode)
            throws ReflectiveOperationException {
        try {
            Class<?> methodHandles = Class.forName("java.lang.invoke.MethodHandles");
            Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");
            Method lookup = methodHandles.getMethod("lookup");
            Method privateLookupIn = methodHandles.getMethod("privateLookupIn", Class.class, lookupClass);
            Object privateLookup = privateLookupIn.invoke(null, anchor, lookup.invoke(null));
            Method defineClass = lookupClass.getMethod("defineClass", byte[].class);
            return (Class<?>) defineClass.invoke(privateLookup, bytecode);
        } catch (NoSuchMethodException ignored) {
            // Java 8 has no Lookup#defineClass; use its accessible class-loader route instead.
        }
        Method defineClass = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class);
        defineClass.setAccessible(true);
        return (Class<?>) defineClass.invoke(loader, className, bytecode, 0, bytecode.length);
    }
}
