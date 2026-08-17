package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

final class ShaderSourceDumper {
    private ShaderSourceDumper() {
    }

    static void dumpFailedSource(String shaderPath, String source) {
        dumpSource(shaderPath, source, "failed-shaders", true);
    }

    static void dumpDebugSource(String shaderPath, String source) {
        dumpSource(shaderPath, source, "debug-shaders", false);
    }

    private static void dumpSource(String shaderPath, String source, String directoryName, boolean warn) {
        if (source == null || source.isBlank()) {
            return;
        }

        try {
            Minecraft minecraft = MinecraftReflectionCompat.minecraft();
            File gameDir = minecraft != null
                    ? MinecraftReflectionCompat.gameDir(minecraft)
                    : new File(System.getProperty("user.dir", "."));
            Path directory = gameDir.toPath()
                    .resolve("config")
                    .resolve("ausm")
                    .resolve(directoryName);
            Files.createDirectories(directory);

            Path target = directory.resolve(sanitize(shaderPath) + ".glsl");
            Files.writeString(target, source, StandardCharsets.UTF_8);
            if (warn) {
                MainMod.LOGGER.warn("[ShaderCompiler] Wrote failed shader source to {}", target.toAbsolutePath());
            }
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[ShaderCompiler] Failed to write shader source for '{}'", shaderPath, e);
        }
    }

    private static String sanitize(String shaderPath) {
        if (shaderPath == null || shaderPath.isBlank()) {
            return "unknown";
        }
        return shaderPath.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
