package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZipShaderPack implements ShaderPack {

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
    public InputStream getResourceAsStream(String path) throws IOException {
        // Zip file system roots are absolute, we need to ensure the path doesn't start with /
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        Path resolved = zipFileSystem.getPath(normalizedPath).normalize();
        
        if (!Files.isRegularFile(resolved)) {
            MainMod.LOGGER.debug("[ZipShaderPack] File not found: {}", path);
            return null;
        }
        
        MainMod.LOGGER.debug("[ZipShaderPack] Reading file: {}", path);
        return Files.newInputStream(resolved);
    }

    @Override
    public boolean hasResource(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        Path resolved = zipFileSystem.getPath(normalizedPath).normalize();
        return Files.isRegularFile(resolved);
    }

    @Override
    public void close() throws IOException {
        if (zipFileSystem != null && zipFileSystem.isOpen()) {
            zipFileSystem.close();
            MainMod.LOGGER.debug("Closed ZipShaderPack: {}", name);
        }
    }
}