package com.l.ausm.impl.pipeline.pack;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderPackConfigurationStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsShaderSelectionAndPackOptions() {
        ShaderPackConfigurationStore store = new ShaderPackConfigurationStore(temporaryDirectory);
        store.save("Entree.zip", true);
        store.saveOptions("Entree.zip", Map.of("SHADOW_QUALITY", "2", "BLOOM", "true"));

        assertEquals(new SavedShaderConfiguration("Entree.zip", true), store.load("OFF"));
        assertEquals(Map.of("SHADOW_QUALITY", "2", "BLOOM", "true"),
                store.loadOptions("Entree.zip", "(internal)"));

        store.resetOptions("Entree.zip");
        assertTrue(store.loadOptions("Entree.zip", "(internal)").isEmpty());
    }

    @Test
    void internalPacksNeverReadExternalOverrides() {
        ShaderPackConfigurationStore store = new ShaderPackConfigurationStore(temporaryDirectory);

        assertTrue(store.loadOptions("(internal)", "(internal)").isEmpty());
    }
}
