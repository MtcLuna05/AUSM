package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FolderShaderPack implements ShaderPack {

    private final Path rootDir;
    private final String name;

    public FolderShaderPack(Path rootDir) {
        // Ensure rootDir is absolute to avoid resolution issues
        this.rootDir = rootDir.toAbsolutePath().normalize();
        this.name = this.rootDir.getFileName().toString();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public InputStream getResourceAsStream(String path) throws IOException {
        Path resolved = rootDir.resolve(path).normalize();
        if (!resolved.startsWith(rootDir)) {
            MainMod.LOGGER.warn("[FolderShaderPack] Blocked path traversal attempt to read: {}", path);
            return null;
        }
        if (!Files.isRegularFile(resolved)) {
            MainMod.LOGGER.debug("[FolderShaderPack] File not found when reading: {}", resolved);
            return null;
        }
        
        MainMod.LOGGER.debug("[FolderShaderPack] Reading file: {}", resolved);
        return Files.newInputStream(resolved);
    }

    @Override
    public boolean hasResource(String path) {
        Path resolved = rootDir.resolve(path).normalize();
        boolean exists = resolved.startsWith(rootDir) && Files.isRegularFile(resolved);
        if (!exists) {
            MainMod.LOGGER.debug("[FolderShaderPack] hasResource failed for: {}", resolved);
        }
        return exists;
    }

    @Override
    public void close() {
        MainMod.LOGGER.debug("Closed FolderShaderPack: {}", name);
    }
}
