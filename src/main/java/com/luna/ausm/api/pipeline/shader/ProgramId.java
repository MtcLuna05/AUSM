package com.luna.ausm.api.pipeline.shader;

import java.util.Arrays;
import java.util.Objects;

/**
 * Iris-style shaderpack program identity.
 *
 * <p>This intentionally separates shaderpack source names from AUSM's current
 * 1.12 render-pass binding. Deferred/composite/prepare are kept as AUSM
 * extensions because OptiFine shaderpacks expose them as source programs.</p>
 */
public enum ProgramId {
    SHADOW(ProgramGroup.SHADOW, "", null),
    SHADOW_SOLID(ProgramGroup.SHADOW, "solid", SHADOW),
    SHADOW_CUTOUT(ProgramGroup.SHADOW, "cutout", SHADOW),
    SHADOW_WATER(ProgramGroup.SHADOW, "water", SHADOW),
    SHADOW_ENTITIES(ProgramGroup.SHADOW, "entities", SHADOW),
    SHADOW_LIGHTNING(ProgramGroup.SHADOW, "lightning", SHADOW_ENTITIES),
    SHADOW_BLOCK(ProgramGroup.SHADOW, "block", SHADOW),

    BASIC(ProgramGroup.GBUFFERS, "basic", null),
    LINE(ProgramGroup.GBUFFERS, "line", BASIC),
    TEXTURED(ProgramGroup.GBUFFERS, "textured", BASIC),
    TEXTURED_LIT(ProgramGroup.GBUFFERS, "textured_lit", TEXTURED),
    SKY_BASIC(ProgramGroup.GBUFFERS, "skybasic", BASIC),
    SKY_TEXTURED(ProgramGroup.GBUFFERS, "skytextured", TEXTURED),
    CLOUDS(ProgramGroup.GBUFFERS, "clouds", TEXTURED),
    TERRAIN(ProgramGroup.GBUFFERS, "terrain", TEXTURED_LIT),
    TERRAIN_SOLID(ProgramGroup.GBUFFERS, "terrain_solid", TERRAIN),
    TERRAIN_CUTOUT(ProgramGroup.GBUFFERS, "terrain_cutout", TERRAIN),
    TERRAIN_CUTOUT_MIPPED(ProgramGroup.GBUFFERS, "terrain_cutout_mip", TERRAIN_CUTOUT),
    DAMAGED_BLOCK(ProgramGroup.GBUFFERS, "damagedblock", TERRAIN),
    BLOCK(ProgramGroup.GBUFFERS, "block", TERRAIN),
    BLOCK_TRANSLUCENT(ProgramGroup.GBUFFERS, "block_translucent", BLOCK),
    BEACON_BEAM(ProgramGroup.GBUFFERS, "beaconbeam", TEXTURED),
    ITEM(ProgramGroup.GBUFFERS, "item", TEXTURED_LIT),
    ENTITIES(ProgramGroup.GBUFFERS, "entities", TEXTURED_LIT),
    ENTITIES_TRANSLUCENT(ProgramGroup.GBUFFERS, "entities_translucent", ENTITIES),
    LIGHTNING(ProgramGroup.GBUFFERS, "lightning", ENTITIES),
    ENTITIES_GLOWING(ProgramGroup.GBUFFERS, "entities_glowing", ENTITIES),
    ARMOR_GLINT(ProgramGroup.GBUFFERS, "armor_glint", TEXTURED),
    SPIDER_EYES(ProgramGroup.GBUFFERS, "spidereyes", TEXTURED),
    HAND(ProgramGroup.GBUFFERS, "hand", TEXTURED_LIT),
    PARTICLES(ProgramGroup.GBUFFERS, "particles", TEXTURED_LIT),
    PARTICLES_TRANSLUCENT(ProgramGroup.GBUFFERS, "particles_translucent", PARTICLES),
    WEATHER(ProgramGroup.GBUFFERS, "weather", TEXTURED_LIT),
    WATER(ProgramGroup.GBUFFERS, "water", TERRAIN),
    HAND_WATER(ProgramGroup.GBUFFERS, "hand_water", HAND),
    DH_TERRAIN(ProgramGroup.DH, "terrain", null),
    DH_WATER(ProgramGroup.DH, "water", DH_TERRAIN),

    PREPARE(ProgramGroup.PREPARE, "", null),
    DEFERRED(ProgramGroup.DEFERRED, "", null),
    DEFERRED1(ProgramGroup.DEFERRED, "1", null),
    DEFERRED2(ProgramGroup.DEFERRED, "2", null),
    DEFERRED3(ProgramGroup.DEFERRED, "3", null),
    DEFERRED4(ProgramGroup.DEFERRED, "4", null),
    DEFERRED5(ProgramGroup.DEFERRED, "5", null),
    DEFERRED6(ProgramGroup.DEFERRED, "6", null),
    DEFERRED7(ProgramGroup.DEFERRED, "7", null),
    COMPOSITE(ProgramGroup.COMPOSITE, "", null),
    COMPOSITE1(ProgramGroup.COMPOSITE, "1", null),
    COMPOSITE2(ProgramGroup.COMPOSITE, "2", null),
    COMPOSITE3(ProgramGroup.COMPOSITE, "3", null),
    COMPOSITE4(ProgramGroup.COMPOSITE, "4", null),
    COMPOSITE5(ProgramGroup.COMPOSITE, "5", null),
    COMPOSITE6(ProgramGroup.COMPOSITE, "6", null),
    COMPOSITE7(ProgramGroup.COMPOSITE, "7", null),
    FINAL(ProgramGroup.FINAL, "", null);

    private final ProgramGroup group;
    private final String sourceName;
    private final ProgramId fallback;

    ProgramId(ProgramGroup group, String name, ProgramId fallback) {
        this.group = group;
        this.sourceName = name.isEmpty() ? group.baseName() : group.baseName() + "_" + name;
        this.fallback = fallback;
    }

    public ProgramGroup group() {
        return group;
    }

    public String sourceName() {
        return sourceName;
    }

    public ProgramId fallback() {
        return fallback;
    }

    public static ProgramId fromSourceName(String name) {
        Objects.requireNonNull(name, "name");
        if ("gbuffers_terrain_cutout_mip".equals(name) || "gbuffers_terrain_cutout_mipped".equals(name)) {
            return TERRAIN_CUTOUT_MIPPED;
        }
        return Arrays.stream(values())
                .filter(program -> program.sourceName.equals(name))
                .findFirst()
                .orElse(null);
    }
}
