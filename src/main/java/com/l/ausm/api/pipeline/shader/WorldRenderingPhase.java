package com.l.ausm.api.pipeline.shader;

/**
 * Iris-style rendering phases.
 *
 * <p>Mixin hooks should prefer phases over direct {@link RenderPass} choices so
 * the 1.12 render loop can gradually converge on Iris' central pipeline map.</p>
 */
public enum WorldRenderingPhase {
    NONE(null, null),
    SKY(RenderPass.GBUFFERS_SKYBASIC, RenderPass.SHADOW),
    SUNSET(RenderPass.GBUFFERS_SKYBASIC, RenderPass.SHADOW),
    CUSTOM_SKY(RenderPass.GBUFFERS_SKYTEXTURED, RenderPass.SHADOW),
    SUN(RenderPass.GBUFFERS_SKYTEXTURED, RenderPass.SHADOW),
    MOON(RenderPass.GBUFFERS_SKYTEXTURED, RenderPass.SHADOW),
    STARS(RenderPass.GBUFFERS_SKYBASIC, RenderPass.SHADOW),
    VOID(RenderPass.GBUFFERS_SKYTEXTURED, RenderPass.SHADOW),
    TERRAIN_SOLID(RenderPass.GBUFFERS_TERRAIN_SOLID, RenderPass.SHADOW_SOLID),
    TERRAIN_CUTOUT_MIPPED(RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP, RenderPass.SHADOW_CUTOUT),
    TERRAIN_CUTOUT(RenderPass.GBUFFERS_TERRAIN_CUTOUT, RenderPass.SHADOW_CUTOUT),
    ENTITIES(RenderPass.GBUFFERS_ENTITIES, RenderPass.SHADOW_ENTITIES),
    BLOCK_ENTITIES(RenderPass.GBUFFERS_BLOCK, RenderPass.SHADOW_BLOCK),
    DESTROY(RenderPass.GBUFFERS_DAMAGEDBLOCK, RenderPass.SHADOW),
    OUTLINE(RenderPass.GBUFFERS_LINE, RenderPass.SHADOW),
    DEBUG(RenderPass.GBUFFERS_BASIC, RenderPass.SHADOW),
    HAND_SOLID(RenderPass.GBUFFERS_HAND, RenderPass.SHADOW_ENTITIES),
    TERRAIN_TRANSLUCENT(RenderPass.GBUFFERS_WATER, RenderPass.SHADOW_WATER),
    TRIPWIRE(RenderPass.GBUFFERS_WATER, RenderPass.SHADOW_WATER),
    PARTICLES(RenderPass.GBUFFERS_PARTICLES, RenderPass.SHADOW),
    CLOUDS(RenderPass.GBUFFERS_CLOUDS, RenderPass.SHADOW),
    RAIN_SNOW(RenderPass.GBUFFERS_WEATHER, RenderPass.SHADOW),
    WORLD_BORDER(RenderPass.GBUFFERS_BASIC, RenderPass.SHADOW),
    HAND_TRANSLUCENT(RenderPass.GBUFFERS_HAND_WATER, RenderPass.SHADOW_ENTITIES),

    // AUSM-specific refinements. Keep these after Iris' official stages so
    // renderStage ordinals remain compatible with Iris shaderpacks.
    SKY_TEXTURED(RenderPass.GBUFFERS_SKYTEXTURED, RenderPass.SHADOW),
    ENTITIES_TRANSLUCENT(RenderPass.GBUFFERS_ENTITIES_TRANSLUCENT, RenderPass.SHADOW_ENTITIES),
    BLOCK_ENTITIES_TRANSLUCENT(RenderPass.GBUFFERS_BLOCK_TRANSLUCENT, RenderPass.SHADOW_BLOCK),
    BEACON_BEAM(RenderPass.GBUFFERS_BEACONBEAM, RenderPass.SHADOW_ENTITIES),
    ITEM(RenderPass.GBUFFERS_ITEM, RenderPass.SHADOW_ENTITIES),
    LIGHTNING(RenderPass.GBUFFERS_LIGHTNING, RenderPass.SHADOW_LIGHTNING),
    ARMOR_GLINT(RenderPass.GBUFFERS_ARMOR_GLINT, RenderPass.SHADOW_ENTITIES),
    SPIDER_EYES(RenderPass.GBUFFERS_SPIDEREYES, RenderPass.SHADOW_ENTITIES),
    PARTICLES_TRANSLUCENT(RenderPass.GBUFFERS_PARTICLES_TRANSLUCENT, RenderPass.SHADOW),
    ASTRAL_STARS(RenderPass.GBUFFERS_SKYTEXTURED, RenderPass.SHADOW),
    ASTRAL_SOLAR_ECLIPSE(RenderPass.GBUFFERS_SKYTEXTURED, RenderPass.SHADOW),
    SKY_GROUND(RenderPass.GBUFFERS_SKYBASIC, RenderPass.SHADOW);

    private final RenderPass mainPass;
    private final RenderPass shadowPass;

    WorldRenderingPhase(RenderPass mainPass, RenderPass shadowPass) {
        this.mainPass = mainPass;
        this.shadowPass = shadowPass;
    }

    public RenderPass mainPass() {
        return mainPass;
    }

    public RenderPass shadowPass() {
        return shadowPass != null ? shadowPass : mainPass;
    }

    public boolean usesBlockAtlas() {
        return switch (this) {
            case TERRAIN_SOLID, TERRAIN_CUTOUT_MIPPED, TERRAIN_CUTOUT, TERRAIN_TRANSLUCENT, TRIPWIRE,
                 DESTROY, BLOCK_ENTITIES, BLOCK_ENTITIES_TRANSLUCENT, ITEM, HAND_SOLID, HAND_TRANSLUCENT -> true;
            default -> false;
        };
    }

    public boolean usesEntityFormat() {
        return switch (this) {
            case ITEM, ENTITIES, ENTITIES_TRANSLUCENT, LIGHTNING, BLOCK_ENTITIES, BLOCK_ENTITIES_TRANSLUCENT,
                 BEACON_BEAM, ARMOR_GLINT, SPIDER_EYES, HAND_SOLID, HAND_TRANSLUCENT -> true;
            default -> false;
        };
    }
}
