package com.l.ausm.impl.client;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Builds Entree + Euphoria and overlays AUSM's maintained Entree additions.
 * If the ordinary Complementary + Euphoria output is absent, a pinned binary
 * patch turns the supported Entree release directly into Euphoria's output.
 * The input and output tar hashes are checked so BSDiff is never applied to a
 * merely similar (and therefore unsafe) shader tree.
 */
public final class EuphoriaEntreePackGenerator {
    public static final String TARGET_PACK = "Complimentary Entree + EuphoriaPatches_1.9.3";

    private static final String EUPHORIA_PACK = "ComplementaryUnbound_r5.8.1 + EuphoriaPatches_1.9.3";
    private static final String ENTREE_PACK = "Complimentary Entree";
    private static final String OVERLAY_ROOT = "/assets/ausm/euphoria_entree_overlay/";
    private static final String MANIFEST = OVERLAY_ROOT + "manifest.txt";
    private static final String NATIVE_PATCH = "/assets/ausm/euphoria_entree.patch";
    private static final String NATIVE_ENTREE_TAR_SHA256 = "1025144fba3cebec88e39bc15231d17bb16430a4ddfbc6975c93c5140415a065";
    private static final String NATIVE_EUPHORIA_TAR_SHA256 = "bfb06adddad37b81f90101c3a5203f40742c375a22d94f5d85c3070820ee7c8c";
    private static final String EUPHORIA_MARKER = "// Euphoria Patches 1.9.3";
    private static final String GENERATOR_VERSION = "ausm-entree-euphoria-v6";
    private static final String MARKER_FILE = ".ausm-euphoria-entree-version";
    private static final String STAGING_NAME = ".ausm-entree-euphoria-staging";
    private static final String PREVIOUS_NAME = ".ausm-entree-euphoria-previous";
    private static final String WORK_NAME = ".ausm-entree-euphoria-work";
    private static final int RETRY_INTERVAL_TICKS = 20;
    private static final int MAX_RETRY_TICKS = 600;
    private static final Pattern BLOCK_MAPPING = Pattern.compile("^\\s*block\\.(\\d+)\\s*=.*$");
    private static final EuphoriaEntreePackGenerator INSTANCE = new EuphoriaEntreePackGenerator();

    private int ticks;
    private int failureLogs;
    private boolean optionsCopiedThisStartup;

    private EuphoriaEntreePackGenerator() {
    }

    public static void init() {
        generateNow();
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    /** Called by the Euphoria Patcher constructor compatibility hook. */
    public static void generateNow() {
        INSTANCE.generateIfNeeded();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        if (ticks % RETRY_INTERVAL_TICKS == 0) {
            generateIfNeeded();
        }
        if (ticks >= MAX_RETRY_TICKS) {
            FMLCommonHandler.instance().bus().unregister(this);
        }
    }

    private synchronized void generateIfNeeded() {
        // This generator is packaged after the normal Minecraft remap inputs;
        // calling the MCP name directly survives into the distributable JAR
        // and fails on the SRG-named 1.12 runtime. Use AUSM's cached mapping-
        // safe accessor, as the rest of the client pipeline does.
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        if (minecraft == null) {
            return;
        }

        Path gameDir = MinecraftReflectionCompat.gameDir(minecraft).toPath().toAbsolutePath().normalize();
        Path shaderpacks = gameDir.resolve("shaderpacks").normalize();
        Path euphoria = safeDirectChild(shaderpacks, EUPHORIA_PACK);
        Path entree = findPack(shaderpacks, ENTREE_PACK);
        Path target = safeDirectChild(shaderpacks, TARGET_PACK);
        if (entree == null) {
            return;
        }

        Path euphoriaProperties = euphoria.resolve("shaders/shaders.properties");
        String token;
        try {
            token = GENERATOR_VERSION + "\n"
                    + (Files.isDirectory(euphoria) ? "official:" + fingerprint(euphoriaProperties) : "native:" + NATIVE_EUPHORIA_TAR_SHA256) + "\n"
                    + fingerprintPack(entree) + "\n";
            if (Files.isDirectory(target)
                    && token.equals(readStringIfPresent(target.resolve(MARKER_FILE)))) {
                copyEntreeOptionsOnce(gameDir);
                return;
            }
            if (Files.isDirectory(euphoria)) {
                Path entreeBlocks = materializedPackFile(entree, "shaders/block.properties", shaderpacks);
                try {
                    generateFromPatchedPack(shaderpacks, euphoria, entreeBlocks, target, token);
                } finally {
                    cleanupMaterializedPackFile(entreeBlocks, shaderpacks);
                }
            } else {
                generateFromEntree(shaderpacks, entree, target, token);
            }
            copyEntreeOptionsOnce(gameDir);
            MainMod.LOGGER.info("[AUSMEuphoriaEntree] Generated startup shaderpack '{}' from '{}'", TARGET_PACK,
                    Files.isDirectory(euphoria) ? EUPHORIA_PACK : entree.getFileName());
        } catch (IOException | RuntimeException failure) {
            if (failureLogs++ < 4) {
                MainMod.LOGGER.warn("[AUSMEuphoriaEntree] Could not generate '{}' yet; startup retry remains active", TARGET_PACK, failure);
            }
        }
    }

    private void copyEntreeOptionsOnce(Path gameDir) throws IOException {
        if (!optionsCopiedThisStartup) {
            copyEntreeOptions(gameDir);
            optionsCopiedThisStartup = true;
        }
    }

    private static void generateFromPatchedPack(Path shaderpacks, Path euphoria, Path entreeBlocks, Path target, String token) throws IOException {
        Path staging = safeDirectChild(shaderpacks, STAGING_NAME);
        deleteTree(staging);
        copyTree(euphoria, staging);
        overlayBundledFiles(staging);
        patchReflectiveCaustics(staging);
        mergeEntreeBlockMappings(staging.resolve("shaders/block.properties"), entreeBlocks);
        Files.writeString(staging.resolve(MARKER_FILE), token, StandardCharsets.UTF_8);
        publish(shaderpacks, staging, target);
    }

    private static void generateFromEntree(Path shaderpacks, Path entree, Path target, String token) throws IOException {
        Path staging = safeDirectChild(shaderpacks, STAGING_NAME);
        Path work = safeDirectChild(shaderpacks, WORK_NAME);
        Path extractedEntree = work.resolve("entree");
        Path entreeTar = work.resolve("entree.tar");
        Path euphoriaTar = work.resolve("euphoria.tar");
        Path patch = work.resolve("entree-euphoria.patch");

        deleteTree(staging);
        deleteTree(work);
        Files.createDirectories(work);
        try {
            materializePack(entree, extractedEntree);
            invokeArchive(extractedEntree, entreeTar);
            requireSha256(entreeTar, NATIVE_ENTREE_TAR_SHA256, "Entree shader base");

            try (InputStream stream = requiredResource(NATIVE_PATCH)) {
                Files.copy(stream, patch, StandardCopyOption.REPLACE_EXISTING);
            }
            invokeBinaryPatch(entreeTar, euphoriaTar, patch);
            requireSha256(euphoriaTar, NATIVE_EUPHORIA_TAR_SHA256, "Euphoria shader result");
            invokeExtract(euphoriaTar, staging);
            requireEuphoriaMarker(staging);

            overlayBundledFiles(staging);
            patchReflectiveCaustics(staging);
            mergeEntreeBlockMappings(staging.resolve("shaders/block.properties"), extractedEntree.resolve("shaders/block.properties"));
            Files.writeString(staging.resolve(MARKER_FILE), token, StandardCharsets.UTF_8);
            publish(shaderpacks, staging, target);
        } finally {
            deleteTree(staging);
            deleteTree(work);
        }
    }

    private static void publish(Path shaderpacks, Path staging, Path target) throws IOException {
        Path previous = safeDirectChild(shaderpacks, PREVIOUS_NAME);
        deleteTree(previous);

        boolean parkedTarget = false;
        try {
            if (Files.exists(target)) {
                move(target, previous);
                parkedTarget = true;
            }
            move(staging, target);
            deleteTree(previous);
        } catch (IOException failure) {
            if (parkedTarget && !Files.exists(target) && Files.exists(previous)) {
                move(previous, target);
            }
            throw failure;
        } finally {
            deleteTree(staging);
        }
    }

    private static void invokeArchive(Path source, Path archive) throws IOException {
        invokePatcherMethod(
                "com.euphoriapatches.euphoria_patcher.io.ArchiveUtils",
                "archive",
                new Class<?>[]{Path.class, Path.class},
                source,
                archive
        );
    }

    private static void invokeExtract(Path archive, Path destination) throws IOException {
        invokePatcherMethod(
                "com.euphoriapatches.euphoria_patcher.io.ArchiveUtils",
                "extract",
                new Class<?>[]{Path.class, Path.class},
                archive,
                destination
        );
    }

    private static void invokeBinaryPatch(Path source, Path destination, Path patch) throws IOException {
        invokePatcherMethod(
                "com.euphoriapatches.shadow.io.sigpipe.jbsdiff.ui.FileUI",
                "patch",
                new Class<?>[]{File.class, File.class, File.class},
                source.toFile(),
                destination.toFile(),
                patch.toFile()
        );
    }

    private static void invokePatcherMethod(String className, String methodName, Class<?>[] parameterTypes, Object... arguments) throws IOException {
        try {
            Class<?> owner = Class.forName(className, true, EuphoriaEntreePackGenerator.class.getClassLoader());
            Method method = owner.getMethod(methodName, parameterTypes);
            method.invoke(null, arguments);
        } catch (ClassNotFoundException failure) {
            throw new IOException("Euphoria Patcher is required for Entree-native generation", failure);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("Euphoria Patcher " + methodName + " failed", cause);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IOException("Could not invoke Euphoria Patcher " + methodName, failure);
        }
    }

    private static void requireSha256(Path file, String expected, String description) throws IOException {
        String actual = sha256(file);
        if (!expected.equals(actual)) {
            throw new IOException(description + " is not the supported release (expected " + expected + ", got " + actual + ")");
        }
    }

    private static void requireEuphoriaMarker(Path root) throws IOException {
        Path marker = root.resolve("shaders/lib/misc/myFile.glsl");
        if (!Files.isRegularFile(marker) || !Files.readString(marker, StandardCharsets.UTF_8).startsWith(EUPHORIA_MARKER)) {
            throw new IOException("Patched Entree output is missing the Euphoria 1.9.3 marker");
        }
    }

    private static void overlayBundledFiles(Path staging) throws IOException {
        for (String relative : readManifest()) {
            Path destination = staging.resolve(relative).normalize();
            if (!destination.startsWith(staging)) {
                throw new IOException("Overlay entry escapes shaderpack root: " + relative);
            }
            Files.createDirectories(destination.getParent());
            try (InputStream stream = requiredResource(OVERLAY_ROOT + relative)) {
                Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void patchReflectiveCaustics(Path staging) throws IOException {
        Path mainLighting = staging.resolve("shaders/lib/lighting/mainLighting.glsl");
        String source = Files.readString(mainLighting, StandardCharsets.UTF_8).replace("\r\n", "\n");

        source = replaceExactlyOnce(
                source,
                "#if SHADOW_QUALITY > -1 && (defined OVERWORLD || defined END) && !defined DH_TERRAIN && !defined DH_WATER && !defined VOXY_PATCH\n"
                        + "    #include \"/lib/lighting/shadowSampling.glsl\"\n"
                        + "#endif\n",
                "#if SHADOW_QUALITY > -1 && (defined OVERWORLD || defined END) && !defined DH_TERRAIN && !defined DH_WATER && !defined VOXY_PATCH\n"
                        + "    #include \"/lib/lighting/shadowSampling.glsl\"\n"
                        + "    #ifdef REFLECTIVE_CAUSTICS\n"
                        + "        #include \"/lib/lighting/reflectiveCaustics.glsl\"\n"
                        + "    #endif\n"
                        + "#endif\n",
                "reflective-caustics include"
        );
        source = replaceExactlyOnce(
                source,
                "    vec3 sceneLighting = lightColorM * shadowLightMult + ambientColorM * ambientMult;\n",
                "    vec3 sceneLighting = lightColorM * shadowLightMult + ambientColorM * ambientMult;\n"
                        + "    #if defined REFLECTIVE_CAUSTICS && defined OVERWORLD && (defined GBUFFERS_TERRAIN || defined GBUFFERS_BLOCK)\n"
                        + "        sceneLighting += lightColorM * GetReflectiveCaustics(playerPos, worldGeoNormal, lightmap.y);\n"
                        + "    #endif\n",
                "reflective-caustics lighting hook"
        );

        Files.writeString(mainLighting, source, StandardCharsets.UTF_8);

        Path shadow = staging.resolve("shaders/program/shadow.glsl");
        source = Files.readString(shadow, StandardCharsets.UTF_8).replace("\r\n", "\n");
        source = replaceExactlyOnce(
                source,
                "                if (mat == 32000) { // Water\n"
                        + "                    vec3 worldPos = position.xyz + cameraPosition;\n",
                "                if (mat == 32000) { // Water\n"
                        + "                    #ifdef REFLECTIVE_CAUSTICS\n"
                        + "                        color1.a = 0.03125; // Water-only marker for reflected-light tracing\n"
                        + "                    #endif\n"
                        + "                    vec3 worldPos = position.xyz + cameraPosition;\n",
                "reflective-caustics water marker"
        );
        Files.writeString(shadow, source, StandardCharsets.UTF_8);
    }

    private static String replaceExactlyOnce(String source, String needle, String replacement, String description) throws IOException {
        int first = source.indexOf(needle);
        if (first < 0 || source.indexOf(needle, first + needle.length()) >= 0) {
            throw new IOException("Could not uniquely apply Entree " + description + " patch");
        }
        return source.substring(0, first) + replacement + source.substring(first + needle.length());
    }

    private static List<String> readManifest() throws IOException {
        List<String> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(requiredResource(MANIFEST), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String entry = line.trim();
                if (!entry.isEmpty() && !entry.startsWith("#")) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    private static InputStream requiredResource(String path) throws IOException {
        InputStream stream = EuphoriaEntreePackGenerator.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("Missing bundled Entree overlay resource " + path);
        }
        return stream;
    }

    private static void mergeEntreeBlockMappings(Path targetBlocks, Path entreeBlocks) throws IOException {
        if (!Files.isRegularFile(entreeBlocks) || !Files.isRegularFile(targetBlocks)) {
            return;
        }
        List<String> targetLines = Files.readAllLines(targetBlocks, StandardCharsets.UTF_8);
        Set<Integer> existingIds = new HashSet<>();
        collectBlockIds(targetLines, existingIds);

        List<String> additions = new ArrayList<>();
        for (String line : Files.readAllLines(entreeBlocks, StandardCharsets.UTF_8)) {
            Matcher matcher = BLOCK_MAPPING.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int id = Integer.parseInt(matcher.group(1));
            if (isEntreeCustomBlockId(id) && existingIds.add(id)) {
                additions.add(line);
            }
        }
        if (additions.isEmpty()) {
            return;
        }
        targetLines.add("");
        targetLines.add("# AUSM / MeatballCraft Entree overlay");
        targetLines.addAll(additions);
        Files.write(targetBlocks, targetLines, StandardCharsets.UTF_8);
    }

    private static void collectBlockIds(List<String> lines, Set<Integer> result) {
        for (String line : lines) {
            Matcher matcher = BLOCK_MAPPING.matcher(line);
            if (matcher.matches()) {
                result.add(Integer.parseInt(matcher.group(1)));
            }
        }
    }

    private static boolean isEntreeCustomBlockId(int id) {
        return id == 12003
                || id >= 12070 && id <= 12080
                || id == 12120
                || id == 12130
                || id >= 12141 && id <= 12197
                || id >= 12270 && id <= 12283
                || id >= 32620 && id <= 32645;
    }

    private static void copyEntreeOptions(Path gameDir) throws IOException {
        Path options = gameDir.resolve("config/ausm/shader-options");
        Path source = options.resolve("Complimentary_Entree.properties");
        Path destination = options.resolve("Complimentary_Entree___EuphoriaPatches_1.9.3.properties");
        if (Files.isRegularFile(source)) {
            Files.createDirectories(options);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String fingerprint(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return "missing:" + file.getFileName();
        }
        return file.getFileName() + ":" + Files.size(file) + ":" + Files.getLastModifiedTime(file).toMillis();
    }

    private static String fingerprintPack(Path pack) throws IOException {
        if (Files.isRegularFile(pack)) {
            return pack.getFileName() + ":sha256:" + sha256(pack);
        }
        return pack.getFileName() + ":"
                + fingerprint(pack.resolve("shaders/lib/common.glsl")) + ":"
                + fingerprint(pack.resolve("shaders/block.properties"));
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    private static String readStringIfPresent(Path file) throws IOException {
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    private static Path safeDirectChild(Path root, String name) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path child = normalizedRoot.resolve(name).normalize();
        if (!normalizedRoot.equals(child.getParent())) {
            throw new IllegalArgumentException("Unsafe generated shaderpack path: " + child);
        }
        return child;
    }

    private static Path findPack(Path shaderpacks, String name) {
        Path directory = safeDirectChild(shaderpacks, name);
        if (Files.isDirectory(directory)) {
            return directory;
        }
        Path archive = safeDirectChild(shaderpacks, name + ".zip");
        if (Files.isRegularFile(archive)) {
            return archive;
        }
        return null;
    }

    private static Path materializedPackFile(Path pack, String relative, Path shaderpacks) throws IOException {
        if (Files.isDirectory(pack)) {
            return pack.resolve(relative);
        }
        Path work = safeDirectChild(shaderpacks, WORK_NAME);
        deleteTree(work);
        Path extracted = work.resolve("entree");
        materializePack(pack, extracted);
        return extracted.resolve(relative);
    }

    private static void cleanupMaterializedPackFile(Path materialized, Path shaderpacks) throws IOException {
        Path work = safeDirectChild(shaderpacks, WORK_NAME);
        if (materialized != null && materialized.toAbsolutePath().normalize().startsWith(work)) {
            deleteTree(work);
        }
    }

    private static void materializePack(Path pack, Path destination) throws IOException {
        if (Files.isDirectory(pack)) {
            copyTree(pack, destination);
            return;
        }
        extractZip(pack, destination);
    }

    private static void extractZip(Path archive, Path destination) throws IOException {
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        Files.createDirectories(normalizedDestination);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                while (entryName.startsWith("/")) {
                    entryName = entryName.substring(1);
                }
                if (entryName.isEmpty()) {
                    continue;
                }
                Path output = normalizedDestination.resolve(entryName).normalize();
                if (!output.startsWith(normalizedDestination)) {
                    throw new IOException("Shaderpack archive entry escapes extraction root: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }
}
