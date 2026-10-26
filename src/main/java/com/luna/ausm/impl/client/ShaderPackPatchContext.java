package com.luna.ausm.impl.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Paths and identity shared by all AUSM 1.12.2 shader-family adapters. */
record ShaderPackPatchContext(Path packRoot, String sourcePackName) {
    ShaderPackPatchContext {
        packRoot = Objects.requireNonNull(packRoot, "packRoot").toAbsolutePath().normalize();
        sourcePackName = Objects.requireNonNull(sourcePackName, "sourcePackName");
    }

    Path shader(String relativePath) {
        Path path = packRoot.resolve("shaders").resolve(relativePath).normalize();
        if (!path.startsWith(packRoot)) {
            throw new IllegalArgumentException("Shader path escapes pack root: " + relativePath);
        }
        return path;
    }

    boolean hasShader(String relativePath) {
        return Files.isRegularFile(shader(relativePath));
    }
}
