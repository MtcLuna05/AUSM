package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.pipeline.pack.ShaderAlphaTest;

import java.util.Locale;

/**
 * Iris-style shader key metadata.
 *
 * <p>This is the backport layer between 1.12 render hooks and Iris' program
 * routing model. Vertex formats are still handled by the existing AUSM
 * extended format code; the key currently carries the Iris program id,
 * fallback alpha test, fog mode, and vanilla lighting classification.</p>
 */
public enum ShaderKey {
    BASIC(ProgramId.BASIC, ShaderAlphaTest.ALWAYS, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    BASIC_COLOR(ProgramId.BASIC, ShaderAlphaTest.NON_ZERO_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP),
    TEXTURED(ProgramId.TEXTURED, ShaderAlphaTest.NON_ZERO_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP),
    TEXTURED_COLOR(ProgramId.TEXTURED, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP),
    SPS(ProgramId.SPIDER_EYES, ShaderAlphaTest.ALWAYS, FogMode.PER_FRAGMENT, LightingModel.FULLBRIGHT),
    SKY_BASIC(ProgramId.SKY_BASIC, ShaderAlphaTest.ALWAYS, FogMode.OFF, LightingModel.FULLBRIGHT),
    SKY_BASIC_COLOR(ProgramId.SKY_BASIC, ShaderAlphaTest.NON_ZERO_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP),
    SKY_TEXTURED(ProgramId.SKY_TEXTURED, ShaderAlphaTest.ALWAYS, FogMode.OFF, LightingModel.LIGHTMAP),
    SKY_TEXTURED_COLOR(ProgramId.SKY_TEXTURED, ShaderAlphaTest.ALWAYS, FogMode.OFF, LightingModel.LIGHTMAP),
    CLOUDS(ProgramId.CLOUDS, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    TERRAIN_SOLID(ProgramId.TERRAIN_SOLID, ShaderAlphaTest.ALWAYS, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    TERRAIN_CUTOUT(ProgramId.TERRAIN_CUTOUT, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    TERRAIN_CUTOUT_MIPPED(ProgramId.TERRAIN_CUTOUT_MIPPED, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    TERRAIN_TRANSLUCENT(ProgramId.WATER, ShaderAlphaTest.NON_ZERO_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    MOVING_BLOCK(ProgramId.BLOCK, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    ENTITIES_ALPHA(ProgramId.ENTITIES, ShaderAlphaTest.VERTEX_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    ENTITIES_SOLID(ProgramId.ENTITIES, ShaderAlphaTest.ALWAYS, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    ENTITIES_CUTOUT(ProgramId.ENTITIES, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    ENTITIES_TRANSLUCENT(ProgramId.ENTITIES_TRANSLUCENT, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.DIFFUSE_LM),
    ENTITIES_EYES(ProgramId.SPIDER_EYES, ShaderAlphaTest.NON_ZERO_ALPHA, FogMode.PER_VERTEX, LightingModel.FULLBRIGHT),
    LIGHTNING(ProgramId.LIGHTNING, ShaderAlphaTest.ALWAYS, FogMode.PER_VERTEX, LightingModel.FULLBRIGHT),
    HAND_CUTOUT(ProgramId.HAND, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    HAND_TRANSLUCENT(ProgramId.HAND_WATER, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    PARTICLES(ProgramId.PARTICLES, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    PARTICLES_TRANS(ProgramId.PARTICLES_TRANSLUCENT, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    WEATHER(ProgramId.WEATHER, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    CRUMBLING(ProgramId.DAMAGED_BLOCK, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.OFF, LightingModel.FULLBRIGHT),
    BLOCK_ENTITY(ProgramId.BLOCK, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    BE_TRANSLUCENT(ProgramId.BLOCK_TRANSLUCENT, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.PER_VERTEX, LightingModel.DIFFUSE_LM),
    BEACON(ProgramId.BEACON_BEAM, ShaderAlphaTest.ALWAYS, FogMode.PER_FRAGMENT, LightingModel.FULLBRIGHT),
    GLINT(ProgramId.ARMOR_GLINT, ShaderAlphaTest.NON_ZERO_ALPHA, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),
    LINES(ProgramId.LINE, ShaderAlphaTest.ALWAYS, FogMode.PER_VERTEX, LightingModel.LIGHTMAP),

    SHADOW_TERRAIN_SOLID(ProgramId.SHADOW_SOLID, ShaderAlphaTest.ALWAYS, FogMode.OFF, LightingModel.LIGHTMAP),
    SHADOW_TERRAIN_CUTOUT(ProgramId.SHADOW_CUTOUT, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP),
    SHADOW_TRANSLUCENT(ProgramId.SHADOW_WATER, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP),
    SHADOW_ENTITIES_CUTOUT(ProgramId.SHADOW_ENTITIES, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP),
    SHADOW_BLOCK(ProgramId.SHADOW_BLOCK, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP),
    SHADOW_BEACON_BEAM(ProgramId.SHADOW_ENTITIES, ShaderAlphaTest.ALWAYS, FogMode.OFF, LightingModel.FULLBRIGHT),
    SHADOW_BASIC(ProgramId.SHADOW, ShaderAlphaTest.ALWAYS, FogMode.OFF, LightingModel.LIGHTMAP),
    SHADOW_TEX(ProgramId.SHADOW, ShaderAlphaTest.NON_ZERO_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP),
    SHADOW_TEX_COLOR(ProgramId.SHADOW, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP),
    SHADOW_LINES(ProgramId.SHADOW, ShaderAlphaTest.ALWAYS, FogMode.OFF, LightingModel.LIGHTMAP),
    SHADOW_LIGHTNING(ProgramId.SHADOW_LIGHTNING, ShaderAlphaTest.ALWAYS, FogMode.OFF, LightingModel.FULLBRIGHT),
    SHADOW_PARTICLES(ProgramId.SHADOW, ShaderAlphaTest.ONE_TENTH_ALPHA, FogMode.OFF, LightingModel.LIGHTMAP);

    private final ProgramId program;
    private final ShaderAlphaTest alphaTest;
    private final FogMode fogMode;
    private final LightingModel lightingModel;

    ShaderKey(ProgramId program, ShaderAlphaTest alphaTest, FogMode fogMode, LightingModel lightingModel) {
        this.program = program;
        this.alphaTest = alphaTest;
        this.fogMode = fogMode;
        this.lightingModel = lightingModel;
    }

    public ProgramId program() {
        return program;
    }

    public ShaderAlphaTest alphaTest() {
        return alphaTest;
    }

    public FogMode fogMode() {
        return fogMode;
    }

    public LightingModel lightingModel() {
        return lightingModel;
    }

    public boolean isShadow() {
        return program.group() == ProgramGroup.SHADOW;
    }

    public boolean hasDiffuseLighting() {
        return lightingModel == LightingModel.DIFFUSE || lightingModel == LightingModel.DIFFUSE_LM;
    }

    public boolean shouldIgnoreLightmap() {
        return lightingModel == LightingModel.FULLBRIGHT || lightingModel == LightingModel.DIFFUSE;
    }

    public String keyName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ShaderKey fromRenderPass(RenderPass pass) {
        return switch (pass) {
            case SHADOW -> SHADOW_BASIC;
            case SHADOW_SOLID -> SHADOW_TERRAIN_SOLID;
            case SHADOW_CUTOUT -> SHADOW_TERRAIN_CUTOUT;
            case SHADOW_WATER -> SHADOW_TRANSLUCENT;
            case SHADOW_ENTITIES -> SHADOW_ENTITIES_CUTOUT;
            case SHADOW_LIGHTNING -> SHADOW_LIGHTNING;
            case SHADOW_BLOCK -> SHADOW_BLOCK;
            case GBUFFERS_BASIC -> BASIC;
            case GBUFFERS_TEXTURED -> TEXTURED;
            case GBUFFERS_TEXTURED_LIT -> TEXTURED_COLOR;
            case GBUFFERS_SKYBASIC -> SKY_BASIC;
            case GBUFFERS_SKYTEXTURED -> SKY_TEXTURED;
            case GBUFFERS_CLOUDS -> CLOUDS;
            case GBUFFERS_TERRAIN, GBUFFERS_TERRAIN_SOLID -> TERRAIN_SOLID;
            case GBUFFERS_TERRAIN_CUTOUT_MIP -> TERRAIN_CUTOUT_MIPPED;
            case GBUFFERS_TERRAIN_CUTOUT -> TERRAIN_CUTOUT;
            case GBUFFERS_DAMAGEDBLOCK -> CRUMBLING;
            case GBUFFERS_BLOCK -> BLOCK_ENTITY;
            case GBUFFERS_BLOCK_TRANSLUCENT -> BE_TRANSLUCENT;
            case GBUFFERS_BEACONBEAM -> BEACON;
            case GBUFFERS_ITEM, GBUFFERS_HAND -> HAND_CUTOUT;
            case GBUFFERS_ENTITIES -> ENTITIES_CUTOUT;
            case GBUFFERS_ENTITIES_TRANSLUCENT -> ENTITIES_TRANSLUCENT;
            case GBUFFERS_LIGHTNING -> LIGHTNING;
            case GBUFFERS_ENTITIES_GLOWING, GBUFFERS_SPIDEREYES -> ENTITIES_EYES;
            case GBUFFERS_ARMOR_GLINT -> GLINT;
            case GBUFFERS_LINE -> LINES;
            case GBUFFERS_PARTICLES -> PARTICLES;
            case GBUFFERS_PARTICLES_TRANSLUCENT -> PARTICLES_TRANS;
            case GBUFFERS_WEATHER -> WEATHER;
            case GBUFFERS_WATER -> TERRAIN_TRANSLUCENT;
            case GBUFFERS_HAND_WATER -> HAND_TRANSLUCENT;
            default -> null;
        };
    }
}
