package com.l.ausm.impl.pipeline.bloom;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.l.ausm.impl.MainMod;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

public final class AusmBloomResourceIndex {
    private final AtomicBoolean scanned = new AtomicBoolean();
    private int scannedArchives;
    private int scannedDirectories;
    private int glowTextures;
    private int emissiveTextures;
    private int bloomTextures;
    private int bloomMetadata;
    private int ctmBloomMetadata;
    private int declaredLightMetadata;

    public void scanOnce() {
        if (!scanned.compareAndSet(false, true)) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        File runDir = mc != null ? mc.gameDir : new File(System.getProperty("user.dir", "."));
        scanPackRoot(new File(runDir, "resourcepacks"));
        scanPackRoot(new File(runDir, "mods"));

        MainMod.LOGGER.info(
                "[AUSMBloom] Resource scan complete: archives={} directories={} glow={} emissive={} bloom={} bloomMeta={} ctmBloomMeta={} declaredLights={}",
                scannedArchives,
                scannedDirectories,
                glowTextures,
                emissiveTextures,
                bloomTextures,
                bloomMetadata,
                ctmBloomMetadata,
                declaredLightMetadata
        );
    }

    public boolean hasBloomResources() {
        return glowTextures > 0 || emissiveTextures > 0 || bloomTextures > 0 || ctmBloomMetadata > 0;
    }

    private void scanPackRoot(File root) {
        if (root == null || !root.isDirectory()) {
            return;
        }

        File[] files = root.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String lowerName = file.getName().toLowerCase(Locale.ROOT);
            if (file.isDirectory()) {
                scanDirectoryPack(file.toPath());
            } else if (lowerName.endsWith(".zip") || lowerName.endsWith(".jar")) {
                scanArchivePack(file);
            }
        }
    }

    private void scanArchivePack(File file) {
        try (JarFile jar = new JarFile(file)) {
            scannedArchives++;
            jar.stream().forEach(entry -> scanArchiveEntry(jar, entry));
        } catch (IOException | RuntimeException error) {
            MainMod.LOGGER.debug("[AUSMBloom] Skipping unreadable resource archive {}", file, error);
        }
    }

    private void scanArchiveEntry(JarFile jar, ZipEntry entry) {
        if (entry == null || entry.isDirectory()) {
            return;
        }

        String name = normalizePath(entry.getName());
        scanTexturePath(name);
        if (!isMetadataPath(name)) {
            return;
        }

        try (InputStream stream = jar.getInputStream(entry)) {
            scanMetadataPath(name, stream);
        } catch (IOException | RuntimeException error) {
            MainMod.LOGGER.debug("[AUSMBloom] Failed to parse bloom metadata {}", name, error);
        }
    }

    private void scanDirectoryPack(Path root) {
        scannedDirectories++;
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(path -> scanDirectoryEntry(root, path));
        } catch (IOException | RuntimeException error) {
            MainMod.LOGGER.debug("[AUSMBloom] Skipping unreadable resource directory {}", root, error);
        }
    }

    private void scanDirectoryEntry(Path root, Path path) {
        String name = normalizePath(root.relativize(path).toString());
        scanTexturePath(name);
        if (!isMetadataPath(name)) {
            return;
        }

        try (InputStream stream = Files.newInputStream(path)) {
            scanMetadataPath(name, stream);
        } catch (IOException | RuntimeException error) {
            MainMod.LOGGER.debug("[AUSMBloom] Failed to parse bloom metadata {}", name, error);
        }
    }

    private void scanTexturePath(String path) {
        if (!path.startsWith("assets/") || !path.contains("/textures/") || !path.endsWith(".png")) {
            return;
        }

        String withoutExtension = path.substring(0, path.length() - ".png".length()).toLowerCase(Locale.ROOT);
        if (withoutExtension.endsWith("_glow")) {
            glowTextures++;
        } else if (withoutExtension.endsWith("_emissive")) {
            emissiveTextures++;
        } else if (withoutExtension.endsWith("_bloom")) {
            bloomTextures++;
        }
    }

    private void scanMetadataPath(String path, InputStream stream) {
        if (stream == null) {
            return;
        }

        bloomMetadata++;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject()) {
                return;
            }

            JsonObject object = root.getAsJsonObject();
            JsonObject ctm = object.has("ctm") && object.get("ctm").isJsonObject()
                    ? object.getAsJsonObject("ctm")
                    : null;
            if (ctm == null) {
                return;
            }

            if (ctm.has("layer") && "BLOOM".equalsIgnoreCase(ctm.get("layer").getAsString())) {
                ctmBloomMetadata++;
            }

            JsonObject extra = ctm.has("extra") && ctm.get("extra").isJsonObject()
                    ? ctm.getAsJsonObject("extra")
                    : null;
            if (extra != null && extra.has("light")) {
                declaredLightMetadata++;
            }
        } catch (IOException | RuntimeException error) {
            MainMod.LOGGER.debug("[AUSMBloom] Invalid bloom metadata {}", path, error);
        }
    }

    private static boolean isMetadataPath(String path) {
        return path.startsWith("assets/")
                && path.contains("/textures/")
                && path.endsWith(".png.mcmeta");
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/');
    }
}
