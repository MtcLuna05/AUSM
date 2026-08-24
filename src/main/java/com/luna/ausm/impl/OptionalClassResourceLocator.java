package com.luna.ausm.impl;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import net.minecraft.launchwrapper.Launch;

final class OptionalClassResourceLocator {
    private OptionalClassResourceLocator() {
    }

    static boolean isPresent(String resourcePath, boolean allowJarFallback) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return false;
        }

        return resourcePresent(MainMixinConfigPlugin.class.getClassLoader(), resourcePath)
                || resourcePresent(Thread.currentThread().getContextClassLoader(), resourcePath)
                || resourcePresent(Launch.classLoader, resourcePath)
                || ClassLoader.getSystemResource(resourcePath) != null
                || allowJarFallback && resourceJarInModsDirectory(resourcePath) != null;
    }

    private static boolean resourcePresent(ClassLoader loader, String resourcePath) {
        try {
            return loader != null && loader.getResource(resourcePath) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static File resourceJarInModsDirectory(String resourcePath) {
        File modsDirectory = new File(System.getProperty("user.dir", "."), "mods");
        if (!modsDirectory.isDirectory()) {
            return null;
        }

        File[] files = modsDirectory.listFiles((directory, name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".jar") || lowerName.endsWith(".zip");
        });
        if (files == null) {
            return null;
        }

        for (File file : files) {
            try (JarFile jar = new JarFile(file)) {
                if (jar.getEntry(resourcePath) != null) {
                    return file;
                }
            } catch (IOException | RuntimeException ignored) {
                // A broken optional mod archive must not make mixin selection fatal.
            }
        }
        return null;
    }
}
