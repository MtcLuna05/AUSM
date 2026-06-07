package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.impl.MainMod;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ShaderSourceDumper {
    private ShaderSourceDumper() {
    }

    static void dumpFailedSource(String shaderPath, String source) {
        if (source == null || source.isBlank()) {
            return;
        }

        try {
            Path directory = Minecraft.getMinecraft().gameDir.toPath()
                    .resolve("config")
                    .resolve("ausm")
                    .resolve("failed-shaders");
            Files.createDirectories(directory);

            Path target = directory.resolve(sanitize(shaderPath) + ".glsl");
            Files.writeString(target, source, StandardCharsets.UTF_8);
            MainMod.LOGGER.warn("[ShaderCompiler] Wrote failed shader source to {}", target.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            MainMod.LOGGER.warn("[ShaderCompiler] Failed to write failed shader source for '{}'", shaderPath, e);
        }
    }

    private static String sanitize(String shaderPath) {
        if (shaderPath == null || shaderPath.isBlank()) {
            return "unknown";
        }
        return shaderPath.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
