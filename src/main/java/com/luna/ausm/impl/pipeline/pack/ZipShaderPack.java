package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.impl.MainMod;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public final class ZipShaderPack extends CachedShaderPack {

    private final Path zipPath;
    private final String name;
    private final FileSystem zipFileSystem;

    public ZipShaderPack(Path zipPath) throws IOException {
        this.zipPath = zipPath;
        this.name = zipPath.getFileName().toString();
        // Create a new FileSystem for the zip file.
        // The context class loader handles standard zip files properly.
        this.zipFileSystem = FileSystems.newFileSystem(zipPath, (ClassLoader) null);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    protected Path resolveResourcePath(String path, boolean logFailures) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return zipFileSystem.getPath(normalizedPath).normalize();
    }

    @Override
    protected String logPrefix() {
        return "ZipShaderPack";
    }

    @Override
    public void close() throws IOException {
        if (zipFileSystem != null && zipFileSystem.isOpen()) {
            zipFileSystem.close();
            MainMod.LOGGER.debug("Closed ZipShaderPack: {}", name);
        }
    }
}
