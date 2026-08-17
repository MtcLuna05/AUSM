package com.l.ausm.impl.pipeline;

import net.minecraft.util.ResourceLocation;

final class PipelineSkyConstants {
    static final int SIMPLE_VOID_WORLD_DIMENSION_ID = 43;
    static final String CUSTOM_VOID_WORLD_OPTION = "AUSM_CUSTOM_VOID_WORLD";
    static final String ASTRAL_NATIVE_STARS_OPTION = "AUSM_ASTRAL_NATIVE_STARS";
    static final String ASTRAL_NATIVE_CONSTELLATIONS_OPTION = "AUSM_ASTRAL_NATIVE_CONSTELLATIONS";
    static final String ASTRAL_SKYBOX_CLASS = "hellfirepvp.astralsorcery.client.sky.RenderSkybox";
    static final ResourceLocation BOTANIA_VOID_SKYBOX_TEXTURE = new ResourceLocation("botania", "textures/misc/skybox.png");
    static final ResourceLocation BOTANIA_VOID_RAINBOW_TEXTURE = new ResourceLocation("botania", "textures/misc/rainbow.png");
    static final ResourceLocation[] BOTANIA_VOID_PLANET_TEXTURES = new ResourceLocation[]{
            new ResourceLocation("botania", "textures/misc/planet0.png"),
            new ResourceLocation("botania", "textures/misc/planet1.png"),
            new ResourceLocation("botania", "textures/misc/planet2.png"),
            new ResourceLocation("botania", "textures/misc/planet3.png"),
            new ResourceLocation("botania", "textures/misc/planet4.png"),
            new ResourceLocation("botania", "textures/misc/planet5.png")
    };

    private PipelineSkyConstants() {
    }
}
