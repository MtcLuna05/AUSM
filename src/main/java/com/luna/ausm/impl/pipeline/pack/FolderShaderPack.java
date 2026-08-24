package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.impl.MainMod;
import java.nio.file.Path;

public final class FolderShaderPack extends CachedShaderPack {

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
    protected Path resolveResourcePath(String path, boolean logFailures) {
        Path resolved = rootDir.resolve(path).normalize();
        if (!resolved.startsWith(rootDir)) {
            if (logFailures) {
                MainMod.LOGGER.warn("[FolderShaderPack] Blocked path traversal attempt to read: {}", path);
            }
            return null;
        }
        return resolved;
    }

    @Override
    protected String logPrefix() {
        return "FolderShaderPack";
    }

    @Override
    protected void logMissingResource(String path, Path resolved) {
        MainMod.LOGGER.debug("[FolderShaderPack] File not found when reading: {}", resolved);
    }

    @Override
    protected void logReadResource(String path, Path resolved) {
        MainMod.LOGGER.debug("[FolderShaderPack] Reading file: {}", resolved);
    }

    @Override
    public void close() {
        MainMod.LOGGER.debug("Closed FolderShaderPack: {}", name);
    }
}
