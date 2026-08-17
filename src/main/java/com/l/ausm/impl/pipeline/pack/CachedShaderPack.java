package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

abstract class CachedShaderPack implements ShaderPack {
    private final Map<String, Boolean> resourceExistenceCache = new HashMap<>();
    private final Map<String, byte[]> resourceContentCache = new HashMap<>();

    @Override
    public final InputStream getResourceAsStream(String path) throws IOException {
        if (path == null) {
            return null;
        }
        Path resolved = resolveResourcePath(path, true);
        if (resolved == null) {
            return null;
        }
        if (!Files.isRegularFile(resolved)) {
            logMissingResource(path, resolved);
            return null;
        }

        byte[] bytes = resourceContentCache.get(path);
        if (bytes == null) {
            logReadResource(path, resolved);
            bytes = Files.readAllBytes(resolved);
            resourceContentCache.put(path, bytes);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public final boolean hasResource(String path) {
        if (path == null) {
            return false;
        }
        Boolean cached = resourceExistenceCache.get(path);
        if (cached != null) {
            return cached;
        }
        Path resolved = resolveResourcePath(path, false);
        boolean exists = resolved != null && Files.isRegularFile(resolved);
        resourceExistenceCache.put(path, exists);
        return exists;
    }

    protected abstract Path resolveResourcePath(String path, boolean logFailures);

    protected abstract String logPrefix();

    protected void logMissingResource(String path, Path resolved) {
        MainMod.LOGGER.debug("[{}] File not found: {}", logPrefix(), path);
    }

    protected void logReadResource(String path, Path resolved) {
        MainMod.LOGGER.debug("[{}] Reading file: {}", logPrefix(), path);
    }
}
