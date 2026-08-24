package com.luna.ausm.impl.util;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolves one exact method without letting unrelated, optional-mod method descriptors poison the lookup.
 */
final class LinkageSafeMethodLookup {

    private LinkageSafeMethodLookup() {
    }

    static Method find(Class<?> owner, String name, Class<?>[] parameterTypes) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
        }
        return findDeclared(owner, name, parameterTypes, new HashSet<>());
    }

    private static Method findDeclared(Class<?> owner, String name, Class<?>[] parameterTypes,
                                       Set<Class<?>> visited) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            if (!visited.add(current)) {
                continue;
            }
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
            }
            Method interfaceMethod = findInterface(current, name, parameterTypes, visited);
            if (interfaceMethod != null) {
                return interfaceMethod;
            }
        }
        return null;
    }

    private static Method findInterface(Class<?> owner, String name, Class<?>[] parameterTypes,
                                        Set<Class<?>> visited) {
        Class<?>[] interfaces;
        try {
            interfaces = owner.getInterfaces();
        } catch (SecurityException | LinkageError ignored) {
            return null;
        }
        for (Class<?> interfaceClass : interfaces) {
            if (!visited.add(interfaceClass)) {
                continue;
            }
            try {
                return interfaceClass.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
            }
            Method nested = findInterface(interfaceClass, name, parameterTypes, visited);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
