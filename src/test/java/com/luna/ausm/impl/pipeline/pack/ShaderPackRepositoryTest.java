package com.luna.ausm.impl.pipeline.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderPackRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void listsOnlySupportedPackPathsInStableOrder() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("Zulu"));
        Files.createDirectories(temporaryDirectory.resolve("alpha"));
        Files.writeString(temporaryDirectory.resolve("Beta.ZIP"), "zip-placeholder");
        Files.writeString(temporaryDirectory.resolve("notes.txt"), "not a pack");

        ShaderPackRepository repository = new ShaderPackRepository(temporaryDirectory);

        assertEquals(List.of("OFF", "alpha", "Beta.ZIP", "Zulu"), repository.availablePacks("OFF"));
    }

    @Test
    void resolvesEquivalentSavedNamesThroughTheAliasFallback() throws IOException {
        Files.writeString(temporaryDirectory.resolve("Complementary Reimagined.zip"), "zip-placeholder");

        ShaderPackRepository repository = new ShaderPackRepository(temporaryDirectory);

        assertTrue(repository.isAvailable("Complementary_Reimagined.zip"));
        assertFalse(repository.isAvailable("Unrelated Pack.zip"));
    }

    @Test
    void importsDirectoriesRecursivelyAndFingerprintsTheirContents() throws IOException {
        Path source = Files.createDirectories(temporaryDirectory.resolve("source-pack"));
        Files.createDirectories(source.resolve("shaders/lib"));
        Files.writeString(source.resolve("shaders/lib/common.glsl"), "first");
        Path destination = Files.createDirectories(temporaryDirectory.resolve("destination"));
        ShaderPackRepository repository = new ShaderPackRepository(destination);

        assertEquals("source-pack", repository.importPack(source));
        assertEquals("first", Files.readString(
                destination.resolve("source-pack/shaders/lib/common.glsl")));

        String before = repository.fingerprint("source-pack");
        Files.writeString(destination.resolve("source-pack/shaders/lib/common.glsl"), "second-version");
        assertNotEquals(before, repository.fingerprint("source-pack"));
    }
}
