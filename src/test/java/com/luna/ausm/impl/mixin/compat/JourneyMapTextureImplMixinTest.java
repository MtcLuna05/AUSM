package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.compat.JourneyMapDefaultSkinFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JourneyMapTextureImplMixinTest {
    @Test
    void identifiesOnlyVanillaDefaultPlayerTextures() {
        assertTrue(JourneyMapDefaultSkinFilter.isDefaultPlayerSkin(
                "minecraft:textures/entity/alex.png"));
        assertTrue(JourneyMapDefaultSkinFilter.isDefaultPlayerSkin(
                "minecraft:textures/entity/steve.png"));

        assertFalse(JourneyMapDefaultSkinFilter.isDefaultPlayerSkin(null));
        assertFalse(JourneyMapDefaultSkinFilter.isDefaultPlayerSkin(
                "minecraft:skins/downloaded.png"));
        assertFalse(JourneyMapDefaultSkinFilter.isDefaultPlayerSkin(
                "journeymap:textures/entity/alex.png"));
    }
}
