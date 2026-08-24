package com.luna.ausm.impl.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientSettingsConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsDefaultShaderedLodRadiiInBlocks() {
        ClientSettingsConfig config = new ClientSettingsConfig(temporaryDirectory);

        config.load();

        assertEquals(96, config.shaderedLod1RadiusBlocks());
        assertEquals(144, config.shaderedLod2RadiusBlocks());
        assertEquals(192, config.shaderedLod3RadiusBlocks());
        assertEquals(240, config.shaderedLod4RadiusBlocks());
    }

    @Test
    void keepsLodRadiiOrderedAndPersisted() throws Exception {
        ClientSettingsConfig config = new ClientSettingsConfig(temporaryDirectory);
        config.load();

        config.setShaderedLod4RadiusBlocks(768);
        config.setShaderedLod3RadiusBlocks(752);
        config.setShaderedLod2RadiusBlocks(736);
        config.setShaderedLod3RadiusBlocks(128);

        assertEquals(736, config.shaderedLod2RadiusBlocks());
        assertEquals(752, config.shaderedLod3RadiusBlocks());
        assertEquals(768, config.shaderedLod4RadiusBlocks());

        Path configFile = temporaryDirectory.resolve("config/ausm/client-settings.properties");
        String persisted = Files.readString(configFile);
        assertTrue(persisted.contains("shaderedLod2RadiusBlocks=736"));
        assertTrue(persisted.contains("shaderedLod3RadiusBlocks=752"));
    }
}
