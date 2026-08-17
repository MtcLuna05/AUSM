package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.pipeline.PipelineContext;

/**
 * Iris-shaped phase predicates for shader override routing.
 */
public final class ShaderOverrides {
    private ShaderOverrides() {
    }

    public static boolean isSky() {
        return isSky(PipelineContext.getInstance().getPhase());
    }

    public static boolean isEntities() {
        return PipelineContext.getInstance().getPhase() == WorldRenderingPhase.ENTITIES;
    }

    public static boolean isBlockEntities() {
        return PipelineContext.getInstance().getPhase() == WorldRenderingPhase.BLOCK_ENTITIES;
    }

    public static boolean isPhase(WorldRenderingPhase phase) {
        return PipelineContext.getInstance().getPhase() == phase;
    }

    public static boolean isSky(WorldRenderingPhase phase) {
        return switch (phase) {
            case CUSTOM_SKY, SKY, SUNSET, SUN, STARS, VOID, MOON, SKY_TEXTURED, ASTRAL_STARS, ASTRAL_SOLAR_ECLIPSE,
                 SKY_GROUND -> true;
            default -> false;
        };
    }
}
