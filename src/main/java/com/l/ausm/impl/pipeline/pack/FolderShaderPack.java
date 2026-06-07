package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class FolderShaderPack implements ShaderPack {

    private final Path rootDir;
    private final String name;
    private final Map<String, Boolean> resourceExistenceCache = new HashMap<>();
    private final Map<String, byte[]> resourceContentCache = new HashMap<>();

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

        byte[] bytes = resourceContentCache.get(path);
        if (bytes == null) {
            MainMod.LOGGER.debug("[FolderShaderPack] Reading file: {}", resolved);
            bytes = Files.readAllBytes(resolved);
            resourceContentCache.put(path, bytes);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public boolean hasResource(String path) {
        if (path == null) {
            return false;
        }
        return resourceExistenceCache.computeIfAbsent(path, this::hasResourceUncached);
    }

    @Override
    public void close() {
        MainMod.LOGGER.debug("Closed FolderShaderPack: {}", name);
    }

    private boolean hasResourceUncached(String path) {
        Path resolved = rootDir.resolve(path).normalize();
        return resolved.startsWith(rootDir) && Files.isRegularFile(resolved);
    }
}
