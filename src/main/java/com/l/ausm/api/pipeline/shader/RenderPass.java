package com.l.ausm.api.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import java.util.Arrays;

/**
 * OptiFine-compatible shader program names with their stage and fallback chain.
 */
public enum RenderPass {
    PREPARE(ProgramId.PREPARE, ProgramStage.PREPARE, null),
    SHADOW(ProgramId.SHADOW, ProgramStage.SHADOW, null),
    SHADOW_SOLID(ProgramId.SHADOW_SOLID, ProgramStage.SHADOW, SHADOW),
    SHADOW_CUTOUT(ProgramId.SHADOW_CUTOUT, ProgramStage.SHADOW, SHADOW),
    SHADOW_WATER(ProgramId.SHADOW_WATER, ProgramStage.SHADOW, SHADOW),
    SHADOW_ENTITIES(ProgramId.SHADOW_ENTITIES, ProgramStage.SHADOW, SHADOW),
    SHADOW_LIGHTNING(ProgramId.SHADOW_LIGHTNING, ProgramStage.SHADOW, SHADOW_ENTITIES),
    SHADOW_BLOCK(ProgramId.SHADOW_BLOCK, ProgramStage.SHADOW, SHADOW),

    GBUFFERS_BASIC(ProgramId.BASIC, ProgramStage.GBUFFERS, null),
    GBUFFERS_TEXTURED(ProgramId.TEXTURED, ProgramStage.GBUFFERS, GBUFFERS_BASIC),
    GBUFFERS_TEXTURED_LIT(ProgramId.TEXTURED_LIT, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED),
    GBUFFERS_SKYBASIC(ProgramId.SKY_BASIC, ProgramStage.GBUFFERS, GBUFFERS_BASIC),
    GBUFFERS_SKYTEXTURED(ProgramId.SKY_TEXTURED, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED),
    GBUFFERS_CLOUDS(ProgramId.CLOUDS, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED),
    GBUFFERS_TERRAIN(ProgramId.TERRAIN, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED_LIT),
    GBUFFERS_TERRAIN_SOLID(ProgramId.TERRAIN_SOLID, ProgramStage.GBUFFERS, GBUFFERS_TERRAIN),
    GBUFFERS_TERRAIN_CUTOUT(ProgramId.TERRAIN_CUTOUT, ProgramStage.GBUFFERS, GBUFFERS_TERRAIN),
    GBUFFERS_TERRAIN_CUTOUT_MIP(ProgramId.TERRAIN_CUTOUT_MIPPED, ProgramStage.GBUFFERS, GBUFFERS_TERRAIN_CUTOUT),
    GBUFFERS_DAMAGEDBLOCK(ProgramId.DAMAGED_BLOCK, ProgramStage.GBUFFERS, GBUFFERS_TERRAIN),
    GBUFFERS_BLOCK(ProgramId.BLOCK, ProgramStage.GBUFFERS, GBUFFERS_TERRAIN),
    GBUFFERS_BLOCK_TRANSLUCENT(ProgramId.BLOCK_TRANSLUCENT, ProgramStage.GBUFFERS, GBUFFERS_BLOCK),
    GBUFFERS_BEACONBEAM(ProgramId.BEACON_BEAM, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED),
    GBUFFERS_ITEM(ProgramId.ITEM, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED_LIT),
    GBUFFERS_ENTITIES(ProgramId.ENTITIES, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED_LIT),
    GBUFFERS_ENTITIES_TRANSLUCENT(ProgramId.ENTITIES_TRANSLUCENT, ProgramStage.GBUFFERS, GBUFFERS_ENTITIES),
    GBUFFERS_LIGHTNING(ProgramId.LIGHTNING, ProgramStage.GBUFFERS, GBUFFERS_ENTITIES),
    GBUFFERS_ENTITIES_GLOWING(ProgramId.ENTITIES_GLOWING, ProgramStage.GBUFFERS, GBUFFERS_ENTITIES),
    GBUFFERS_ARMOR_GLINT(ProgramId.ARMOR_GLINT, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED),
    GBUFFERS_SPIDEREYES(ProgramId.SPIDER_EYES, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED),
    GBUFFERS_LINE(ProgramId.LINE, ProgramStage.GBUFFERS, GBUFFERS_BASIC),
    GBUFFERS_HAND(ProgramId.HAND, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED_LIT),
    GBUFFERS_PARTICLES(ProgramId.PARTICLES, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED_LIT),
    GBUFFERS_PARTICLES_TRANSLUCENT(ProgramId.PARTICLES_TRANSLUCENT, ProgramStage.GBUFFERS, GBUFFERS_PARTICLES),
    GBUFFERS_WEATHER(ProgramId.WEATHER, ProgramStage.GBUFFERS, GBUFFERS_TEXTURED_LIT),
    GBUFFERS_WATER(ProgramId.WATER, ProgramStage.GBUFFERS, GBUFFERS_TERRAIN),
    GBUFFERS_HAND_WATER(ProgramId.HAND_WATER, ProgramStage.GBUFFERS, GBUFFERS_HAND),

    DEFERRED(ProgramId.DEFERRED, ProgramStage.DEFERRED, null),
    DEFERRED1(ProgramId.DEFERRED1, ProgramStage.DEFERRED, null),
    DEFERRED2(ProgramId.DEFERRED2, ProgramStage.DEFERRED, null),
    DEFERRED3(ProgramId.DEFERRED3, ProgramStage.DEFERRED, null),
    DEFERRED4(ProgramId.DEFERRED4, ProgramStage.DEFERRED, null),
    DEFERRED5(ProgramId.DEFERRED5, ProgramStage.DEFERRED, null),
    DEFERRED6(ProgramId.DEFERRED6, ProgramStage.DEFERRED, null),
    DEFERRED7(ProgramId.DEFERRED7, ProgramStage.DEFERRED, null),

    COMPOSITE(ProgramId.COMPOSITE, ProgramStage.COMPOSITE, null),
    COMPOSITE1(ProgramId.COMPOSITE1, ProgramStage.COMPOSITE, null),
    COMPOSITE2(ProgramId.COMPOSITE2, ProgramStage.COMPOSITE, null),
    COMPOSITE3(ProgramId.COMPOSITE3, ProgramStage.COMPOSITE, null),
    COMPOSITE4(ProgramId.COMPOSITE4, ProgramStage.COMPOSITE, null),
    COMPOSITE5(ProgramId.COMPOSITE5, ProgramStage.COMPOSITE, null),
    COMPOSITE6(ProgramId.COMPOSITE6, ProgramStage.COMPOSITE, null),
    COMPOSITE7(ProgramId.COMPOSITE7, ProgramStage.COMPOSITE, null),

    FINAL(ProgramId.FINAL, ProgramStage.FINAL, null);

    private final ProgramId programId;
    private final ProgramStage stage;
    private final RenderPass fallback;

    RenderPass(ProgramId programId, ProgramStage stage, RenderPass fallback) {
        this.programId = programId;
        this.stage = stage;
        this.fallback = fallback;
    }

    public String getProgramName() {
        return programId.sourceName();
    }

    public ProgramId programId() {
        return programId;
    }

    public ProgramStage stage() {
        return stage;
    }

    public RenderPass fallback() {
        return fallback;
    }

    public static RenderPass fromName(String name) {
        ProgramId programId = ProgramId.fromSourceName(name);
        if (programId != null) {
            return fromProgramId(programId);
        }
        return Arrays.stream(values())
                .filter(pass -> pass.getProgramName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static RenderPass fromProgramId(ProgramId programId) {
        if (programId == ProgramId.TERRAIN_CUTOUT) {
            return GBUFFERS_TERRAIN_CUTOUT;
        }
        if (programId == ProgramId.TERRAIN_CUTOUT_MIPPED) {
            return GBUFFERS_TERRAIN_CUTOUT_MIP;
        }
        return Arrays.stream(values())
                .filter(pass -> pass.programId == programId)
                .findFirst()
                .orElse(null);
    }

    public static final RenderPass[] DEFERRED_PASSES = Arrays.stream(values())
            .filter(pass -> pass.stage() == ProgramStage.DEFERRED)
            .toArray(RenderPass[]::new);

    public static final RenderPass[] COMPOSITE_PASSES = Arrays.stream(values())
            .filter(pass -> pass.stage() == ProgramStage.COMPOSITE)
            .toArray(RenderPass[]::new);
}
