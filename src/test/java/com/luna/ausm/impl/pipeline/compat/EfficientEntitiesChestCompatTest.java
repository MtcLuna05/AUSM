package com.luna.ausm.impl.pipeline.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EfficientEntitiesChestCompatTest {
    @Test
    void recognizesChestTileEntityClassNames() {
        assertTrue(EfficientEntitiesChestCompat.isChestClassName(
                "net.minecraft.tileentity.TileEntityChest"));
        assertTrue(EfficientEntitiesChestCompat.isChestClassName(
                "example.storage.TileEntityEnderChest"));
        assertFalse(EfficientEntitiesChestCompat.isChestClassName(
                "example.machine.TileEntityCrusher"));
    }

    @Test
    void rejectsNullClassNames() {
        assertFalse(EfficientEntitiesChestCompat.isChestClassName(null));
    }

    @Test
    void keepsVanillaModelRenderingWhileShaderPipelineIsActive() {
        assertTrue(EfficientEntitiesChestCompat.shouldUseVanillaModelRenderer(true));
        assertFalse(EfficientEntitiesChestCompat.shouldUseVanillaModelRenderer(false));
    }
}
