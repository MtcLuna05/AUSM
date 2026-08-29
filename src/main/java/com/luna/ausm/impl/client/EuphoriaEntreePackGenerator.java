package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

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
    private static final String EUPHORIA_PATCH_SUFFIX = " + EuphoriaPatches_1.9.3";
    private static final String ENTREE_PACK = "Complimentary Entree";
    private static final String OVERLAY_ROOT = "/assets/ausm/euphoria_entree_overlay/";
    private static final String MANIFEST = OVERLAY_ROOT + "manifest.txt";
    private static final String NATIVE_PATCH = "/assets/ausm/euphoria_entree.patch";
    private static final String NATIVE_ENTREE_TAR_SHA256 = "1025144fba3cebec88e39bc15231d17bb16430a4ddfbc6975c93c5140415a065";
    private static final String NATIVE_EUPHORIA_TAR_SHA256 = "bfb06adddad37b81f90101c3a5203f40742c375a22d94f5d85c3070820ee7c8c";
    private static final String EUPHORIA_MARKER = "// Euphoria Patches 1.9.3";
    private static final String GENERATOR_VERSION = "ausm-entree-euphoria-v10";
    private static final String MARKER_FILE = ".ausm-euphoria-entree-version";
    private static final String STAGING_NAME = ".ausm-entree-euphoria-staging";
    private static final String PREVIOUS_NAME = ".ausm-entree-euphoria-previous";
    private static final String WORK_NAME = ".ausm-entree-euphoria-work";
    private static final String AUSM_112_PATCH_SUFFIX = " + AUSM 1.12.2 Patches";
    private static final String AUSM_112_PATCH_MARKER = ".ausm-1.12.2-patches-version";
    private static final String AUSM_112_PATCH_VERSION = "ausm-1.12.2-patches-v14";
    private static final String LOD_API_PROPERTY = "ausm.lod.api=1";
    private static final String LOD_HELPER = "shaders/lib/ausm/distantLod.glsl";
    private static final String LOD_HELPER_INCLUDE = "#include \"/lib/ausm/distantLod.glsl\"";
    private static final String VOLUMETRIC_LIGHT_LIBRARY = "shaders/lib/atmospherics/volumetricLight/volumetricLight.glsl";
    private static final String PIXELATION_LIBRARY = "shaders/lib/misc/pixelation.glsl";
    private static final String FXAA_LIBRARY = "shaders/lib/antialiasing/fxaa.glsl";
    private static final int RETRY_INTERVAL_TICKS = 20;
    private static final int MAX_RETRY_TICKS = 600;
    private static final Pattern BLOCK_MAPPING = Pattern.compile("^\\s*block\\.(\\d+)\\s*=.*$");
    private static final Pattern ROOT_OPTIONS_SCREEN = Pattern.compile("(?m)^(\\s*screen=)([^\\r\\n]*)");
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

    /**
     * Called by the Euphoria Patcher constructor compatibility hook.
     */
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
        try {
            generateAUSM112PatchPacks(shaderpacks);
            injectAUSM112LodSupport(shaderpacks);
        } catch (IOException | RuntimeException failure) {
            if (failureLogs++ < 4) {
                MainMod.LOGGER.warn("[AUSM112Lod] Could not inject shader LOD support yet; startup retry remains active", failure);
            }
        }
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
        injectAUSM112LodSupportInto(staging);
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
            injectAUSM112LodSupportInto(staging);
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
        overlayBundledFiles(staging, true);
    }

    private static void overlayBundledFiles(Path staging, boolean overwriteCommonLibrary) throws IOException {
        for (String relative : readManifest()) {
            if (!overwriteCommonLibrary && relative.equals("shaders/lib/common.glsl")) {
                continue;
            }
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

    /**
     * Keeps generated 1.12.2 patch folders compatible with AUSM's distance-LOD
     * contract. Older generated folders predate the helper or only patch one
     * shader stage; repair those folders in place without touching source packs.
     */
    private static void injectAUSM112LodSupport(Path shaderpacks) throws IOException {
        if (!Files.isDirectory(shaderpacks)) {
            return;
        }
        List<Path> patchPacks;
        try (Stream<Path> entries = Files.list(shaderpacks)) {
            patchPacks = entries
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().endsWith(AUSM_112_PATCH_SUFFIX))
                    .toList();
        }
        for (Path patchPack : patchPacks) {
            injectAUSM112LodSupportInto(patchPack);
        }
    }

    /**
     * Euphoria creates its patched directories, while plain Complementary
     * Unbound and Reimagined packs are direct sources. In every case AUSM
     * needs a second, 1.12.2-specific derivative so a fresh instance does
     * not depend on a preexisting pack.
     */
    private static void generateAUSM112PatchPacks(Path shaderpacks) throws IOException {
        if (!Files.isDirectory(shaderpacks)) {
            return;
        }
        List<Path> sourcePacks;
        try (Stream<Path> entries = Files.list(shaderpacks)) {
            sourcePacks = entries
                    .filter(path -> Files.isDirectory(path)
                            || (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".zip")))
                    .filter(path -> isAUSM112PatchSource(path.getFileName().toString()))
                    .toList();
        }
        for (Path sourcePack : sourcePacks) {
            String sourceName = sourcePack.getFileName().toString();
            String sourcePackName = withoutZipExtension(sourceName);
            Path target = safeDirectChild(shaderpacks, sourcePackName + AUSM_112_PATCH_SUFFIX);
            String token = AUSM_112_PATCH_VERSION + "\n" + fingerprintPack(sourcePack) + "\n";
            if (Files.isDirectory(target)) {
                Path marker = target.resolve(AUSM_112_PATCH_MARKER);
                if (!Files.isRegularFile(marker)) {
                    MainMod.LOGGER.warn("[AUSM112] Leaving non-AUSM shaderpack '{}' untouched", target.getFileName());
                    continue;
                }
                if (token.equals(readStringIfPresent(marker))) {
                    continue;
                }
            }

            Path staging = safeDirectChild(shaderpacks, STAGING_NAME);
            deleteTree(staging);
            materializePack(sourcePack, staging);
            // Complementary's current common.glsl owns its complete option surface.
            // The bundled Entree-era copy is intentionally retained only for the
            // Euphoria/Entree generator paths above; replacing it here deletes
            // modern water, cloud, and material options before preprocessing.
            overlayBundledFiles(staging, false);
            injectAUSM112LodSupportInto(staging);
            Files.writeString(staging.resolve(AUSM_112_PATCH_MARKER), token, StandardCharsets.UTF_8);
            publish(shaderpacks, staging, target);
            MainMod.LOGGER.info("[AUSM112] Generated shaderpack '{}' from '{}'", target.getFileName(), sourceName);
        }
    }

    static boolean isAUSM112PatchSource(String name) {
        String packName = withoutZipExtension(name);
        return !packName.endsWith(AUSM_112_PATCH_SUFFIX)
                && (packName.endsWith(EUPHORIA_PATCH_SUFFIX)
                || packName.startsWith("ComplementaryUnbound")
                || packName.startsWith("ComplementaryReimagined"));
    }

    private static String withoutZipExtension(String name) {
        return name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }

    private static void injectAUSM112LodSupportInto(Path patchPack) throws IOException {
        Path properties = patchPack.resolve("shaders/shaders.properties");
        ensureLodApiDeclaration(properties);
        ensureAUSMOptionsCategory(properties, patchPack.resolve("shaders/lang/en_US.lang"));
        ensureBundledLodHelper(patchPack.resolve(LOD_HELPER));
        ensureStablePixelationLibrary(patchPack.resolve(PIXELATION_LIBRARY));
        ensureDriverSafeFxaaLibrary(patchPack.resolve(FXAA_LIBRARY));
        ensureClampedVolumetricLight(patchPack.resolve(VOLUMETRIC_LIGHT_LIBRARY));
        EuphoriaEntreeLodPatches.inject(patchPack);
        injectLodHelperIntoProgram(patchPack.resolve("shaders/program/gbuffers_terrain.glsl"));
        injectLodHelperIntoProgram(patchPack.resolve("shaders/program/gbuffers_water.glsl"));
    }

    private static void ensureLodApiDeclaration(Path properties) throws IOException {
        if (!Files.isRegularFile(properties)) {
            return;
        }
        String source = Files.readString(properties, StandardCharsets.UTF_8);
        if (source.lines().anyMatch(line -> line.trim().equals(LOD_API_PROPERTY))) {
            return;
        }
        String declaration = "# AUSM shader LOD API; generated patches opt in even when the upstream pack does not.\n"
                + LOD_API_PROPERTY + "\n\n";
        Files.writeString(properties, declaration + source, StandardCharsets.UTF_8);
    }

    private static void ensureAUSMOptionsCategory(Path properties, Path languageFile) throws IOException {
        if (!Files.isRegularFile(properties)) {
            return;
        }
        String source = Files.readString(properties, StandardCharsets.UTF_8);
        if (!source.contains("screen.AUSM_SETTINGS=")) {
            source = source.replaceFirst(
                    "(?m)^(\\s*screen=.*?)(\\R)",
                    "$1 [AUSM_SETTINGS]$2"
            );
            source += "\n# AUSM-generated compatibility options\n"
                    + "screen.AUSM_SETTINGS=<empty> <empty> [AUSM_LOD_SETTINGS]\n"
                    + "screen.AUSM_LOD_SETTINGS=<empty> <empty> AUSM_LOD_FALLBACK\n";
        } else if (!source.contains("screen.AUSM_LOD_SETTINGS=")) {
            source = source.replace(
                    "screen.AUSM_SETTINGS=<empty> <empty> [AUSM_MODDED_DIMENSIONS]",
                    "screen.AUSM_SETTINGS=<empty> <empty> [AUSM_MODDED_DIMENSIONS] [AUSM_LOD_SETTINGS]"
            );
            source += "\nscreen.AUSM_LOD_SETTINGS=<empty> <empty> AUSM_LOD_FALLBACK\n";
        }
        source = source.replace("[AUSM_FLUID_SETTINGS]", "");
        source = source.replaceAll("(?m)^\\s*screen\\.AUSM_FLUID_SETTINGS=.*(?:\\R|$)", "");
        source = source.replace("[AUSM_CAUSTICS_SETTINGS]", "");
        source = source.replaceAll("(?m)^\\s*screen\\.AUSM_CAUSTICS_SETTINGS=.*(?:\\R|$)", "");
        source = placeAUSMSettingsAtTop(source);
        if (!source.contains("screen.AUSM_LOD_SETTINGS=")) {
            return;
        }
        Files.writeString(properties, source, StandardCharsets.UTF_8);
        if (!Files.isRegularFile(languageFile)) {
            return;
        }
        String language = Files.readString(languageFile, StandardCharsets.UTF_8);
        if (!language.contains("screen.AUSM_LOD_SETTINGS=")) {
            String labels = "\n# AUSM-generated compatibility options\n";
            if (!language.contains("screen.AUSM_SETTINGS=")) {
                labels += "screen.AUSM_SETTINGS=§bAUSM Patches§r\n"
                        + "screen.AUSM_SETTINGS.comment=Compatibility options generated by AUSM.\n";
            }
            Files.writeString(languageFile, language + labels
                    + "screen.AUSM_LOD_SETTINGS=Distance LOD\n"
                    + "option.AUSM_LOD_FALLBACK=Distance LOD fallback\n"
                    + "option.AUSM_LOD_FALLBACK.comment=Adjust distant terrain and water detail for AUSM's LOD ranges.\n",
                    StandardCharsets.UTF_8);
        }
        String updatedLanguage = Files.readString(languageFile, StandardCharsets.UTF_8)
                .replaceAll("(?m)^screen\\.AUSM_FLUID_SETTINGS=.*(?:\\R|$)", "")
                .replaceAll("(?m)^option\\.AUSM_CURVED_FLUID_SURFACES=.*(?:\\R|$)", "")
                .replaceAll("(?m)^option\\.AUSM_CURVED_FLUID_SURFACES\\.comment=.*(?:\\R|$)", "")
                .replaceAll("(?m)^screen\\.AUSM_CAUSTICS_SETTINGS=.*(?:\\R|$)", "")
                .replaceAll("(?m)^screen\\.AUSM_CAUSTICS_SETTINGS\\.comment=.*(?:\\R|$)", "")
                .replaceAll("(?m)^option\\.REFLECTIVE_CAUSTICS=.*(?:\\R|$)", "")
                .replaceAll("(?m)^option\\.REFLECTIVE_CAUSTICS\\.comment=.*(?:\\R|$)", "");
        if (!updatedLanguage.equals(Files.readString(languageFile, StandardCharsets.UTF_8))) {
            Files.writeString(languageFile, updatedLanguage, StandardCharsets.UTF_8);
        }
    }

    private static String placeAUSMSettingsAtTop(String source) {
        Matcher rootScreen = ROOT_OPTIONS_SCREEN.matcher(source);
        if (!rootScreen.find()) {
            return source;
        }
        String entries = rootScreen.group(2)
                .replace("[AUSM_SETTINGS]", "")
                .replace("[EUPHORIA_SETTINGS]", "")
                .trim()
                .replaceAll("\\s+", " ");
        String leadingButtons = source.contains("screen.EUPHORIA_SETTINGS=")
                ? "[EUPHORIA_SETTINGS] [AUSM_SETTINGS]"
                : "[AUSM_SETTINGS]";
        String replacement = rootScreen.group(1) + leadingButtons
                + (entries.isEmpty() ? "" : " " + entries);
        return source.substring(0, rootScreen.start()) + replacement + source.substring(rootScreen.end());
    }

    private static void ensureBundledLodHelper(Path helper) throws IOException {
        Files.createDirectories(helper.getParent());
        try (InputStream stream = requiredResource(OVERLAY_ROOT + LOD_HELPER)) {
            Files.copy(stream, helper, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void ensureStablePixelationLibrary(Path pixelationLibrary) throws IOException {
        if (!Files.isRegularFile(pixelationLibrary)) {
            return;
        }
        try (InputStream stream = requiredResource(OVERLAY_ROOT + PIXELATION_LIBRARY)) {
            Files.copy(stream, pixelationLibrary, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureDriverSafeFxaaLibrary(Path fxaaLibrary) throws IOException {
        if (!Files.isRegularFile(fxaaLibrary)) {
            return;
        }
        String source = Files.readString(fxaaLibrary, StandardCharsets.UTF_8);
        String helper = """
                // AUSM: framebuffer reads outside its bounds are undefined. Clamp all FXAA samples.
                // FXAA branches per pixel, so use explicit base-level sampling rather than
                // implicit derivatives, which are undefined in divergent control flow.
                ivec2 FXAAClampPixel(ivec2 pixel) {
                    return clamp(pixel, ivec2(0), ivec2(viewWidth - 1, viewHeight - 1));
                }

                vec2 FXAASafeTexelUv(ivec2 pixel) {
                    return (vec2(FXAAClampPixel(pixel)) + 0.5) / vec2(viewWidth, viewHeight);
                }

                vec2 FXAASafeUv(vec2 uv) {
                    vec2 halfPixel = 0.5 / vec2(viewWidth, viewHeight);
                    return clamp(uv, halfPixel, vec2(1.0) - halfPixel);
                }

                """;
        String patched = source;
        if (!patched.contains("FXAAClampPixel")) {
            patched = patched.replace("void FXAA311(inout vec3 color) {", helper + "void FXAA311(inout vec3 color) {");
        }
        patched = patched
                .replace("texelFetch(colortex3, texelCoord + ivec2( 0, -1), 0).rgb", "texture2D(colortex3, FXAASafeTexelUv(texelCoord + ivec2( 0, -1))).rgb")
                .replace("texelFetch(colortex3, texelCoord + ivec2( 0,  1), 0).rgb", "texture2D(colortex3, FXAASafeTexelUv(texelCoord + ivec2( 0,  1))).rgb")
                .replace("texelFetch(colortex3, texelCoord + ivec2(-1,  0), 0).rgb", "texture2D(colortex3, FXAASafeTexelUv(texelCoord + ivec2(-1,  0))).rgb")
                .replace("texelFetch(colortex3, texelCoord + ivec2( 1,  0), 0).rgb", "texture2D(colortex3, FXAASafeTexelUv(texelCoord + ivec2( 1,  0))).rgb")
                .replace("texelFetch(colortex3, texelCoord + ivec2(-1, -1), 0).rgb", "texture2D(colortex3, FXAASafeTexelUv(texelCoord + ivec2(-1, -1))).rgb")
                .replace("texelFetch(colortex3, texelCoord + ivec2( 1,  1), 0).rgb", "texture2D(colortex3, FXAASafeTexelUv(texelCoord + ivec2( 1,  1))).rgb")
                .replace("texelFetch(colortex3, texelCoord + ivec2(-1,  1), 0).rgb", "texture2D(colortex3, FXAASafeTexelUv(texelCoord + ivec2(-1,  1))).rgb")
                .replace("texelFetch(colortex3, texelCoord + ivec2( 1, -1), 0).rgb", "texture2D(colortex3, FXAASafeTexelUv(texelCoord + ivec2( 1, -1))).rgb")
                .replace("texture2D(colortex3, uv1)", "texture2D(colortex3, FXAASafeUv(uv1))")
                .replace("texture2D(colortex3, uv2)", "texture2D(colortex3, FXAASafeUv(uv2))")
                .replace("texture2D(colortex3, finalUv)", "texture2D(colortex3, FXAASafeUv(finalUv))")
                .replace("texelFetch(depthtex0, texelCoord, 0).r", "texture2D(depthtex0, FXAASafeTexelUv(texelCoord)).r")
                .replace("texelFetch(depthtex1, texelCoord, 0).r", "texture2D(depthtex1, FXAASafeTexelUv(texelCoord)).r")
                .replace("texelFetch(depthtex0, texelCoordM, 0).r", "texture2D(depthtex0, FXAASafeTexelUv(texelCoordM)).r")
                .replace("texelFetch(depthtex1, texelCoordM, 0).r", "texture2D(depthtex1, FXAASafeTexelUv(texelCoordM)).r")
                .replace("texelFetch(colortex2, texelCoord, 0).rgb", "texture2D(colortex2, FXAASafeTexelUv(texelCoord)).rgb");
        String[] explicitLodSamples = {
                "colortex3, FXAASafeTexelUv(texelCoord + ivec2( 0, -1))",
                "colortex3, FXAASafeTexelUv(texelCoord + ivec2( 0,  1))",
                "colortex3, FXAASafeTexelUv(texelCoord + ivec2(-1,  0))",
                "colortex3, FXAASafeTexelUv(texelCoord + ivec2( 1,  0))",
                "colortex3, FXAASafeTexelUv(texelCoord + ivec2(-1, -1))",
                "colortex3, FXAASafeTexelUv(texelCoord + ivec2( 1,  1))",
                "colortex3, FXAASafeTexelUv(texelCoord + ivec2(-1,  1))",
                "colortex3, FXAASafeTexelUv(texelCoord + ivec2( 1, -1))",
                "colortex3, FXAASafeUv(uv1)", "colortex3, FXAASafeUv(uv2)", "colortex3, FXAASafeUv(finalUv)",
                "depthtex0, FXAASafeTexelUv(texelCoord)", "depthtex1, FXAASafeTexelUv(texelCoord)",
                "depthtex0, FXAASafeTexelUv(texelCoordM)", "depthtex1, FXAASafeTexelUv(texelCoordM)",
                "colortex2, FXAASafeTexelUv(texelCoord)"
        };
        for (String sample : explicitLodSamples) {
            patched = patched.replace("texture2D(" + sample + ")", "texture2DLod(" + sample + ", 0.0)");
        }
        if (!patched.contains("FXAAClampPixel")) {
            MainMod.LOGGER.warn("[AUSM] Could not apply the driver-safe FXAA patch; keeping the upstream shader");
            return;
        }
        if (patched.equals(source)) {
            return;
        }
        Files.writeString(fxaaLibrary, patched, StandardCharsets.UTF_8);
        MainMod.LOGGER.info("[AUSM] Applied clamped, explicit-LOD FXAA sampling");
    }

    private static void ensureClampedVolumetricLight(Path volumetricLightLibrary) throws IOException {
        if (!Files.isRegularFile(volumetricLightLibrary)) {
            return;
        }
        String source = Files.readString(volumetricLightLibrary, StandardCharsets.UTF_8);
        if (source.contains("AUSM_CLAMPED_VOLUMETRIC_LIGHT")) {
            return;
        }
        String anchor = "    volumetricLight.rgb *= vlMult;";
        if (!source.contains(anchor)) {
            return;
        }
        String patch = anchor + "\n\n"
                + "    // AUSM_CLAMPED_VOLUMETRIC_LIGHT: malformed 1.12 shadow-color\n"
                + "    // samples must not become unbounded in-scattering radiance.\n"
                + "    volumetricLight.rgb = clamp(volumetricLight.rgb, vec3(0.0), vec3(1.0));";
        Files.writeString(volumetricLightLibrary, source.replace(anchor, patch), StandardCharsets.UTF_8);
    }

    private static void injectLodHelperIntoProgram(Path program) throws IOException {
        if (!Files.isRegularFile(program)) {
            return;
        }
        String source = Files.readString(program, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String original = source;
        String marker = "//Program//";
        if (!source.contains(LOD_HELPER_INCLUDE) && !source.contains(marker)) {
            MainMod.LOGGER.warn("[AUSM112Lod] Skipping {}: no shader program marker", program.getFileName());
            return;
        }
        if (!source.contains(LOD_HELPER_INCLUDE)) {
            source = source.replace(marker, LOD_HELPER_INCLUDE + "\n\n" + marker);
        }
        source = injectLodFallbackUsage(program.getFileName().toString(), source);
        if (!original.equals(source)) {
            Files.writeString(program, source, StandardCharsets.UTF_8);
            MainMod.LOGGER.info("[AUSM112Lod] Injected custom LOD support into {}", program.getFileName());
        }
    }

    static String injectLodFallbackUsage(String programName, String source) {
        if ("gbuffers_terrain.glsl".equals(programName)) {
            source = source.replace(
                    "float ausmTerrainWaveWeight = ausmEntreeDetailWeight(position.xyz);",
                    "float ausmTerrainWaveWeight = ausmEntreeFoliageWaveWeight(position.xyz);"
            );
            source = source.replace(
                    "playerPosM = mix(ausmInteractivePosition, playerPosM, ausmEntreeDetailWeight(position.xyz));",
                    "playerPosM = mix(ausmInteractivePosition, playerPosM, ausmEntreeFoliageWaveWeight(position.xyz));"
            );
        }
        if ("gbuffers_terrain.glsl".equals(programName) && !source.contains("ausmTerrainWaveWeight")) {
            source = source.replace(
                    "        #ifdef WAVING_ANYTHING_TERRAIN\n"
                            + "            DoWave(position.xyz, mat);\n"
                            + "        #endif\n",
                    "        #ifdef WAVING_ANYTHING_TERRAIN\n"
                            + "            float ausmTerrainWaveWeight = ausmEntreeFoliageWaveWeight(position.xyz);\n"
                            + "            vec3 ausmTerrainWavedPosition = position.xyz;\n"
                            + "            DoWave(ausmTerrainWavedPosition, mat);\n"
                            + "            position.xyz = mix(position.xyz, ausmTerrainWavedPosition, ausmTerrainWaveWeight);\n"
                            + "        #endif\n"
            );
            source = source.replace(
                    "                DoInteractiveWave(playerPosM, mat);\n",
                    "                vec3 ausmInteractivePosition = playerPosM;\n"
                            + "                DoInteractiveWave(playerPosM, mat);\n"
                            + "                playerPosM = mix(ausmInteractivePosition, playerPosM, ausmEntreeFoliageWaveWeight(position.xyz));\n"
            );
            source = source.replace(
                    "        #ifdef WAVE_EVERYTHING\n"
                            + "            DoWaveEverything(position.xyz);\n"
                            + "        #endif\n",
                    "        #ifdef WAVE_EVERYTHING\n"
                            + "            vec3 ausmEverythingWavePosition = position.xyz;\n"
                            + "            DoWaveEverything(ausmEverythingWavePosition);\n"
                            + "            position.xyz = mix(position.xyz, ausmEverythingWavePosition, ausmEntreeDetailWeight(position.xyz));\n"
                            + "        #endif\n"
            );
        }
        if ("gbuffers_water.glsl".equals(programName) && !source.contains("ausmWaterWaveWeight")) {
            source = source.replace(
                    "    #ifdef WAVING_WATER_VERTEX\n"
                            + "        DoWave(position.xyz, mat);\n"
                            + "    #endif\n",
                    "    #ifdef WAVING_WATER_VERTEX\n"
                            + "        float ausmWaterWaveWeight = ausmEntreeWaterDetailWeight(position.xyz);\n"
                            + "        vec3 ausmWaterWavedPosition = position.xyz;\n"
                            + "        DoWave(ausmWaterWavedPosition, mat);\n"
                            + "        position.xyz = mix(position.xyz, ausmWaterWavedPosition, ausmWaterWaveWeight);\n"
                            + "    #endif\n"
            );
            source = source.replace(
                    "    #ifdef WAVE_EVERYTHING\n"
                            + "        DoWaveEverything(position.xyz);\n"
                            + "    #endif\n",
                    "    #ifdef WAVE_EVERYTHING\n"
                            + "        vec3 ausmEverythingWaterWavePosition = position.xyz;\n"
                            + "        DoWaveEverything(ausmEverythingWaterWavePosition);\n"
                            + "        position.xyz = mix(position.xyz, ausmEverythingWaterWavePosition, ausmEntreeWaterDetailWeight(position.xyz));\n"
                            + "    #endif\n"
            );
        }
        return source;
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
