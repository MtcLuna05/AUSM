package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.impl.MainMod;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class ShaderPackRepository {
    private final Path directory;

    ShaderPackRepository(Path directory) {
        this.directory = directory;
    }

    Path directory() {
        return directory;
    }

    void ensureDirectoryExists() {
        try {
            if (!Files.exists(directory)) {
                MainMod.LOGGER.info("Shaderpacks directory not found, creating at: {}", directory.toAbsolutePath());
            }
            Files.createDirectories(directory);
        } catch (IOException exception) {
            MainMod.LOGGER.error("Failed to create shaderpacks directory!", exception);
        }
    }

    ShaderPack open(String packName) {
        Path packPath = resolve(packName);
        if (packPath == null) {
            MainMod.LOGGER.warn("Attempted to load shaderpack with invalid path name '{}'; disabling shaders.", packName);
            return null;
        }
        if (!Files.exists(packPath)) {
            MainMod.LOGGER.warn("Attempted to load shaderpack '{}', but it does not exist at '{}'",
                    packName, packPath.toAbsolutePath());
            return null;
        }

        try {
            if (Files.isDirectory(packPath)) {
                MainMod.LOGGER.info("Loading folder shaderpack: {}", packName);
                return new FolderShaderPack(packPath);
            }
            if (packName.endsWith(".zip")) {
                MainMod.LOGGER.info("Loading zip shaderpack: {}", packName);
                return new ZipShaderPack(packPath);
            }
            MainMod.LOGGER.warn("Cannot load shaderpack '{}' because it is neither a folder nor a zip file.", packName);
        } catch (IOException exception) {
            MainMod.LOGGER.error("Failed to load shaderpack '{}'", packName, exception);
        }
        return null;
    }

    boolean isAvailable(String packName) {
        Path packPath = resolve(packName);
        return packPath != null && (Files.isDirectory(packPath) || Files.isRegularFile(packPath));
    }

    String importPack(Path source) throws IOException {
        if (!isValidPackPath(source)) {
            return null;
        }

        Files.createDirectories(directory);
        String name = source.getFileName().toString();
        Path target = directory.resolve(name);
        if (Files.exists(target)) {
            if (Files.isSameFile(source, target)) {
                return name;
            }
            throw new FileAlreadyExistsException(target.toString());
        }

        if (Files.isDirectory(source)) {
            copyDirectory(source, target);
        } else {
            Files.copy(source, target);
        }
        return name;
    }

    List<String> availablePacks(String offPackName) {
        List<String> packs = new ArrayList<>();
        packs.add(offPackName);
        if (!Files.exists(directory)) {
            return packs;
        }

        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(this::isValidPackPath)
                    .map(path -> path.getFileName().toString())
                    .forEach(packs::add);
        } catch (IOException exception) {
            MainMod.LOGGER.error("Failed to list available shaderpacks!", exception);
        }
        packs.subList(1, packs.size()).sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        return packs;
    }

    String fingerprint(String packName) {
        Path path = resolve(packName);
        if (path == null) {
            return "invalid";
        }
        if (!Files.exists(path)) {
            return "missing";
        }
        if (Files.isRegularFile(path)) {
            return Long.toString(pathFingerprint(path));
        }

        try (Stream<Path> stream = Files.walk(path)) {
            long fingerprint = stream.filter(Files::isRegularFile)
                    // Files.walk does not promise a stable traversal order. A
                    // folded hash must sort its inputs or an unchanged folder
                    // can look modified to the live shader reload watcher.
                    .sorted()
                    .mapToLong(ShaderPackRepository::pathFingerprint)
                    .reduce(17L, (current, value) -> current * 31L + value);
            return Long.toString(fingerprint);
        } catch (IOException exception) {
            MainMod.LOGGER.warn("Failed to fingerprint shaderpack '{}'; falling back to directory timestamp.",
                    packName, exception);
            return Long.toString(pathFingerprint(path));
        }
    }

    private Path resolve(String packName) {
        if (packName == null || packName.isEmpty()) {
            return null;
        }
        try {
            Path direct = directory.resolve(packName);
            if (Files.exists(direct)) {
                return direct;
            }
            Path aliasMatch = findByAlias(packName);
            return aliasMatch != null ? aliasMatch : direct;
        } catch (InvalidPathException exception) {
            Path aliasMatch = findByAlias(packName);
            if (aliasMatch != null) {
                MainMod.LOGGER.warn(
                        "Resolved shaderpack '{}' through directory scan because the JVM filesystem encoding rejected the saved name.",
                        packName);
                return aliasMatch;
            }
            MainMod.LOGGER.warn("Ignoring shaderpack name with invalid filesystem encoding: '{}'", packName);
            return null;
        }
    }

    private Path findByAlias(String packName) {
        if (!Files.exists(directory)) {
            return null;
        }
        String targetAlias = alias(packName);
        if (targetAlias.isEmpty()) {
            return null;
        }

        Path bestPath = null;
        int bestScore = Integer.MAX_VALUE;
        int bestLengthDelta = Integer.MAX_VALUE;
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : stream.toList()) {
                if (!isValidPackPath(path)) {
                    continue;
                }
                String candidateName = path.getFileName() != null ? path.getFileName().toString() : "";
                int score = aliasDistance(targetAlias, alias(candidateName));
                if (score < 0) {
                    continue;
                }
                int lengthDelta = Math.abs(candidateName.length() - packName.length());
                if (score < bestScore
                        || score == bestScore && lengthDelta < bestLengthDelta
                        || score == bestScore && lengthDelta == bestLengthDelta
                        && Files.isDirectory(path) && bestPath != null && !Files.isDirectory(bestPath)) {
                    bestPath = path;
                    bestScore = score;
                    bestLengthDelta = lengthDelta;
                }
            }
        } catch (IOException exception) {
            MainMod.LOGGER.warn("Failed to scan shaderpacks for alias match for '{}'", packName, exception);
        }
        return bestPath;
    }

    private static int aliasDistance(String targetAlias, String candidateAlias) {
        if (targetAlias.equals(candidateAlias)) {
            return 0;
        }
        int distance = boundedEditDistance(targetAlias, candidateAlias, 2);
        return distance <= 2 ? distance : -1;
    }

    private static String alias(String name) {
        if (name == null) {
            return "";
        }
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character >= 'a' && character <= 'z' || character >= '0' && character <= '9') {
                builder.append(character);
            }
        }
        String result = builder.toString();
        return result.endsWith("zip") ? result.substring(0, result.length() - 3) : result;
    }

    private static int boundedEditDistance(String left, String right, int maxDistance) {
        if (Math.abs(left.length() - right.length()) > maxDistance) {
            return maxDistance + 1;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            int rowMinimum = row;
            for (int column = 1; column <= right.length(); column++) {
                int cost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + cost);
                rowMinimum = Math.min(rowMinimum, current[column]);
            }
            if (rowMinimum > maxDistance) {
                return maxDistance + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private boolean isValidPackPath(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        if (Files.isDirectory(path)) {
            return true;
        }
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && fileName.endsWith(".zip");
    }

    private static long pathFingerprint(Path path) {
        try {
            long modified = Files.getLastModifiedTime(path).toMillis();
            long size = Files.isRegularFile(path) ? Files.size(path) : 0L;
            return modified * 31L + size;
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.sorted().toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }
}
